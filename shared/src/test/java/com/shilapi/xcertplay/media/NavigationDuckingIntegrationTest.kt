package com.shilapi.xcertplay.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Looper
import com.shilapi.xcertplay.airplay.CarPlayAudioDuckingCommand
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], manifest = Config.NONE,
    shadows = [AudioFocusAutoYieldTest.VolumeTrackingAudioTrack::class])
class NavigationDuckingIntegrationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    private val tracks = mutableListOf<AudioTrack>()
    private val coordinators = mutableListOf<AudioFocusCoordinator>()

    @Before fun reset() {
        AudioFocusAutoYieldTest.VolumeTrackingAudioTrack.volumes.clear()
        shadowOf(manager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }
    @After fun cleanup() {
        coordinators.forEach(AudioFocusCoordinator::close)
        tracks.forEach(AudioTrack::release)
        AudioFocusAutoYieldTest.VolumeTrackingAudioTrack.volumes.clear()
    }

    @Test fun navigationDucksWithFocusDisabledAndOnlyMusicTracksChange() {
        val coordinator = coordinator(false)
        val media = track(); val phone = track(); val assistant = track(); val nav = track()
        coordinator.acquire(media, AudioChannel.MEDIA, attributes)
        coordinator.acquire(phone, AudioChannel.PHONE, attributes)
        coordinator.acquire(assistant, AudioChannel.ASSISTANT, attributes)
        coordinator.acquire(nav, AudioChannel.NAVIGATION, attributes)
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0)
        val userVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val token = coordinator.openNavigation()
        coordinator.navigationPcm(token, 200)
        idle(120)
        assertEquals(0.25f, volume(media), 0.001f)
        assertTrue(volumes(phone).isEmpty())
        assertTrue(volumes(assistant).isEmpty())
        assertTrue(volumes(nav).isEmpty())
        assertNull(shadowOf(manager).lastAudioFocusRequest)
        val later = track()
        coordinator.acquire(later, AudioChannel.MEDIA, attributes)
        assertEquals(0.25f, volume(later), 0.001f)
        idle(430); idle(300)
        assertEquals(1f, volume(media), 0.001f)
        assertEquals(1f, volume(later), 0.001f)
        assertEquals(userVolume, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    @Test fun focusMuteWinsAndFocusGainDoesNotUndoNavigationDucking() {
        val coordinator = coordinator(true); val media = track(); val owner = Any()
        coordinator.acquire(media, AudioChannel.MEDIA, attributes)
        coordinator.onAudioDucking(owner, CarPlayAudioDuckingCommand(0.25f, 0, true))
        coordinator.onExternalFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(0f, volume(media), 0f)
        coordinator.onExternalFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(0.25f, volume(media), 0f)
        coordinator.onExternalFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        coordinator.onAudioDucking(owner, CarPlayAudioDuckingCommand(1f, 0, false))
        assertEquals(0f, volume(media), 0f)
        coordinator.onExternalFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1f, volume(media), 0f)
    }

    @Test fun rendererPcmTriggersSpeechButSilentOpenStreamDoesNotHoldDucking() {
        val coordinator = coordinator(false); val media = track(); val nav = track()
        coordinator.acquire(media, AudioChannel.MEDIA, attributes)
        val type = Class.forName("com.shilapi.xcertplay.media.AudioRenderer")
        val renderer = type.declaredConstructors.single { it.parameterCount == 13 }
            .apply { isAccessible = true }.newInstance(
                com.shilapi.xcertplay.airplay.AudioFormat(
                    com.shilapi.xcertplay.airplay.AudioCodecKind.LPCM, 44100, 2, 96, "default"),
                false, false, 0, 0, coordinator, 0, 300, { _: String -> }, 0L, false, null, false)
        fun set(name: String, value: Any) =
            type.getDeclaredField(name).apply { isAccessible = true }.set(renderer, value)
        val write = type.getDeclaredMethod("writePcm", ByteArray::class.java, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType).apply { isAccessible = true }
        try {
            set("track", nav); set("mappedChannel", AudioChannel.NAVIGATION)
            val pcm = ByteArray(1764)
            write.invoke(renderer, pcm, 0, pcm.size)
            idle(150)
            assertEquals(1f, volume(media), 0f)
            pcm[1] = 16
            write.invoke(renderer, pcm, 0, pcm.size)
            idle(120)
            assertEquals(0.25f, volume(media), 0.001f)
            idle(1000)
            write.invoke(renderer, ByteArray(1764), 0, 1764)
            idle(150)
            assertEquals(1f, volume(media), 0.001f)
        } finally { (renderer as java.io.Closeable).close() }
    }

    @Test fun overlappingOwnersAndLateTeardownCallbacksCannotLeaveMusicDucked() {
        val coordinator = coordinator(false); val media = track()
        coordinator.acquire(media, AudioChannel.MEDIA, attributes)
        val first = Any(); val second = Any()
        coordinator.onAudioDucking(first, CarPlayAudioDuckingCommand(0.25f, 0, true))
        coordinator.onAudioDucking(second, CarPlayAudioDuckingCommand(0.1f, 0, true))
        coordinator.endAudioDucking(first)
        assertEquals(0.1f, volume(media), 0f)
        coordinator.endAudioDucking(second)
        idle(300)
        assertEquals(1f, volume(media), 0.001f)
        coordinator.onAudioDucking(second, CarPlayAudioDuckingCommand(0.1f, 0, true))
        assertEquals(1f, volume(media), 0.001f)
        coordinator.close()
        coordinator.navigationPcm(coordinator.openNavigation(), 1000)
        idle(1000)
        assertEquals(1f, volume(media), 0.001f)
    }

    private fun idle(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    private fun coordinator(enabled: Boolean) = AudioFocusCoordinator(context, enabled).also(coordinators::add)
    private fun track(): AudioTrack = AudioTrack.Builder().setAudioAttributes(attributes)
        .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
        .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(4096).build().also(tracks::add)
    private fun volumes(track: AudioTrack) = AudioFocusAutoYieldTest.VolumeTrackingAudioTrack.volumes[track].orEmpty()
    private fun volume(track: AudioTrack) = volumes(track).lastOrNull() ?: 1f
}
