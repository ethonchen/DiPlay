package com.shilapi.xcertplay

import android.content.Intent
import android.content.res.Configuration
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 25], qualifiers = "en-sw600dp-w1024dp-h600dp-land-mdpi")
class Android6StartupTest {
    @Test fun scaledArabicSettingsOpenEveryCategoryAndReturnToSystemLanguage() {
        val app = RuntimeEnvironment.getApplication()
        CarPlayBackgroundSession.clear()
        AppLocale.save(app, AppLocale.ARABIC)
        InterfaceSize.save(app, 150)
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(app, DiPlayActivity::class.java).putExtra("page", "settings"))
        try {
            controller.setup()
            val activity = controller.get()
            assertEquals("ar", activity.resources.configuration.primaryLocale().language)
            assertEquals(View.LAYOUT_DIRECTION_RTL, activity.resources.configuration.layoutDirection)
            assertEquals(240, activity.resources.configuration.densityDpi)
            for (category in SettingsCategory.entries) {
                ReflectionHelpers.setField(activity, "settingsCategory", category)
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
            }
            AppLocale.save(app, AppLocale.SYSTEM)
            AppLocale.enforce(activity)
            assertEquals(android.content.res.Resources.getSystem().configuration.primaryLocale(),
                activity.resources.configuration.primaryLocale())
        } finally {
            controller.pause().stop().destroy()
            AppLocale.save(app, AppLocale.SYSTEM)
            InterfaceSize.save(app, InterfaceSize.AUTO)
        }
    }

    @Test fun densityOverridesKeepTheSelectedLanguageAndLayoutDirection() {
        val original = Configuration().apply {
            densityDpi = 160
            screenWidthDp = 1280
            screenHeightDp = 800
            smallestScreenWidthDp = 800
            setLocale(Locale("ar"))
        }
        val scaled = InterfaceSize.override(original, 150)!!
        val contextOverride = InterfaceSize.contextOverride(scaled)
        assertEquals(Locale("ar"), scaled.primaryLocale())
        assertEquals(Locale("ar"), contextOverride.primaryLocale())
        assertEquals(View.LAYOUT_DIRECTION_RTL, contextOverride.layoutDirection)
        assertEquals(0, contextOverride.screenWidthDp)
    }
}
