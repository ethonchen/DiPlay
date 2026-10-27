package com.shilapi.xcertplay.update

import java.net.HttpURLConnection
import java.net.URL

/** Explicit ADB test of the actual optimized updater's TLS path. Never runs at startup. */
object Q7UpdateNetworkProbe {
    @JvmStatic fun main(urls: Array<String>) {
        for (value in urls) {
            val connection = URL(value).openConnection() as HttpURLConnection
            try {
                UpdateTls.configure(connection)
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("User-Agent", "DiPlay-Q7-API23-Probe")
                val status = connection.responseCode
                val body = (if (status < 400) connection.inputStream else connection.errorStream)?.use {
                    val bytes = ByteArray(160)
                    val read = it.read(bytes)
                    if (read > 0) String(bytes, 0, read, Charsets.UTF_8).replace('\n', ' ') else ""
                }.orEmpty()
                println("Q7-UPDATE-HTTPS $value status=$status prefix=$body")
            } catch (failure: Exception) {
                println("Q7-UPDATE-HTTPS $value ERROR $failure")
                generateSequence(failure.cause) { it.cause }.take(6).forEach { println("cause: $it") }
            } finally {
                connection.disconnect()
            }
        }
    }
}
