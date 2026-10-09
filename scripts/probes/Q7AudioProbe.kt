package com.shilapi.xcertplay

import android.os.Build
import com.shilapi.xcertplay.airplay.AirPlayCrypto
import com.shilapi.xcertplay.airplay.AudioStream
import com.shilapi.xcertplay.airplay.AudioStreamCodec
import com.shilapi.xcertplay.airplay.AudioStreamId
import com.shilapi.xcertplay.media.AndroidMediaSink
import java.io.File
import java.io.RandomAccessFile
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

/** CI-only driver. Copied into q7 sources for the test build; never shipped in the release APK. */
object Q7AudioProbe {
    @JvmStatic fun main(arguments: Array<String>) {
        check(Build.VERSION.SDK_INT == 23)
        val frames = adtsFrames(File(arguments[0]).readBytes())
        println("ENV sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.joinToString()} cores=${Runtime.getRuntime().availableProcessors()}")
        println("ENV " + File("/proc/meminfo").readLines().take(3).joinToString(" "))
        scenario("baseline", 300, frames)
        scenario("cpu-io", 300, frames, competingLoad = true)
        scenario("hold-650-buffer300", 300, frames, gapMillis = 650)
        scenario("hold-650-buffer1000", 1000, frames, gapMillis = 650)
        scenario("hold-1250-buffer1000", 1000, frames, gapMillis = 1250)
        scenario("loss-650-buffer1000", 1000, frames, gapMillis = 650, losePackets = true)
        println("Q7_AUDIO_PROBE PASS scenarios=6")
    }

