package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class Android6AudioTrackTest {
    @Test fun pcmPlaybackAndDiagnosticsDoNotNeedAndroid7UnderrunCounter() {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        val track = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(44100)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(4096).build()
        try {
            assertEquals(AudioTrack.STATE_INITIALIZED, track.state)
            assertNull(audioTrackUnderrunCount(track))
            assertSame(attributes, audioTrackAttributesForFocus(track, attributes))
            track.play()
            assertEquals(AudioTrack.PLAYSTATE_PLAYING, track.playState)
            assertEquals(1024, track.write(ByteArray(1024), 0, 1024, AudioTrack.WRITE_BLOCKING))
            track.pause()
        } finally { track.release() }
    }

    @Test fun fallbackKeepsQueuedPcmAndRebuffersOnlyAtAnEmptyTrack() {
        val progress = AudioBufferProgress(4)
        progress.written(4096)
        assertFalse(audioBufferStarved(null, 0, progress.queuedBytes(512)))
        val head = 1024
        val starved = audioBufferStarved(null, 0, progress.queuedBytes(head))
        assertTrue(progress.shouldRebuffer(true, true, starved, true, head, 2048))
        assertFalse(progress.shouldRebuffer(false, true, starved, true, head, 2048))
        assertFalse(progress.shouldRebuffer(true, false, starved, true, head, 2048))
        assertFalse(progress.shouldRebuffer(true, true, starved, false, head, 2048))
        assertFalse(audioBufferStarved(4, 4, 0))
        assertTrue(audioBufferStarved(5, 4, 2048))
    }
}
