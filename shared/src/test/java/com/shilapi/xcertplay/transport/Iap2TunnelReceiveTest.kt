package com.shilapi.xcertplay.transport

import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class Iap2TunnelReceiveTest {
    @Test(timeout = 10_000) fun pausedRuntimeConsumerDoesNotStopTunnelFramingOrLoseTheBurst() {
        val framedWhilePaused = CountDownLatch(1)
        val wire = PhoneWire()
        val channel = Iap2CsmChannel.openTunnel(wire, onDiagnostic = {
            if (it.contains("pending=64")) framedWhilePaused.countDown()
        })
        try {
            assertTrue(channel.awaitReady(2_000))
            // No recv() from the runtime consumer until the background framer has drained the burst.
            repeat(192) { wire.control(Iap2Frame(0x5001, byteArrayOf(it.toByte()))) }
            assertTrue("Framing must continue while the callback consumer is paused",
                framedWhilePaused.await(2, TimeUnit.SECONDS))
            repeat(192) {
                assertArrayEquals(byteArrayOf(it.toByte()), channel.recv(2_000)!!.payload)
            }
            assertNull(channel.recv(10))
            assertFalse(channel.isClosed)
        } finally { channel.close() }
        assertTrue(wire.closed)
    }

    @Test(timeout = 10_000) fun tunnelFramerPreservesPartialAndConcatenatedCsmFrames() {
        val wire = PhoneWire()
        val channel = Iap2CsmChannel.openTunnel(wire)
        try {
            assertTrue(channel.awaitReady(2_000))
            val first = Iap2Frame(0x5201, byteArrayOf(1, 2, 3)).encodedFrame()
            val second = Iap2Frame(0x5001, byteArrayOf(4, 5)).encodedFrame()
            wire.rawControl(first.copyOf(3))
            wire.rawControl(first.copyOfRange(3, first.size) + second)
            assertEquals(0x5201, channel.recv(2_000)!!.messageId)
            assertArrayEquals(byteArrayOf(4, 5), channel.recv(2_000)!!.payload)
        } finally { channel.close() }
    }

    @Test(timeout = 10_000) fun closeWakesAWaitingConsumerAndTerminatesOwnedThreads() {
        val wire = PhoneWire()
        val channel = Iap2CsmChannel.openTunnel(wire)
        assertTrue(channel.awaitReady(2_000))
        val result = AtomicReference<Iap2Frame?>()
        val failure = AtomicReference<Throwable?>()
        val consumer = Thread {
            try { result.set(channel.recv(30_000)) } catch (error: Throwable) { failure.set(error) }
        }
        consumer.start()
        channel.close()
        consumer.join(2_000)
        assertFalse(consumer.isAlive)
        assertNull(failure.get())
        assertNull(result.get())
        assertTrue(wire.closed)
    }

    @Test(timeout = 10_000) fun peerEofIsReportedToTheConsumerInsteadOfAnEndlessPoll() {
        val wire = PhoneWire()
        val channel = Iap2CsmChannel.openTunnel(wire)
        assertTrue(channel.awaitReady(2_000))
        wire.endPeer()
        val failure = assertThrows(IOException::class.java) { channel.recv(2_000) }
        assertTrue(failure.message!!.contains("link ended"))
        assertTrue(channel.isClosed)
        // The original terminal transport failure remains observable during cleanup.
        try { channel.close() } catch (_: IOException) { }
    }

    @Test fun boundedQueueRejectsExcessBytesWithoutDiscardingAnEarlierUpdate() {
        val queue = Iap2ControlFrameQueue(maximumBytes = 18)
        val frame = Iap2Frame(0x5001, byteArrayOf(1, 2, 3)) // 9 encoded bytes.
        queue.offer(frame)
        queue.offer(frame)
        assertThrows(IOException::class.java) { queue.offer(frame) }
        assertArrayEquals(frame.payload, queue.poll(0)!!.payload)
        assertArrayEquals(frame.payload, queue.poll(0)!!.payload)
    }

    /** A real peer link engine, with arbitrary transport chunks and both endpoints' negotiation. */
    private class PhoneWire : BlockingDuplexByteStream {
        private val lock = Object()
        private val inbound = LinkedBlockingDeque<ByteArray>()
        private val peer = Iap2LinkEngine(Iap2LinkConfig(zeroAcknowledgements = true, controlSessionVersion = 2))
        @Volatile var closed = false
            private set

        init {
            peer.start(wiredInitiator = false, nowMillis = nowMillis())
            flushPeer()
        }

        override fun send(data: ByteArray) = synchronized(lock) {
            if (closed) throw IOException("wire closed")
            peer.feed(data, nowMillis())
            flushPeer()
        }

        fun control(frame: Iap2Frame) = rawControl(frame.encodedFrame())

        fun rawControl(bytes: ByteArray) = synchronized(lock) {
            assertTrue("Peer must complete negotiation", peer.writable())
            peer.sendControl(bytes, nowMillis())
            flushPeer()
        }

        fun endPeer() { inbound.addLast(ByteArray(0)) }

        override fun recv(maxBytes: Int, timeoutMillis: Long): ByteArray? {
            val bytes = inbound.pollFirst(timeoutMillis, TimeUnit.MILLISECONDS) ?: return null
            if (bytes.size <= maxBytes) return bytes
            inbound.addFirst(bytes.copyOfRange(maxBytes, bytes.size))
            return bytes.copyOf(maxBytes)
        }

        override fun close() { closed = true; inbound.clear(); inbound.addLast(ByteArray(0)) }

        private fun flushPeer() {
            val output = peer.takeOutput()
            if (output.isNotEmpty()) inbound.addLast(output)
            while (peer.pollEvent() != null) Unit
        }

        private fun nowMillis() = System.nanoTime() / 1_000_000
    }
}
