package com.shilapi.xcertplay

import android.app.AlertDialog
import android.os.Build
import android.os.Looper
import android.provider.Settings
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], qualifiers = "en", manifest = Config.NONE,
    shadows = [CarHotspotAdbGrantTest.WritePermission::class])
class Q7RestorePermissionTest {
    private var board = ""
    private var hardware = ""
    private lateinit var activity: DiPlayActivity
    @Before fun setup() {
        board = Build.BOARD; hardware = Build.HARDWARE
        ReflectionHelpers.setStaticField(Build::class.java, "BOARD", "sql_fyt6025")
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", "sc8830")
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        app.getSharedPreferences("diplay", 0).edit().clear().commit()
        AirPlayPersistence.applyQ7RestorePreset(app)
        CarHotspotAdbGrantTest.WritePermission.allowed = false
        activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
    }
    @After fun cleanup() {
        ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
        ReflectionHelpers.setStaticField(Build::class.java, "BOARD", board)
        ReflectionHelpers.setStaticField(Build::class.java, "HARDWARE", hardware)
        CarHotspotAdbGrantTest.WritePermission.allowed = false
    }
    @Test fun missingPermissionOpensTheSpecificSystemGrantScreenWithoutConnecting() {
        DiPlayActivity::class.java.getDeclaredMethod("connect", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, true)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertEquals(activity.getString(R.string.settings_q7_restore_title), shadowOf(dialog).title.toString())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val intent = shadowOf(activity).nextStartedActivity
        assertNotNull(intent)
        assertEquals(Settings.ACTION_MANAGE_WRITE_SETTINGS, intent.action)
        assertEquals("package:${activity.packageName}", intent.dataString)
        assertFalse(Settings.System.canWrite(activity))
    }
}
