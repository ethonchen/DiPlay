package com.shilapi.xcertplay.media

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], manifest = Config.NONE)
class Android6CaptureClockTest {
    @Test fun microphoneTimeUsesTheLatencyFallbackWithoutTheApi24TimestampMethod() {
        val recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(4096).build()
        try {
            val clock = CaptureClock(16000)
            val before = System.nanoTime()
            clock.read(recorder, 160)
            val after = System.nanoTime()
            val end = clock.frameEndNs(160)
            assertTrue(end >= before - 20_000_000L)
            assertTrue(end <= after - 20_000_000L)
            assertEquals(5_000_000L, end - clock.frameEndNs(80))
        } finally { recorder.release() }
    }
}
