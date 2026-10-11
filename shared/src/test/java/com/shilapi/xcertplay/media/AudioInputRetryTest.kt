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
        assertEquals(1, retries)
    }

    @Test fun aPermanentlyStalledCodecHasABoundedRetryBudget() {
        var reads = 0
        var drains = 0
        assertEquals(-1, audioInputBuffer({ true }, { drains++ }, { reads++; -1 }, {}))
        assertEquals(5, reads)
        assertEquals(4, drains)
    }

    @Test fun shutdownDuringDrainDoesNotKeepFeedingTheCodec() {
        var active = true
        var reads = 0
        assertEquals(-1, audioInputBuffer({ active }, { active = false }, { reads++; -1 }, {}))
        assertEquals(1, reads)
    }
}
