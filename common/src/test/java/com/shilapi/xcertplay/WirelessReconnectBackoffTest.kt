package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Test

class WirelessReconnectBackoffTest {
    @Test fun firstConnectionRetainsTheNormalBackoff() {
        val backoff = WirelessReconnectBackoff { 1_000L }
        backoff.afterLoss()
        assertEquals(30_000L, backoff.cap(30_000L))
    }

    @Test fun briefSessionRecoveryCapsBothNormalAndStartupDelays() {
        var now = 1_000L
        val backoff = WirelessReconnectBackoff { now }
        backoff.sessionActive()
        now += 60 * 60 * 1_000L // Even a session that ran for an hour gets a full recovery window.
        backoff.afterLoss()
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 8_000L, 8_000L),
            listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L).map(backoff::cap))
        now += 119_999L
        backoff.afterLoss() // Failed attempts must not keep extending the fast-retry window.
        assertEquals(8_000L, backoff.cap(30_000L))
        now++
        assertEquals(30_000L, backoff.cap(30_000L))
    }

    @Test fun aNewActiveSessionCanStartAnotherRecoveryWindow() {
        var now = 1_000L
        val backoff = WirelessReconnectBackoff { now }
        backoff.sessionActive()
        backoff.afterLoss()
        now += 200_000L
        assertEquals(30_000L, backoff.cap(30_000L))
        backoff.sessionActive()
        backoff.afterLoss()
        assertEquals(8_000L, backoff.cap(30_000L))
    }
}