    private fun scenario(name: String, buffer: Int, frames: List<ByteArray>,
        competingLoad: Boolean = false, gapMillis: Int = 0, losePackets: Boolean = false) {
        println("BEGIN scenario=$name bufferMs=$buffer durationSeconds=24 gapMs=$gapMillis lostPackets=$losePackets syntheticCpuIo=$competingLoad")
        val diagnostics = java.util.Collections.synchronizedList(mutableListOf<String>())
        val report: (String) -> Unit = { line ->
            diagnostics.add(line)
            println("DIAG scenario=$name $line")
        }
        val sink = AndroidMediaSink(mediaBufferMillis = buffer, onAudioDiagnostic = report)
        val key = ByteArray(32) { it.toByte() }
        val format = AudioStreamCodec.fromFormatBits(0x800000L, 102, "media")
        val id = AudioStreamId(102, "media")
        val received = AtomicInteger()
        val authErrors = AtomicInteger()
        val stream = AudioStream(key, 102, report)
        val port = stream.listen(object : AudioStream.Listener {
            override fun onStarted(firstSample: Int) { sink.onAudioStarted(id, format, firstSample) }
            override fun onRtp(rtp: ByteArray, sample: Int) {
                received.incrementAndGet()
                sink.onAudioRtp(id, format, rtp, sample)
            }
            override fun onPacket(wire: ByteArray, rtp: ByteArray?, sample: Int?, error: Throwable?) {
                if (error != null) authErrors.incrementAndGet()
            }
        }).first
        val loadRunning = AtomicBoolean(competingLoad)
        val loadWorkers = if (competingLoad) (0 until 3).map { worker ->
            Thread({
                val block = ByteArray(64 * 1024) { (it + worker).toByte() }
                val digest = MessageDigest.getInstance("SHA-256")
                while (loadRunning.get()) {
                    val stop = System.nanoTime() + 20_000_000L
                    while (loadRunning.get() && System.nanoTime() < stop) digest.digest(block)
                    Thread.sleep(20)
                }
            }, "synthetic-cpu-$worker").apply { start() }
        } else emptyList()
        val ioWorker = if (competingLoad) Thread({
            val file = File("/data/local/tmp/q7-audio-load.bin")
            try {
                RandomAccessFile(file, "rw").use { out ->
                    val block = ByteArray(256 * 1024)
                    while (loadRunning.get()) {
                        out.write(block)
                        out.fd.sync()
                        if (out.filePointer >= 8 * 1024 * 1024) out.seek(0)
                        Thread.sleep(64)
                    }
                }
            } finally { file.delete() }
        }, "synthetic-io").apply { start() } else null
        val start = System.nanoTime()
        val period = 1024L * 1_000_000_000L / 48_000
        val count = (24L * 48_000 / 1024).toInt()
        var sent = 0
        var intentionallyLost = 0
        var injected = 0
        var maxSenderLateNs = 0L
        try {
            DatagramSocket().use { socket ->
                val host = InetAddress.getByName("127.0.0.1")
                for (index in 0 until count) {
                    val planned = start + index * period
                    while (System.nanoTime() < planned) LockSupport.parkNanos(planned - System.nanoTime())
                    if (gapMillis > 0 && (index == 375 || index == 750)) {
                        println("INJECT scenario=$name index=$index holdMs=$gapMillis")
                        if (!losePackets) Thread.sleep(gapMillis.toLong())
                        injected++
                    }
                    if (losePackets && (index in 375 until 375 + gapMillis * 48 / 1024 ||
                        index in 750 until 750 + gapMillis * 48 / 1024)) {
                        intentionallyLost++
                        continue
                    }
                    maxSenderLateNs = maxOf(maxSenderLateNs, System.nanoTime() - planned)
                    val header = ByteArray(12)
                    header[0] = 0x80.toByte()
                    header[1] = 102
                    header[2] = (index ushr 8).toByte()
                    header[3] = index.toByte()
                    val sample = index * 1024
                    for (b in 0..3) header[4 + b] = (sample ushr (24 - b * 8)).toByte()
                    header[11] = 1
                    val nonce = AirPlayCrypto.nonce64(index.toLong())
                    val sealed = AirPlayCrypto.chachaSeal(key, nonce, frames[index % frames.size], header.copyOfRange(4, 12))
                    val wire = header + sealed + nonce.copyOfRange(4, 12)
                    socket.send(DatagramPacket(wire, wire.size, host, port))
                    sent++
                }
            }
            Thread.sleep(100)
        } finally {
            loadRunning.set(false)
            loadWorkers.forEach { it.join(1000) }
            ioWorker?.join(1000)
            stream.close()
            sink.close()
            Thread.sleep(500)
        }
        val lines = synchronized(diagnostics) { diagnostics.toList() }
        check(authErrors.get() == 0) { "$name authentication errors=${authErrors.get()}" }
        check(lines.none { it.contains("renderer failed") }) { "$name renderer failed" }
        val stats = lines.filter { it.startsWith("audio stats ") }
        check(stats.isNotEmpty()) { "$name no AudioTrack stats" }
        fun number(line: String, keyName: String): Long = Regex("(?:^| )$keyName=([0-9]+)").find(line)?.groupValues?.get(1)?.toLong() ?: 0L
        check(stats.maxOf { number(it, "playbackHeadFrames") } > 48_000) { "$name playback head did not advance" }
        check(stats.sumOf { number(it, "writeErrors") } == 0L) { "$name AudioTrack write errors" }
        val rebuffers = stats.maxOf { number(it, "rebuffers") }
        val dropped = stats.sumOf { number(it, "dropped") }
        val decoderDropped = stats.maxOf { number(it, "decoderDroppedTotal") }
        val head = stats.maxOf { number(it, "playbackHeadFrames") }
        val totalWritten = stats.maxOf { number(it, "totalWrittenFrames") }
        println("RESULT scenario=$name sent=$sent received=${received.get()} authErrors=${authErrors.get()} intentionalLoss=$intentionallyLost injected=$injected rebuffers=$rebuffers queueDropped=$dropped decoderDropped=$decoderDropped playbackHeadFrames=$head totalWrittenFrames=$totalWritten maxSenderLateMs=${maxSenderLateNs / 1_000_000}")
        Thread.sleep(500)
    }

    private fun adtsFrames(bytes: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var offset = 0
        while (offset + 7 <= bytes.size) {
            check(bytes[offset].toInt() and 255 == 255)
            val headerSize = if (bytes[offset + 1].toInt() and 1 == 1) 7 else 9
            val frameSize = ((bytes[offset + 3].toInt() and 3) shl 11) or
                ((bytes[offset + 4].toInt() and 255) shl 3) or ((bytes[offset + 5].toInt() and 224) ushr 5)
            check(frameSize > headerSize && offset + frameSize <= bytes.size)
            result.add(bytes.copyOfRange(offset + headerSize, offset + frameSize))
            offset += frameSize
        }
        check(result.size > 100)
        return result
    }
}
