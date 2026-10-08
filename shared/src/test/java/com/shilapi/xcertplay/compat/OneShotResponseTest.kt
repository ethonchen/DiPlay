package com.shilapi.xcertplay.compat

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class OneShotResponseTest {
    @Test fun responseArrivingBeforeTheWaitIsNotLostOrReplaced() {
        val response = OneShotResponse<String>()
        assertTrue(response.complete("first"))
        assertFalse(response.complete("duplicate"))
        assertEquals("first", response.get(0, TimeUnit.MILLISECONDS))
    }

    @Test fun networkThreadCanCompleteTheLoaderThreadsWait() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val response = OneShotResponse<String>()
            val waiting = executor.submit<String> { response.get(5, TimeUnit.SECONDS) }
            response.complete("loaded")
            assertEquals("loaded", waiting.get(5, TimeUnit.SECONDS))
        } finally { executor.shutdownNow() }
    }

    @Test(expected = TimeoutException::class)
    fun unansweredRequestHasABoundedWait() {
        OneShotResponse<String>().get(0, TimeUnit.MILLISECONDS)
    }
}
