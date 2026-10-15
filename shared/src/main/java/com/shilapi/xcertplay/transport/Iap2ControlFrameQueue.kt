package com.shilapi.xcertplay.transport

import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.io.IOException
import java.util.ArrayDeque

/** Ordered, bounded handoff between the tunnel framer and the control/metadata consumer. */
internal class Iap2ControlFrameQueue(
    private val onDiagnostic: (String) -> Unit = {},
    private val maximumFrames: Int = 256,
    private val maximumBytes: Int = 1_048_576,
) {
    private data class Pending(val frame: Iap2Frame, val bytes: Int, val atNanos: Long)
    private val lock = Object()
    private val pending = ArrayDeque<Pending>()
    private var pendingBytes = 0
    private var peakFrames = 0
    private var lastDiagnosticNanos = 0L
    private var closed = false
    private var failure: Throwable? = null

    fun offer(frame: Iap2Frame) {
        val bytes = frame.encodedFrame().size
        val diagnostic = synchronized(lock) {
            if (closed || failure != null) return
            if (pending.size >= maximumFrames || bytes > maximumBytes - pendingBytes) {
                throw IOException("CSM received-frame queue limit exceeded " +
                    "pending=${pending.size} bytes=$pendingBytes incomingBytes=$bytes peak=$peakFrames")
            }
            val now = System.nanoTime()
            pending.addLast(Pending(frame, bytes, now))
            pendingBytes += bytes
            peakFrames = maxOf(peakFrames, pending.size)
            lock.notifyAll()
            val oldestAgeMillis = (now - pending.first.atNanos) / 1_000_000
            if ((pending.size >= 64 || oldestAgeMillis >= 2_000) &&
                (lastDiagnosticNanos == 0L || now - lastDiagnosticNanos >= 5_000_000_000L)) {
                lastDiagnosticNanos = now
                "iAP2 CSM receive queue pending=${pending.size} bytes=$pendingBytes " +
                    "peak=$peakFrames oldestAgeMs=$oldestAgeMillis"
            } else null
        }
        diagnostic?.let(::diagnose)
    }

    fun poll(timeoutMillis: Long): Iap2Frame? {
        val started = System.nanoTime()
        val budget = timeoutMillis * 1_000_000
        val result = synchronized(lock) {
            while (pending.isEmpty() && !closed && failure == null) {
                val remaining = budget - (System.nanoTime() - started)
                if (remaining <= 0) return null
                try {
                    lock.wait(remaining / 1_000_000, (remaining % 1_000_000).toInt())
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
            failure?.let { throw it }
            val next = pending.pollFirst() ?: return null
            pendingBytes -= next.bytes
            next to ((System.nanoTime() - next.atNanos) / 1_000_000)
        }
        if (result.second >= 2_000) {
            val diagnostic = synchronized(lock) {
                val now = System.nanoTime()
                if (now - lastDiagnosticNanos < 5_000_000_000L) null else {
                    lastDiagnosticNanos = now
                    "iAP2 CSM consumer resumed queueAgeMs=${result.second} " +
                        "pending=${pending.size} bytes=$pendingBytes peak=$peakFrames"
                }
            }
            diagnostic?.let(::diagnose)
        }
        return result.first.frame
    }

    fun fail(cause: Throwable) = synchronized(lock) {
        failure = cause
        lock.notifyAll()
    }

    fun close() = synchronized(lock) {
        closed = true
        pending.clear()
        pendingBytes = 0
        lock.notifyAll()
    }

    private fun diagnose(message: String) {
        try { onDiagnostic(message) } catch (_: Exception) {
            // Diagnostic callbacks cannot terminate a healthy link.
        }
    }
}
