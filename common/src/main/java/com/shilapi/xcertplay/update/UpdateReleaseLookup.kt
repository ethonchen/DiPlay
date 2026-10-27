package com.shilapi.xcertplay.update

import java.net.URL
import java.net.URLConnection
import java.io.IOException

internal object UpdateReleaseLookup {
    fun latest(
        installedVersion: String,
        userAgent: String,
        installedCode: Long = 0L,
        openConnection: (URL) -> URLConnection = { it.openConnection() },
    ): UpdateRelease? {
        val releases = try {
            UpdateClient.fetchValidatedText(UpdateClient.RELEASES_URL, "application/vnd.github+json",
                userAgent, UpdateCatalog::candidates, openConnection)
        } catch (apiFailure: IOException) {
            // Both configured proxies support release attachments even if their API route
            // does not work. Do not mistake an unreachable API for "up to date".
            return try {
                manifest(UpdateClient.LATEST_MANIFEST_URL, null, userAgent, openConnection)
                    .takeIf { UpdateVersion.isNewer(it, installedVersion, installedCode) }
            } catch (manifestFailure: IOException) {
                manifestFailure.addSuppressed(apiFailure)
                throw manifestFailure
            }
        }
        var failure: IOException? = null
        val updates = releases.mapNotNull { release ->
            try {
                val resolved = release.manifestUrl?.let { manifest(it, release.tagName, userAgent, openConnection) }
                    ?: release
                resolved.takeIf { UpdateVersion.isNewer(it, installedVersion, installedCode) }
            } catch (error: IOException) {
                failure = error
                null
            }
        }
        // GitHub's creation order need not match the Android build order.
        val newest = updates.maxWithOrNull(compareBy<UpdateRelease> { it.versionCode ?: 0L }
            .thenBy { UpdateVersion.parse(it.tagName)?.first ?: 0 }
            .thenBy { UpdateVersion.parse(it.tagName)?.second ?: 0 }
            .thenBy { UpdateVersion.parse(it.tagName)?.third ?: 0 })
        if (newest == null && failure != null) throw failure!!
        return newest
    }

    private fun manifest(url: String, tag: String?, userAgent: String, openConnection: (URL) -> URLConnection) =
        UpdateClient.fetchValidatedText(url, "application/json", userAgent,
            { UpdateManifest.parse(it, tag) }, openConnection)
}
