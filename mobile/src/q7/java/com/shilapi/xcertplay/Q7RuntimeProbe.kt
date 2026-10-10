package com.shilapi.xcertplay

import android.os.Build
import com.shilapi.xcertplay.airplay.AirPlayCrypto
import com.shilapi.xcertplay.mfi.LocalMfiProbe
import com.shilapi.xcertplay.media.OpusEncoderSupport

/** Explicit ADB entry point for testing the actual R8 output; never runs at app startup. */
object Q7RuntimeProbe {
    @JvmStatic fun main(arguments: Array<String>) {
        check(Build.VERSION.SDK_INT == 23)
        LocalMfiProbe.main(arguments)
        check(OpusEncoderSupport.isAvailable()) { "Opus microphone encoding unavailable on API 23" }
        val alice = AirPlayCrypto.x25519Generate()
        val bob = AirPlayCrypto.x25519Generate()
        check(AirPlayCrypto.x25519Shared(alice.privateKey, bob.publicKey)
            .contentEquals(AirPlayCrypto.x25519Shared(bob.privateKey, alice.publicKey)))
        val signing = AirPlayCrypto.ed25519Generate()
        val message = byteArrayOf(1, 2, 3)
        val signature = AirPlayCrypto.ed25519Sign(signing.privateKey, message)
        check(AirPlayCrypto.ed25519Verify(signing.publicKey, message, signature))
        check(!AirPlayCrypto.ed25519Verify(signing.publicKey, byteArrayOf(3, 2, 1), signature))
        val key = ByteArray(32) { it.toByte() }
        val aad = ByteArray(128) { (it * 3).toByte() }
        for ((counter, size) in listOf(0, 1, 63, 64, 65, 1400, 16_385, 131_072, 0).withIndex()) {
            val nonce = AirPlayCrypto.nonce64(counter.toLong())
            val plain = ByteArray(size) { (it + counter).toByte() }
            val sealed = AirPlayCrypto.chachaSeal(key, nonce, plain, aad)
            check(AirPlayCrypto.chachaOpen(key, nonce, sealed, aad).contentEquals(plain))
            val damaged = sealed.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            var rejected = false
            try { AirPlayCrypto.chachaOpen(key, nonce, damaged, aad) } catch (_: Exception) { rejected = true }
            check(rejected) { "Tampered packet was accepted" }
            check(AirPlayCrypto.chachaOpen(key, nonce, sealed, aad).contentEquals(plain))
        }
        println("q7-runtime PASS sdk=23 pairing=true authenticatedPackets=true opusMicrophoneAvailable=true implementation=${AirPlayCrypto.chachaImplementation}")
    }
}
