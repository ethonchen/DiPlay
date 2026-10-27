package com.shilapi.xcertplay.update

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class UpdateResponse(url: URL, private val body: ByteArray, private val status: Int = 200,
    private val length: Long = body.size.toLong()) : HttpURLConnection(url) {
    override fun connect() {}
    override fun disconnect() {}
    override fun usingProxy() = false
    override fun getResponseCode() = status
    override fun getInputStream() = ByteArrayInputStream(body)
    override fun getErrorStream() = ByteArrayInputStream(body)
    override fun getHeaderField(name: String?): String? = if (name == "Content-Length") length.toString() else null
}

class UpdateFailoverTest {
    @get:Rule val files = TemporaryFolder()
    private val url = "https://github.com/ethonchen/DiPlay/releases/download/vtest/DiPlay-Q7.apk"
    private val payload = "real APK bytes".toByteArray()
    private fun release() = UpdateRelease("vtest", "DiPlay-Q7.apk", url, "",
        sha256 = UpdateChecksums.sha256Hex(ByteArrayInputStream(payload)), size = payload.size.toLong())

    @Test fun proxyOrderKeepsTheFullGithubUrlAndHasADirectLastResort() {
        assertEquals(listOf("https://gh-proxy.com/$url", "https://ghproxy.net/$url", url), UpdateSources.candidates(url))
        val api = UpdateClient.RELEASES_URL
        assertEquals(listOf(api, "https://gh-proxy.com/$api", "https://ghproxy.net/$api"), UpdateSources.candidates(api))
    }

    @Test fun connectionFailureSwitchesToTheSecondProxy() {
        val requests = mutableListOf<String>()
        val destination = File(files.root, "update.apk")
        UpdateDownloader.download(release(), destination, "test", openConnection = { source ->
            requests.add(source.toString())
            if (requests.size == 1) throw IOException("connect timeout")
            UpdateResponse(source, payload)
        }) { _, _ -> }
        assertEquals(UpdateSources.candidates(url).take(2), requests)
        assertArrayEquals(payload, destination.readBytes())
    }

    @Test fun htmlWithHttp200AndATruncatedDownloadBothFallThroughToGithub() {
        val requests = mutableListOf<String>()
        val destination = File(files.root, "update.apk")
        UpdateDownloader.download(release(), destination, "test", openConnection = { source ->
            requests.add(source.toString())
            when (requests.size) {
                1 -> UpdateResponse(source, "<html>proxy error</html>".toByteArray())
                2 -> UpdateResponse(source, payload.copyOf(3), length = payload.size.toLong())
                else -> UpdateResponse(source, payload)
            }
        }) { _, _ -> }
        assertEquals(UpdateSources.candidates(url), requests)
        assertArrayEquals(payload, destination.readBytes())
    }

    @Test fun sameLengthTamperingAlsoRetriesAndAllFailuresLeaveNoApk() {
        val destination = File(files.root, "update.apk")
        val requests = mutableListOf<String>()
        assertThrows(IOException::class.java) {
            UpdateDownloader.download(release(), destination, "test", openConnection = { source ->
                requests.add(source.toString())
                UpdateResponse(source, ByteArray(payload.size))
            }) { _, _ -> }
        }
        assertEquals(3, requests.size)
        assertFalse(destination.exists())
    }

    @Test fun checksumFileProxyErrorIsRetriedBeforeDownloadingTheApk() {
        val requests = mutableListOf<String>()
        val withSums = release().copy(sha256 = null, checksumsUrl = url.replace("DiPlay-Q7.apk", "SHA256SUMS.txt"))
        UpdateDownloader.download(withSums, File(files.root, "update.apk"), "test", openConnection = { source ->
            requests.add(source.toString())
            val bytes = if (source.toString().endsWith("SHA256SUMS.txt")) {
                if (requests.size == 1) "<html>not a checksum</html>".toByteArray()
                else "${release().sha256} *DiPlay-Q7.apk\n".toByteArray()
            } else payload
            UpdateResponse(source, bytes)
        }) { _, _ -> }
        assertEquals("ghproxy.net", URL(requests[1]).host)
        assertEquals("gh-proxy.com", URL(requests[2]).host)
    }
}
