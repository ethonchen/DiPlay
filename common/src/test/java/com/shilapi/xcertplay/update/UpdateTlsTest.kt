package com.shilapi.xcertplay.update

import java.net.URL
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 25])
class UpdateTlsTest {
    @Test fun modernPublicRootsAreLoadedAndUnknownSelfSignedCertificatesAreRejected() {
        assertEquals(118, UpdateTls.bundledRootCountForTest())
        val manager = UpdateTls.trustManagerForTest()
        assertTrue(manager.acceptedIssuers.any { "ISRG Root X1" in it.subjectX500Principal.name })
        assertTrue(manager.acceptedIssuers.any { "GTS Root R4" in it.subjectX500Principal.name })
        val unknown = javaClass.classLoader!!.getResourceAsStream("update/untrusted-ca.crt")!!.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        assertThrows(CertificateException::class.java) { manager.checkServerTrusted(arrayOf(unknown), "RSA") }
    }

    @Test fun changesOnlyTheRequestedConnectionAndKeepsHostnameVerification() {
        val global = HttpsURLConnection.getDefaultSSLSocketFactory()
        val connection = URL("https://github.com/").openConnection() as HttpsURLConnection
        val verifier = connection.hostnameVerifier
        UpdateTls.configure(connection)
        assertNotSame(global, connection.sslSocketFactory)
        assertSame(global, HttpsURLConnection.getDefaultSSLSocketFactory())
        assertSame(verifier, connection.hostnameVerifier)
    }
}
