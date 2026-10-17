package com.shilapi.xcertplay

import android.content.Intent
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Start the production media-key owner, including the no-focus compatibility setting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 25, 28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class LegacyMediaSessionTest {
    @Test fun systemKeyRoutingIsEnabledBeforeSessionActivation() = withSession(false) { session, _ ->
        @Suppress("DEPRECATION")
        val required = MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        inOrder(session).apply {
            verify(session).setFlags(required)
            verify(session).isActive = true
        }
    }

    @Test fun disablingAudioFocusStillEnablesSystemMediaKeyRouting() = withSession(true) { session, _ ->
        @Suppress("DEPRECATION")
        verify(session).setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        verify(session).isActive = true
    }

    @Test fun installedCallbackForwardsEachTrackTapOnceAndRecordsTheResult() = withSession(false) { session, controller ->
        val installed = ArgumentCaptor.forClass(MediaSession.Callback::class.java)
        verify(session).setCallback(installed.capture(), any(Handler::class.java))
        val callback = installed.value
        for (key in listOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
            for ((action, repeat) in listOf(KeyEvent.ACTION_DOWN to 0, KeyEvent.ACTION_DOWN to 1, KeyEvent.ACTION_UP to 0)) {
                assertTrue(callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(
                    Intent.EXTRA_KEY_EVENT, KeyEvent(0, 0, action, key, repeat),
                )))
            }
        }
        verify(controller).sendMediaButton(CarPlayMediaButton.NEXT)
        verify(controller).sendMediaButton(CarPlayMediaButton.PREVIOUS)
        val report = WheelKeyLog.report(bound = false).joinToString("\n")
        assertTrue(report, report.contains("media key ${KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_NEXT)} -> CarPlay 4 sent=true"))
        assertTrue(report, report.contains("media key ${KeyEvent.keyCodeToString(KeyEvent.KEYCODE_MEDIA_PREVIOUS)} -> CarPlay 5 sent=true"))
    }

    private fun withSession(disableFocus: Boolean, checkSession: (MediaSession, CarPlayController) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val controller = mock(CarPlayController::class.java)
        `when`(controller.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
        `when`(controller.sendMediaButton(anyInt())).thenReturn(true)
        AirPlayPersistence.saveDisableMediaKeyAudioFocus(app, disableFocus)
        WheelKeyLog.reset()
        mockConstruction(MediaSession::class.java).use { sessions ->
            try {
                CarPlayMediaKeys.attach(app, controller)
                CarPlayMediaKeys.onMediaAudioChanged(true)
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(1, sessions.constructed().size)
                checkSession(sessions.constructed().single(), controller)
            } finally {
                CarPlayMediaKeys.detach(controller)
                shadowOf(Looper.getMainLooper()).idle()
                AirPlayPersistence.saveDisableMediaKeyAudioFocus(app, false)
                WheelKeyLog.reset()
            }
        }
    }
}
