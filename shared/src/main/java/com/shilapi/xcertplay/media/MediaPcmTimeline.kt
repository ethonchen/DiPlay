package com.shilapi.xcertplay.media

/** Keeps bounded holes in UDP music on the RTP sample timeline instead of consuming the jitter buffer. */
internal class MediaPcmTimeline(private val sampleRate: Int) {
    private var expectedFrame: Long? = null
    var gapEvents = 0
        private set
    var resets = 0
        private set

    fun missingFrames(presentationTimeUs: Long, frames: Int): Int {
        if (presentationTimeUs < 0 || frames <= 0 || sampleRate <= 0) return 0
        val start = ((presentationTimeUs * sampleRate + 500_000L) / 1_000_000L) and MASK
        val previous = expectedFrame
        expectedFrame = (start + frames) and MASK
        if (previous == null) return 0
        // The source's unsigned 32-bit RTP clock wraps roughly every 25 hours at 48 kHz.
        val gap = (start - previous) and MASK
        if (gap == 0L || gap == MASK) return 0 // allow one frame of timestamp rounding
        if (gap > sampleRate * 2L) {
            resets++ // seek, duplicate/backward timestamp or long discontinuity: rebase, never allocate it
            return 0
        }
        gapEvents++
        return gap.toInt()
    }
    private companion object { const val MASK = 0xffff_ffffL }
}

/** Short ramps keep a missing interval's transition to/from silence from making a sharp click. */
internal object PcmGapFade {
    fun silenceChunk(data: ByteArray, length: Int, framesBefore: Int, sampleRate: Int, tail: ByteArray) {
        data.fill(0, 0, length)
        val frameBytes = tail.size
        val rampFrames = maxOf(1, sampleRate / 200) // 5 ms, then silence
        for (frame in 0 until minOf(length / frameBytes, rampFrames - framesBefore).coerceAtLeast(0)) {
            val gain = rampFrames - framesBefore - frame - 1
            for (channel in 0 until frameBytes / 2) {
                val at = channel * 2
                val value = (tail[at].toInt() and 0xff) or (tail[at + 1].toInt() shl 8)
                put(data, frame * frameBytes + at, value * gain / rampFrames)
            }
        }
    }

    fun fadeIn(data: ByteArray, offset: Int, length: Int, sampleRate: Int, frameBytes: Int) {
        val rampFrames = minOf(length / frameBytes, maxOf(1, sampleRate / 200))
        for (frame in 0 until rampFrames) {
            for (channel in 0 until frameBytes / 2) {
                val at = offset + frame * frameBytes + channel * 2
                val value = (data[at].toInt() and 0xff) or (data[at + 1].toInt() shl 8)
                put(data, at, value * (frame + 1) / rampFrames)
            }
        }
    }

    private fun put(data: ByteArray, at: Int, value: Int) {
        data[at] = value.toByte()
        data[at + 1] = (value shr 8).toByte()
    }
}
