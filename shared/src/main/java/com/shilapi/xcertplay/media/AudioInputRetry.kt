package com.shilapi.xcertplay.media

/** Release pending output before retrying the same compressed packet, with a bounded codec wait. */
internal fun audioInputBuffer(
    running: () -> Boolean,
    drain: () -> Unit,
    dequeue: () -> Int,
    onRetry: () -> Unit,
): Int {
    var retries = 0
    while (running()) {
        val index = dequeue()
        if (index >= 0) return index
        if (retries >= 4) return index
        retries++
        onRetry()
        drain()
    }
    return -1
}
