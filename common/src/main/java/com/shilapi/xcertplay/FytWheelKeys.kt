package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.orchestration.CarPlayController
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Legacy FYT MCU keys can bypass both MediaSession and Android's input dispatcher. */
internal object FytWheelKeys {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "diplay-fyt-keys").apply { isDaemon = true }
    }
    private var connection: FytWheelKeyConnection? = null
    private var owner: CarPlayController? = null
    @Volatile private var state = "inactive"
    @Volatile private var active = false
    private val duplicates = FytKeyDuplicates()

    fun start(context: Context, expected: CarPlayController) {
        val app = context.applicationContext
        main.post {
            if (!legacyFyt(Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.MODEL, Build.BOARD)) return@post
            if (AirPlayPersistence.loadCarBluetoothAudio(app)) {
                connection?.stop()
                connection = null
                owner = null
                active = false
                duplicates.clear()
                state = "car Bluetooth audio: wheel remains with AVRCP"
                return@post
            }
            if (owner === expected && connection != null) return@post
            connection?.stop()
            owner = expected
            duplicates.clear()
            active = true
            connection = FytWheelKeyConnection(app, main, worker,
                onKey = { index, source -> CarPlayMediaKeys.onFytMediaKey(expected, index, source) },
                note = { state = it; WheelKeyLog.note("FYT $it") },
            ).also { it.start() }
        }
    }

    fun stop() {
        main.post {
            active = false
            connection?.stop()
            connection = null
            owner = null
            state = "inactive"
            duplicates.clear()
        }
    }

    fun report(): String = "FYT wheel keys active=$active state=$state"

    /** Some firmware emits the same tap through both vendor and Android callbacks. */
    fun shouldForward(index: Int, source: String): Boolean =
        !active || duplicates.accept(index, source, SystemClock.elapsedRealtime())

    internal fun legacyFyt(sdk: Int, vararg identity: String): Boolean =
        sdk <= 23 && identity.any { it.contains("fyt", ignoreCase = true) }
}

internal class FytKeyDuplicates {
    private var previous: Triple<Int, String, Long>? = null
    @Synchronized fun clear() { previous = null }
    @Synchronized fun accept(index: Int, source: String, now: Long): Boolean {
        val last = previous
        if (last != null && last.first == index && last.second != source && now - last.third in 0..150 &&
            (source.startsWith("fyt-") || last.second.startsWith("fyt-"))) return false
        previous = Triple(index, source, now)
        return true
    }
}

