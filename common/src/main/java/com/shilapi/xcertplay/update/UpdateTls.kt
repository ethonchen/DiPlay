package com.shilapi.xcertplay.update

import android.os.Build
import java.net.URLConnection
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Updated public roots for the updater on Android 6/7; never changes process-wide TLS. */
internal object UpdateTls {
    private val certificates: List<X509Certificate> by lazy {
        val pem = UpdateTls::class.java.classLoader!!.getResourceAsStream("update-ca-bundle.crt")
            ?.bufferedReader(Charsets.US_ASCII)?.use { it.readText() }
            ?: throw CertificateException("Update CA bundle is missing")
        val factory = CertificateFactory.getInstance("X.509")
        // Android 6's generateCertificates reads only the first PEM certificate.
        // Give generateCertificate one block at a time, with a markable byte stream.
        val blocks = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----").findAll(pem)
        blocks.map { block ->
            (block.value + "\n").byteInputStream(Charsets.US_ASCII).use {
                factory.generateCertificate(it) as X509Certificate
            }
        }.toList().also { require(it.size >= 100) { "Update CA bundle is incomplete" } }
    }

    private val trustManager: X509TrustManager by lazy {
        val platform = manager(null)
        val roots = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        certificates.forEachIndexed { index, certificate ->
            require(certificate.basicConstraints >= 0) { "Update trust requires CA certificates" }
            roots.setCertificateEntry("mozilla-$index", certificate)
        }
        val modern = manager(roots)
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                platform.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    platform.checkServerTrusted(chain, authType)
                } catch (platformFailure: CertificateException) {
                    try {
                        modern.checkServerTrusted(chain, authType)
                    } catch (failure: CertificateException) {
                        failure.addSuppressed(platformFailure)
                        throw failure
                    }
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> =
                platform.acceptedIssuers + modern.acceptedIssuers
        }
    }

    private val socketFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory
    }

    fun configure(connection: URLConnection) {
        if (connection !is HttpsURLConnection || Build.VERSION.SDK_INT >= 26) return
        connection.sslSocketFactory = socketFactory
        // Keep HttpsURLConnection's default hostname verification. Only CA trust is extended.
    }

    private fun manager(store: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()

    internal fun trustManagerForTest(): X509TrustManager = trustManager
    internal fun bundledRootCountForTest(): Int = certificates.size
}
