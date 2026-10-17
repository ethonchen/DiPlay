package com.shilapi.xcertplay

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.media.RemoteControlClient
import android.media.session.MediaSession
import android.os.Looper
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class FytLegacyMediaKeysTest {
    @Test fun registersTheSameExplicitMutableReceiverWithTheSessionAndLegacyAudioApis() = withRegistration { session, audio, remote, _ ->
        val capture = ArgumentCaptor.forClass(PendingIntent::class.java)
        verify(session).setMediaButtonReceiver(capture.capture())
        val pending = capture.value
        verify(audio).registerMediaButtonEventReceiver(pending)
        verify(audio).registerRemoteControlClient(remote)
        val target = shadowOf(pending).savedIntent
        assertEquals(Intent.ACTION_MEDIA_BUTTON, target.action)
        assertEquals(ComponentName(RuntimeEnvironment.getApplication(), FytLegacyMediaReceiver::class.java), target.component)
        FytLegacyMediaKeys.updatePlayback(true)
        verify(remote).setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING)
        assertTrue(FytLegacyMediaKeys.report(), FytLegacyMediaKeys.report().contains("liveReceiver=true"))
    }

    @Test fun stockMusicAndFytCommandBroadcastsForwardRealNextAndPreviousCommands() = withRegistration { _, _, _, keys ->
        val app = RuntimeEnvironment.getApplication()
        app.sendBroadcast(Intent("com.android.music.musicservicecommand").putExtra("command", "next"))
        app.sendBroadcast(Intent("com.syu.ms.action.external_command").putExtra("action_command", "previous"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(4, 5), keys.map { it.first })
        // Either receiver may win; Android does not guarantee their delivery order.
        assertTrue(keys.all { it.second in setOf("fyt-legacy-dynamic", "fyt-legacy-manifest") })
    }

    @Test fun liveReceiverChannelForwardsEvenWhenNoManifestDeliveryOccurs() = withRegistration { _, _, _, keys ->
        assertTrue(FytLegacyMediaKeys.receive(Intent("com.android.music.next"), "dynamic"))
        assertTrue(FytLegacyMediaKeys.receive(Intent("com.android.music.previous"), "dynamic"))
        assertEquals(listOf(4 to "fyt-legacy-dynamic", 5 to "fyt-legacy-dynamic"), keys)
    }

    @Test fun implicitBroadcastDeliveredToBothReceiversSkipsOnlyOnceWithoutVendorBinder() = withRegistration { _, _, _, keys ->
        val app = RuntimeEnvironment.getApplication()
        app.sendBroadcast(Intent("com.android.music.musicservicecommand").putExtra("command", "next"))
        app.sendBroadcast(Intent("com.android.music.previous"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(4, 5), keys.map { it.first })
    }

    @Test fun explicitMediaReceiverForwardsDownOnlyAndAcceptsFilledInKeyEvents() = withRegistration { session, _, _, keys ->
        val capture = ArgumentCaptor.forClass(PendingIntent::class.java)
        verify(session).setMediaButtonReceiver(capture.capture())
        val target = shadowOf(capture.value).savedIntent
        val receiver = FytLegacyMediaReceiver()
        for ((action, repeat) in listOf(0 to 0, 0 to 1, 1 to 0)) {
            val filled = Intent(target).apply {
                fillIn(Intent().putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(0, 0, action, 87, repeat)), 0)
            }
            receiver.onReceive(RuntimeEnvironment.getApplication(), filled)
        }
        assertEquals(listOf(4 to "fyt-legacy-manifest"), keys)
    }

    @Test fun handlesDirectActionsAndDocumentedRawExtrasWithoutGuessingVendorNumbers() {
        assertEquals(4, FytLegacyMediaKeys.mediaIndex(Intent("com.android.music.next")))
        assertEquals(5, FytLegacyMediaKeys.mediaIndex(Intent("com.android.music.previous")))
        assertEquals(4, FytLegacyMediaKeys.mediaIndex(Intent("com.syu.ms.action.external_command").putExtra("key_code", 87)))
        assertNull(FytLegacyMediaKeys.mediaIndex(Intent("com.syu.ms.action.external_command").putExtra("keycode", 4)))
        assertNull(FytLegacyMediaKeys.mediaIndex(Intent("unknown").putExtra("command", "next")))
        assertNull(FytLegacyMediaKeys.mediaIndex(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(0, 29))))
        assertNull(FytLegacyMediaKeys.mediaIndex(Intent("com.android.music.musicservicecommand").putExtra("command", "private text")))
    }

    @Test fun unregistersBothLegacyApisAndRejectsLateEventsAfterStop() = withRegistration { session, audio, remote, keys ->
        val capture = ArgumentCaptor.forClass(PendingIntent::class.java)
        verify(session).setMediaButtonReceiver(capture.capture())
        FytLegacyMediaKeys.stop()
        verify(audio).unregisterRemoteControlClient(remote)
        verify(audio).unregisterMediaButtonEventReceiver(capture.value)
        FytLegacyMediaReceiver().onReceive(RuntimeEnvironment.getApplication(), Intent("com.android.music.next"))
        RuntimeEnvironment.getApplication().sendBroadcast(Intent("com.android.music.next"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(keys.isEmpty())
        try { capture.value.send(); fail("A stopped receiver token must be cancelled") }
        catch (_: PendingIntent.CanceledException) { }
    }

    @Test fun broadcastReachesTheProductionCarPlaySenderAndStopsWithItsController() {
        val app = RuntimeEnvironment.getApplication()
        val audio = mock(AudioManager::class.java)
        val controller = mock(CarPlayController::class.java)
        `when`(controller.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
        `when`(controller.sendMediaButton(anyInt())).thenReturn(true)
        mockConstruction(MediaSession::class.java).use { sessions ->
            mockConstruction(RemoteControlClient::class.java).use {
                try {
                    CarPlayMediaKeys.attach(app, controller)
                    CarPlayMediaKeys.onMediaAudioChanged(true)
                    shadowOf(Looper.getMainLooper()).idle()
                    FytLegacyMediaKeys.install(app, sessions.constructed().first(), audio) { index, source ->
                        CarPlayMediaKeys.onLegacyFytMediaKey(controller, index, source)
                    }
                    app.sendBroadcast(Intent("com.android.music.musicservicecommand").putExtra("command", "next"))
                    app.sendBroadcast(Intent("com.android.music.previous"))
                    shadowOf(Looper.getMainLooper()).idle()
                    verify(controller).sendMediaButton(CarPlayMediaButton.NEXT)
                    verify(controller).sendMediaButton(CarPlayMediaButton.PREVIOUS)
                    CarPlayMediaKeys.detach(controller)
                    FytLegacyMediaReceiver().onReceive(app, Intent("com.android.music.next"))
                    verify(controller, times(1)).sendMediaButton(CarPlayMediaButton.NEXT)
                } finally {
                    FytLegacyMediaKeys.stop()
                    CarPlayMediaKeys.detach(controller)
                    shadowOf(Looper.getMainLooper()).idle()
                }
            }
        }
    }

    private fun withRegistration(check: (MediaSession, AudioManager, RemoteControlClient, MutableList<Pair<Int, String>>) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val session = mock(MediaSession::class.java)
        val audio = mock(AudioManager::class.java)
        val keys = mutableListOf<Pair<Int, String>>()
        mockConstruction(RemoteControlClient::class.java).use { remotes ->
            try {
                FytLegacyMediaKeys.install(app, session, audio) { index, source -> keys.add(index to source); true }
                check(session, audio, remotes.constructed().single(), keys)
            } finally { FytLegacyMediaKeys.stop(); shadowOf(Looper.getMainLooper()).idle() }
        }
    }
}
