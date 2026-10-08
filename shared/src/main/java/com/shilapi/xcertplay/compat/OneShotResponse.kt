package com.shilapi.xcertplay.compat

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A bounded, single response without Android 7's CompletableFuture class. */
class OneShotResponse<T : Any> {
    private val ready = CountDownLatch(1)
    @Volatile private var value: T? = null

    @Synchronized fun complete(response: T): Boolean {
        if (value != null) return false
        value = response
        ready.countDown()
        return true
    }

    fun get(timeout: Long, unit: TimeUnit): T {
        if (!ready.await(timeout, unit)) throw TimeoutException("Response timed out")
        return checkNotNull(value)
    }
}
