package com.shilapi.xcertplay.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/** Owner-requested Q7 experiment, restricted to the exact board reported by the car. */
object Q7HotspotRestore {
    private val lock = ReentrantLock()

    fun supported(api: Int = Build.VERSION.SDK_INT, board: String = Build.BOARD,
        hardware: String = Build.HARDWARE): Boolean =
        api == 23 && board.equals("sql_fyt6025", true) && hardware.equals("sc8830", true)

    fun enabled(security: ManualHotspotSecurity): Boolean = supported() && security == ManualHotspotSecurity.WPA2

    fun prepare(context: Context, ssid: String, credential: String, channel: Int,
        timeoutMillis: Long, cancelled: () -> Boolean, log: (String) -> Unit) {
        require(credential.length in 8..63 && '\u0000' !in credential)
        require(ssid.isNotBlank() && ssid.encodeToByteArray().size <= 32 && '\u0000' !in ssid)
        require(channel in 1..11)
        check(supported())
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        var acquired = false
        try {
            while (!acquired) {
                if (cancelled() || Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Q7 hotspot restore cancelled")
                if (System.nanoTime() >= deadline) throw java.io.IOException("Q7 hotspot restore timed out")
                acquired = lock.tryLock(100, TimeUnit.MILLISECONDS)
            }
            val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
                ?: throw IllegalStateException("Q7 Wi-Fi manager unavailable")
            val port = LegacyQ7ApPort(wifi, ssid, credential, channel) { Settings.System.canWrite(context) }
            val result = LegacyApRestoreTransaction(port, cancelled, log = log).run(
                ((deadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(1),
            )
            if (result != LegacyApRestoreTransaction.Result.READY) {
                val detail = if (result == LegacyApRestoreTransaction.Result.PERMISSION_REQUIRED)
                    "Allow DiPlay to modify system settings, return to DiPlay, and connect again."
                else "The firmware could not restore the saved hotspot configuration."
                throw WirelessStartupException(WirelessStartupFailure.HOTSPOT_CONFIGURATION,
                    "Q7 hotspot restore: $result. $detail")
            }
            log("q7-restore radioEnabled=true security=WPA_WPA2 band=2.4GHz awaitingNetworkInterface=true")
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw java.io.InterruptedIOException("Q7 hotspot restore cancelled")
        } finally {
            if (acquired) lock.unlock()
        }
    }

    /** Recheck immediately before credentials/StartSession; a late OEM reset must trigger a retry. */
    fun verify(context: Context, ssid: String, credential: String, channel: Int) {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: throw IllegalStateException("Q7 Wi-Fi manager unavailable")
        val port = LegacyQ7ApPort(wifi, ssid, credential, channel) { false }
        if (port.state() != 13 || port.read()?.let(port::matches) != true) {
            throw WirelessStartupException(WirelessStartupFailure.HOTSPOT_CONFIGURATION,
                "Q7 hotspot configuration changed before publication")
        }
    }
}

@SuppressLint("PrivateApi")
internal class LegacyQ7ApPort(private val wifi: Any, private val ssid: String,
    private val credential: String, private val channel: Int,
    private val allowed: () -> Boolean) : LegacyApRestoreTransaction.Port<WifiConfiguration> {
    private fun invoke(name: String, types: Array<Class<*>> = emptyArray(), vararg args: Any?): Any? =
        try { wifi.javaClass.getMethod(name, *types).invoke(wifi, *args) }
        catch (failure: InvocationTargetException) { throw failure.targetException }

    override fun read(): WifiConfiguration? = invoke("getWifiApConfiguration") as? WifiConfiguration
    override fun state(): Int? = (invoke("getWifiApState") as? Number)?.toInt()
    override fun canControl(): Boolean = allowed()
    private fun field(name: String) = WifiConfiguration::class.java.getField(name)
    // This constructor exists as a hidden API on Android 6, but entered the public SDK at API 30.
    override fun target(original: WifiConfiguration): WifiConfiguration = WifiConfiguration::class.java
        .getConstructor(WifiConfiguration::class.java).newInstance(original).apply {
        SSID = ssid
        preSharedKey = credential
        allowedKeyManagement.clear(); allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK)
        allowedAuthAlgorithms.clear(); allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
        allowedProtocols.clear(); allowedProtocols.set(WifiConfiguration.Protocol.RSN)
        allowedPairwiseCiphers.clear(); allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
        allowedGroupCiphers.clear(); allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
        field("apBand").setInt(this, 0) // Legacy WifiConfiguration: 0 = 2.4 GHz.
        field("apChannel").setInt(this, channel)
    }
    override fun matches(configuration: WifiConfiguration): Boolean =
        configuration.SSID?.removeSurrounding("\"") == ssid && configuration.preSharedKey == credential &&
            configuration.allowedKeyManagement.cardinality() == 1 &&
            configuration.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA2_PSK) &&
            field("apBand").getInt(configuration) == 0 && field("apChannel").getInt(configuration) == channel

    override fun write(configuration: WifiConfiguration): Boolean {
        val method = wifi.javaClass.getMethod("setWifiApConfiguration", WifiConfiguration::class.java)
        val result = try { method.invoke(wifi, configuration) }
        catch (failure: InvocationTargetException) { throw failure.targetException }
        return result == true || method.returnType == Void.TYPE
    }
    override fun enable(configuration: WifiConfiguration?, enabled: Boolean): Boolean =
        invoke("setWifiApEnabled", arrayOf(WifiConfiguration::class.java, Boolean::class.javaPrimitiveType!!),
            configuration, enabled) == true
}
