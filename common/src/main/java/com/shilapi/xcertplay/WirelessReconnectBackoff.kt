package com.shilapi.xcertplay

/** Brief recovery of an established session; absent phones still return to the normal backoff. */
internal class WirelessReconnectBackoff(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var hadSession = false
    private var recoveryUntilMillis = 0L

    fun sessionActive() { hadSession = true; recoveryUntilMillis = 0L }

    fun afterLoss() {
        if (!hadSession) return
        hadSession = false
        recoveryUntilMillis = nowMillis() + 120_000L
    }

    fun cap(delayMillis: Long): Long =
        if (recoveryUntilMillis > nowMillis()) delayMillis.coerceAtMost(8_000L) else delayMillis
}
