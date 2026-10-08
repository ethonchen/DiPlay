package com.shilapi.xcertplay.transport

import com.shilapi.xcertplay.compat.Base64Compat
import org.bouncycastle.asn1.pkcs.RSAPublicKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 25], manifest = Config.NONE)
class Android6LockdownTlsTest {
    @Test fun syntheticUsbPairRecordCanCreateAClientTlsEngineOnAndroid6And7() {
        val device = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val key = device.public as java.security.interfaces.RSAPublicKey
        val encoded = Base64Compat.encode(RSAPublicKey(key.modulus, key.publicExponent).encoded)
        val pem = "-----BEGIN RSA PUBLIC KEY-----\n$encoded\n-----END RSA PUBLIC KEY-----\n".toByteArray()
        val pairRecord = LockdownPairRecordGenerator.generate(pem, "02:00:00:00:00:01", "synthetic-host", "synthetic-system")
        val engine = LockdownTlsEngineFactory.create(pairRecord)
        assertTrue(engine.useClientMode)
        assertTrue(engine.enabledProtocols.isNotEmpty())
        assertTrue(engine.enabledCipherSuites.isNotEmpty())
        engine.closeOutbound()
    }
}