/** State and Android service lifecycle stay on main; all synchronous Binder work stays on worker. */
internal class FytWheelKeyConnection(
    private val context: Context,
    private val main: Handler,
    private val worker: Executor,
    private val onKey: (Int, String) -> Unit,
    private val note: (String) -> Unit,
) {
    private var stopped = true
    private var bound = false
    private var generation = Any()
    private var subscription: FytKeySubscription? = null
    private val retry = Runnable { bind() }
    private val service = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (stopped || !bound || binder == null) return
            invalidate()
            val owner = generation
            worker.execute {
                val next = runCatching {
                    FytKeySubscription(binder) { code, ints ->
                        main.post {
                            if (!stopped && generation === owner) {
                                // Numeric key data only; never vendor strings (which can contain personal data).
                                note("update=$code ints=${ints?.take(4)}")
                                FytKeySubscription.mediaKey(code, ints)?.let { onKey(it, "fyt-$code") }
                            }
                        }
                    }.also { it.register() }
                }
                main.post {
                    if (stopped || generation !== owner) {
                        next.getOrNull()?.let { stale -> worker.execute { stale.close() } }
                    } else {
                        next.onSuccess { subscription = it; note("connected updates=${it.registeredCodes}") }
                            .onFailure { note("registration failed: ${it.javaClass.simpleName}"); reconnect() }
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            if (!stopped) { note("service disconnected"); reconnect() }
        }
    }

    fun start() { if (stopped) { stopped = false; bind() } }
    fun stop() {
        stopped = true
        main.removeCallbacks(retry)
        invalidate()
        unbind()
    }
    private fun invalidate() {
        generation = Any()
        subscription?.let { old -> worker.execute { old.close() } }
        subscription = null
    }
    private fun unbind() {
        if (bound) runCatching { context.unbindService(service) }
        bound = false
    }
    private fun reconnect() {
        invalidate()
        unbind()
        main.removeCallbacks(retry)
        if (!stopped) main.postDelayed(retry, 5_000)
    }
    private fun bind() {
        if (stopped || bound) return
        val result = runCatching {
            context.bindService(Intent().setComponent(ComponentName("com.syu.ms", "app.ToolkitService")),
                service, Context.BIND_AUTO_CREATE)
        }
        bound = result.getOrDefault(false)
        note("bind requested=$bound${result.exceptionOrNull()?.let { " error=${it.javaClass.simpleName}" } ?: ""}")
        if (!bound) main.postDelayed(retry, 5_000)
    }
}

/** Minimal FYT wire protocol, independently implemented from the published SDK (see docs/FYT_WHEEL_KEYS.md). */
internal class FytKeySubscription(toolkit: IBinder, private val update: (Int, IntArray?) -> Unit) {
    private val module: IBinder = parcels { data, reply ->
        data.writeInterfaceToken("com.syu.ipc.IRemoteToolkit")
        data.writeInt(0) // FinalMainServer.MODULE_CODE_MAIN (not FinalMain.MODULE_MAIN = 1).
        check(toolkit.transact(1, data, reply, 0)) { "FYT toolkit unavailable" }
        reply.readException()
        checkNotNull(reply.readStrongBinder()) { "FYT MAIN module missing" }
    }
    val registeredCodes = mutableListOf<Int>()
    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                reply?.writeString("com.syu.ipc.IModuleCallback")
                return true
            }
            if (code != 1) return super.onTransact(code, data, reply, flags)
            data.enforceInterface("com.syu.ipc.IModuleCallback")
            val updateCode = data.readInt()
            val ints = data.createIntArray()
            data.createFloatArray()
            data.createStringArray()
            if (updateCode in UPDATE_CODES) update(updateCode, ints)
            reply?.writeNoException()
            return true
        }
    }

    fun register() {
        // 37 is the old FYT player command; 133 exists only on newer service revisions.
        // Flag 0 still subscribes, but does NOT replay the last key as a ghost tap on connection.
        for (code in UPDATE_CODES) runCatching {
            registration(3, code)
            registeredCodes.add(code)
        }
        check(registeredCodes.isNotEmpty()) { "No FYT key callbacks supported" }
    }
    fun close() { registeredCodes.forEach { runCatching { registration(4, it) } } }
    private fun registration(transaction: Int, code: Int) = parcels { data, reply ->
        data.writeInterfaceToken("com.syu.ipc.IRemoteModule")
        data.writeStrongBinder(callback)
        data.writeInt(code)
        if (transaction == 3) data.writeInt(0)
        check(module.transact(transaction, data, reply, 0)) { "FYT subscription unavailable" }
        // Several vendor versions return an empty Parcel for void methods instead of writeNoException.
        if (reply.dataSize() > 0) reply.readException()
    }

    companion object {
        private val UPDATE_CODES = listOf(37, 60, 133)
        fun mediaKey(code: Int, ints: IntArray?): Int? = when (code) {
            37 -> if (ints != null && ints.size >= 2 && ints[0] in listOf(8, 10)) when (ints[1]) {
                4 -> CarPlayMediaButton.PREVIOUS
                5 -> CarPlayMediaButton.NEXT
                else -> null
            } else null
            133 -> when (ints?.firstOrNull()) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> CarPlayMediaButton.NEXT
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> CarPlayMediaButton.PREVIOUS
                else -> null
            }
            // 60 is firmware-specific direction navigation: record it, never guess a media mapping.
            else -> null
        }
        private inline fun <T> parcels(block: (Parcel, Parcel) -> T): T {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try { return block(data, reply) } finally { reply.recycle(); data.recycle() }
        }
    }
}
