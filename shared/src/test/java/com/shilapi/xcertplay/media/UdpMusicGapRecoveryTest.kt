package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioTrack
import android.media.MediaCodec
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaCodec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], manifest = Config.NONE,
    shadows = [UdpMusicGapRecoveryTest.GapCodec::class, UdpMusicGapRecoveryTest.GapTrack::class])
class UdpMusicGapRecoveryTest {
    @Test fun sixMissingUdpPacketsAreCompensatedAfterReleasingCodecOutput() {
        assertEquals(8 * 4096, render(102, "media"))
    }

    @Test fun bufferedMusicAndVoiceKeepTheirExistingTiming() {
        assertEquals(2 * 4096, render(103, "media"))
        assertEquals(2 * 4096, render(102, "default"))
    }

    private fun render(payloadType: Int, audioType: String): Int {
        GapCodec.next = 0
        GapCodec.outputHeld = false
        GapTrack.bytes = 0
        val codec = MediaCodec.createDecoderByType("audio/mp4a-latm")
        val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().build())
            .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(48_000)
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(192_000).build()
        val type = Class.forName("com.shilapi.xcertplay.media.AudioRenderer")
        val renderer = type.declaredConstructors.single { it.parameterCount == 13 }
            .apply { isAccessible = true }.newInstance(
                AudioFormat(AudioCodecKind.AAC_LC, 48_000, 2, payloadType, audioType), false, false, 0, 0,
                AudioFocusCoordinator(null, false, false), 0, 1000, { _: String -> }, 0L, false, null, false)
        fun set(name: String, value: Any) = type.getDeclaredField(name).apply { isAccessible = true }.set(renderer, value)
        set("track", track)
        set("playbackStarted", true)
        set("fadeApplied", true)
        try {
            type.getDeclaredMethod("drainCodec", MediaCodec::class.java).apply { isAccessible = true }.invoke(renderer, codec)
            return GapTrack.bytes
        } finally {
            track.release()
            codec.release()
        }
    }

    @Implements(MediaCodec::class)
    class GapCodec : ShadowMediaCodec() {
        @Implementation override fun native_dequeueOutputBuffer(info: MediaCodec.BufferInfo, timeout: Long): Int {
            if (next >= 2) return MediaCodec.INFO_TRY_AGAIN_LATER
            val index = next++
            info.set(0, 4096, if (index == 0) 0L else 7168L * 1_000_000L / 48_000L, 0)
            outputHeld = true
            return index
        }
        @Implementation override fun getBuffer(input: Boolean, index: Int): ByteBuffer =
            ByteBuffer.wrap(ByteArray(4096) { 1 })
        @Implementation override fun releaseOutputBuffer(index: Int, render: Boolean) { outputHeld = false }
        companion object { var next = 0; var outputHeld = false }
    }

    @Implements(AudioTrack::class)
    class GapTrack {
        @Implementation fun write(data: ByteArray, offset: Int, size: Int, mode: Int): Int {
            assertFalse("codec output held across a blocking track write", GapCodec.outputHeld)
            bytes += size
            return size
        }
        companion object { var bytes = 0 }
    }
}
