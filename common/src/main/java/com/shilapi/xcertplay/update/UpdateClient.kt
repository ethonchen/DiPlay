package com.shilapi.xcertplay.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

internal object UpdateClient {
    // This fork ships API-23 builds. Upstream releases have a higher installation
    // minimum and a different signing/package identity; they cannot update this app.
    internal const val RELEASES_URL =
        "https://api.github.com/repos/ethonchen/DiPlay/releases?per_page=30"
    internal const val LATEST_MANIFEST_URL =
        "https://github.com/ethonchen/DiPlay/releases/latest/download/update.json"
    private const val CONNECT_TIMEOUT_MILLIS = 10_000
    private const val READ_TIMEOUT_MILLIS = 30_000
    private const val MAXIMUM_TEXT_BYTES = 4 * 1024 * 1024
    private const val BUFFER_BYTES = 64 * 1024

    internal fun fetchText(
        url: String,
        accept: String?,
        userAgent: String,
        openConnection: (URL) -> URLConnection = { it.openConnection() },
    ): String {
        return fetchValidatedText(url, accept, userAgent, { it }, openConnection)
    }

    internal fun <T> fetchValidatedText(
        url: String,
        accept: String?,
        userAgent: String,
        validate: (String) -> T,
        openConnection: (URL) -> URLConnection = { it.openConnection() },
    ): T = fromSources(url) { source ->
        val body = fetchTextOnce(source, accept, userAgent, openConnection)
        try {
            validate(body)
        } catch (failure: Exception) {
            throw IOException("Invalid update response from $source", failure)
        }
    }

    private fun fetchTextOnce(
        url: String,
        accept: String?,
        userAgent: String,
        openConnection: (URL) -> URLConnection,
    ): String {
        val connection = openConnection(URL(url)) as? HttpURLConnection
            ?: throw IOException("Request to $url did not open an HTTP connection")
        try {
            UpdateTls.configure(connection)
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Cache-Control", "no-cache")
            accept?.let { connection.setRequestProperty("Accept", it) }
            connection.setRequestProperty("User-Agent", userAgent)
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readUtf8Limited(url, it) }.orEmpty()
            if (statusCode !in 200..299) throw IOException("Request to $url failed: $statusCode")
            return body
        } finally {
            connection.disconnect()
        }
    }

    internal fun download(url: String, destination: File, onProgress: (Long, Long?) -> Unit) {
        downloadValidated(url, destination, onProgress = onProgress)
    }

    internal fun downloadValidated(
        url: String,
        destination: File,
        validate: (File) -> Unit = {},
        openConnection: (URL) -> URLConnection = { it.openConnection() },
        onProgress: (Long, Long?) -> Unit,
    ) {
        fromSources(url) { source ->
            try {
                // Restart each attempt from zero. A proxy error page or a partial file must
                // never be appended to a valid APK from the next source.
                destination.delete()
                onProgress(0L, null)
                downloadOnce(source, destination, openConnection, onProgress)
                validate(destination)
            } catch (failure: Exception) {
                destination.delete()
                throw if (failure is IOException) failure else IOException("Invalid download from $source", failure)
            }
        }
    }

    private fun downloadOnce(
        url: String,
        destination: File,
        openConnection: (URL) -> URLConnection,
        onProgress: (Long, Long?) -> Unit,
    ) {
        destination.parentFile?.mkdirs()
        val connection = openConnection(URL(url)) as? HttpURLConnection
            ?: throw IOException("Download from $url did not open an HTTP connection")
        try {
            UpdateTls.configure(connection)
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "DiPlay-Q7-Updater")
            connection.setRequestProperty("Accept-Encoding", "identity")
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) throw IOException("Download from $url failed: $statusCode")
            // getContentLengthLong is API 24; the HTTP header remains available on API 23.
            val total = connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
            var written = 0L
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                }
            }
            if (total != null && written != total) throw IOException("Incomplete download from $url")
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> fromSources(url: String, action: (String) -> T): T {
        val failures = mutableListOf<IOException>()
        for (source in UpdateSources.candidates(url)) {
            try {
                return action(source)
            } catch (failure: IOException) {
                failures.add(failure)
            }
        }
        throw IOException("All update sources failed for $url: ${failures.lastOrNull()?.message}", failures.lastOrNull()).apply {
            failures.dropLast(1).forEach { addSuppressed(it) }
        }
    }

    private fun readUtf8Limited(url: String, stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAXIMUM_TEXT_BYTES) throw IOException("Response from $url is too large")
            output.write(buffer, 0, read)
        }
        return output.toString("UTF-8")
    }
}
