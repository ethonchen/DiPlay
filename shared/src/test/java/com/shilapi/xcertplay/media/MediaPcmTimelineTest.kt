package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class MediaPcmTimelineTest {
    private fun pts(frame: Long) = frame * 1_000_000L / 48_000L

    @Test fun continuousAacDoesNotInsertAudioDespiteMicrosecondRounding() {
        val timeline = MediaPcmTimeline(48_000)
        repeat(10_000) { assertEquals(0, timeline.missingFrames(pts(it * 1024L), 1024)) }
        assertEquals(0, timeline.gapEvents)
        assertEquals(0, timeline.resets)
    }

    @Test fun log124SixMissingPacketsKeepTheir128MillisecondsOnTheTimeline() {
        val timeline = MediaPcmTimeline(48_000)
        assertEquals(0, timeline.missingFrames(pts(1_786_880), 1024))
        assertEquals(6 * 1024, timeline.missingFrames(pts(1_794_048), 1024))
        assertEquals(0, timeline.missingFrames(pts(1_795_072), 1024))
        assertEquals(1, timeline.gapEvents)
    }

    @Test fun log123FortySevenMissingPacketsDoNotConsumeOneSecondOfPrebuffer() {
        val timeline = MediaPcmTimeline(48_000)
        timeline.missingFrames(pts(10_523_648), 1024)
        assertEquals(47 * 1024, timeline.missingFrames(pts(10_572_800), 1024))
    }

    @Test fun unsignedRtpWrapIsContinuousAndCanContainAShortGap() {
        val timeline = MediaPcmTimeline(48_000)
        timeline.missingFrames(pts(0xffff_fc00L), 1024)
        assertEquals(0, timeline.missingFrames(pts(0), 1024))
        assertEquals(2 * 1024, timeline.missingFrames(pts(3072), 1024))
    }

    @Test fun seekAndBackwardTimestampsRebaseWithoutUnboundedSilence() {
        val timeline = MediaPcmTimeline(48_000)
        timeline.missingFrames(pts(0), 1024)
        assertEquals(0, timeline.missingFrames(pts(200_000), 1024))
        assertEquals(0, timeline.missingFrames(pts(0), 1024))
        assertEquals(2, timeline.resets)
        assertEquals(0, timeline.missingFrames(pts(1024), 1024))
    }

    @Test fun gapRampsPreserveStereoPolarityAndThenReachSilence() {
        val tail = byteArrayOf(0x10, 0x27, 0xf0.toByte(), 0xd8.toByte()) // +10000, -10000
        val chunk = ByteArray(4096) { 99 }
        PcmGapFade.silenceChunk(chunk, chunk.size, 0, 48_000, tail)
        fun sample(at: Int) = (chunk[at].toInt() and 255) or (chunk[at + 1].toInt() shl 8)
        assertTrue(sample(0) in 1..9999)
        assertEquals(-sample(0), sample(2))
        assertEquals(0, sample(240 * 4))
        PcmGapFade.silenceChunk(chunk, chunk.size, 1024, 48_000, tail)
        assertTrue(chunk.all { it == 0.toByte() })
    }
}
