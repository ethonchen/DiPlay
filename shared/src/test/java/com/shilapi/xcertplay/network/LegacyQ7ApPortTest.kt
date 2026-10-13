package com.shilapi.xcertplay.network

import android.net.wifi.WifiConfiguration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class LegacyQ7ApPortTest {
    class Wifi {
        var saved = WifiConfiguration().apply {
            SSID = "Original"; preSharedKey = null
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        }
        var reject = false
        fun getWifiApConfiguration() = saved
        fun getWifiApState() = 13
        fun setWifiApConfiguration(configuration: WifiConfiguration): Boolean {
            if (reject) throw SecurityException("private OEM detail must not be logged")
            saved = configuration; return true
        }
        fun setWifiApEnabled(configuration: WifiConfiguration?, enabled: Boolean): Boolean = true
    }
    @Test fun legacyCopyUsesWpa2AndRealApi23BandFieldsWithoutMutatingOriginal() {
        val wifi = Wifi()
        val port = LegacyQ7ApPort(wifi, "TestCar", "test1234", 11) { true }
        val original = wifi.saved
        val target = port.target(original)
        assertEquals("Original", original.SSID)
        assertTrue(original.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE))
        assertEquals("TestCar", target.SSID)
        assertEquals("test1234", target.preSharedKey)
        assertTrue(target.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA2_PSK))
        assertFalse(target.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE))
        assertEquals(0, WifiConfiguration::class.java.getField("apBand").getInt(target))
        assertEquals(11, WifiConfiguration::class.java.getField("apChannel").getInt(target))
        assertTrue(port.write(target))
        assertTrue(port.matches(port.read()!!))
    }
    @Test fun wrongCredentialAndSecurityAreNotAcceptedAsRestored() {
        val wifi = Wifi()
        val port = LegacyQ7ApPort(wifi, "TestCar", "test1234", 11) { true }
        val target = port.target(wifi.saved)
        target.preSharedKey = "wrong123"
        assertFalse(port.matches(target))
        target.preSharedKey = "test1234"
        target.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        assertFalse(port.matches(target))
    }
    @Test fun reflectedSecurityRejectionRetainsItsRealFailureType() {
        val wifi = Wifi().apply { reject = true }
        val port = LegacyQ7ApPort(wifi, "TestCar", "test1234", 11) { true }
        assertThrows(SecurityException::class.java) { port.write(port.target(wifi.saved)) }
    }
    @Test fun experimentalControlCannotRunOnOtherBoardsOrAndroidVersions() {
        assertTrue(Q7HotspotRestore.supported(23, "sql_fyt6025", "sc8830"))
        assertFalse(Q7HotspotRestore.supported(24, "sql_fyt6025", "sc8830"))
        assertFalse(Q7HotspotRestore.supported(23, "other", "sc8830"))
        assertFalse(Q7HotspotRestore.supported(23, "sql_fyt6025", "qcom"))
    }
}
