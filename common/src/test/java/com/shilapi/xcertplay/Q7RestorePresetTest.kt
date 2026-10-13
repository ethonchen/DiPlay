package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.network.Q7HotspotRestore
import com.shilapi.xcertplay.orchestration.ManualHotspotBand
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class Q7RestorePresetTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun clear() {
        app.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun oneTimeMigrationReplacesTheFailedOpenProfileWithTheRequestedTarget() {
        AirPlayPersistence.saveManualHotspotSsid(app, "Old")
        AirPlayPersistence.saveManualHotspotPassphrase(app, "")
        AirPlayPersistence.applyQ7RestorePreset(app, supported = true)
        assertEquals("sica_camap_4e27", AirPlayPersistence.loadManualHotspotSsid(app))
        assertEquals("11111111", AirPlayPersistence.loadManualHotspotPassphrase(app))
        assertEquals(ManualHotspotSecurity.WPA2, AirPlayPersistence.loadManualHotspotSecurity(app))
        assertEquals(ManualHotspotBand.GHZ_2_4, AirPlayPersistence.loadManualHotspotBand(app))
        assertEquals(11, AirPlayPersistence.loadManualHotspotChannel(app))
        assertTrue(AirPlayPersistence.loadAutoStartOnBoot(app))
        assertTrue(DiPlayPreferences.autoConnect(app))
    }
    @Test fun laterUserEditsAndStartupChoicesArePreserved() {
        AirPlayPersistence.applyQ7RestorePreset(app, supported = true)
        AirPlayPersistence.saveManualHotspotSsid(app, "Edited")
        AirPlayPersistence.saveManualHotspotPassphrase(app, "edited123")
        AirPlayPersistence.saveAutoStartOnBoot(app, false)
        DiPlayPreferences.saveAutoConnect(app, false)
        AirPlayPersistence.applyQ7RestorePreset(app, supported = true)
        assertEquals("Edited", AirPlayPersistence.loadManualHotspotSsid(app))
        assertEquals("edited123", AirPlayPersistence.loadManualHotspotPassphrase(app))
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(app))
        assertFalse(DiPlayPreferences.autoConnect(app))
    }
    @Test fun otherDevicesKeepTheirConfiguration() {
        AirPlayPersistence.saveManualHotspotSsid(app, "Other")
        AirPlayPersistence.applyQ7RestorePreset(app, supported = false)
        assertEquals("Other", AirPlayPersistence.loadManualHotspotSsid(app))
        assertEquals("", AirPlayPersistence.loadManualHotspotPassphrase(app))
    }
    @Test fun restoreDiagnosticsSurviveRedactionWithoutPersonalConfiguration() {
        val line = "q7-restore result=READY configVerified=true"
        assertEquals(line, DiagnosticRedactor.redact(line))
        assertEquals("q7-restore result=PERMISSION_REQUIRED configVerified=false",
            DiagnosticRedactor.redact("q7-restore result=PERMISSION_REQUIRED configVerified=false"))
        assertNull(DiagnosticRedactor.redact("q7-restore password=test1234"))
    }
}
