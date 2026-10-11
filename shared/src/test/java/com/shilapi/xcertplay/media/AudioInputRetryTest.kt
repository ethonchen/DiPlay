package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class AudioInputRetryTest {
    @Test fun outputBackpressureIsDrainedBeforeTheSameInputIsRetried() {
        var outputHeld = true
        var retries = 0
        val index = audioInputBuffer({ true }, { outputHeld = false },
            { if (outputHeld) -1 else 3 }, { retries++ })
        assertEquals(3, index)
        assertEquals(0, retries)
    }

    @Test fun backpressureBeyondTheOldFiftyMillisecondBudgetKeepsThePacket() {
        var reads = 0
        var retries = 0
        val index = audioInputBuffer({ true }, {},
            { reads++; if (reads < 9) -1 else 3 }, { retries++ },
            nanoTime = { reads * 10_000_000L })
        assertEquals(3, index)
        assertEquals(8, retries)
    }

    @Test fun aPermanentlyStalledCodecStopsAfterHalfASecond() {
        var reads = 0
        var drains = 0
        assertEquals(-1, audioInputBuffer({ true }, { drains++ }, { reads++; -1 }, {},
            nanoTime = { reads * 10_000_000L }))
        assertEquals(50, reads)
        assertEquals(50, drains)
    }

    @Test fun shutdownDuringDrainDoesNotKeepFeedingTheCodec() {
        var active = true
        var reads = 0
        assertEquals(-1, audioInputBuffer({ active }, { active = false }, { reads++; -1 }, {}))
        assertEquals(0, reads)
    }
}
