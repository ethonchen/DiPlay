package com.shilapi.xcertplay.update

import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLConnection

internal object UpdateDownloader {
    fun download(
        release: UpdateRelease,
        destination: File,
        userAgent: String,
        verifyApk: (File) -> Unit = {},
        openConnection: (URL) -> URLConnection = { it.openConnection() },
        onProgress: (Long, Long?) -> Unit,
    ) {
        val checksum = release.sha256 ?: UpdateClient.fetchValidatedText(
            release.checksumsUrl, "text/plain", userAgent,
            { text -> UpdateChecksums.parse(text)[release.apkName] ?: throw IOException("APK checksum is missing") },
            openConnection,
        )
        UpdateClient.downloadValidated(release.apkUrl, destination, validate = { file ->
            if (release.size != null && file.length() != release.size) throw IOException("Incomplete APK download")
            if (!UpdateChecksums.matches(mapOf(release.apkName to checksum), release.apkName, UpdateChecksums.sha256Hex(file))) {
                throw IOException("Checksum mismatch for ${release.apkName}")
            }
            verifyApk(file)
        }, openConnection = openConnection, onProgress = onProgress)
    }
}
