package com.shilapi.xcertplay.media

import java.util.concurrent.TimeUnit

/** Share the upstream decoder pump while retaining Q7 retry diagnostics. */
internal fun audioInputBuffer(
    running: () -> Boolean,
    drain: () -> Unit,
    dequeue: () -> Int,
    onRetry: () -> Unit,
    nanoTime: () -> Long = System::nanoTime,
    timeoutNs: Long = TimeUnit.MILLISECONDS.toNanos(500),
): Int = DecoderInputPump.acquire(
    running = running,
    drain = drain,
    dequeue = {
        if (!running()) -1 else dequeue().also { if (it < 0) onRetry() }
    },
    nanoTime = nanoTime,
    timeoutNs = timeoutNs,
)
