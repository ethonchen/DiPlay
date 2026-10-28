package com.shilapi.xcertplay.update

import java.net.URI
import org.json.JSONArray

/** The published release that a newer build can be downloaded from. */
internal data class UpdateRelease(
    val tagName: String,
    val apkName: String,
    val apkUrl: String,
    val checksumsUrl: String,
    val sha256: String? = null,
    val versionCode: Long? = null,
    val manifestUrl: String? = null,
    val size: Long? = null,
)

internal object UpdateCatalog {
    internal const val CHECKSUMS_FILE = "SHA256SUMS.txt"
    internal const val MANIFEST_FILE = "update.json"
    internal const val ASSET_PREFIX = "https://github.com/ethonchen/DiPlay/releases/download/"
    private const val DOWNLOAD_HOST = "github.com"
    // The name becomes a file in the cache folder, so it can hold no path.
    private val APK_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk")

    private fun isDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && uri.host.equals(DOWNLOAD_HOST, ignoreCase = true) &&
            uri.rawUserInfo == null && uri.port == -1
    }

    internal fun safeApkName(name: String): Boolean = APK_NAME.matches(name)

    internal fun trustedAsset(url: String): Boolean = isDownloadUrl(url) && url.startsWith(ASSET_PREFIX)

    internal fun parse(json: String): UpdateRelease? = candidates(json).firstOrNull()

    /** Incomplete, experimental and other-device releases must not hide a usable Q7 update. */
    internal fun candidates(json: String): List<UpdateRelease> {
        val result = mutableListOf<UpdateRelease>()
        val releases = JSONArray(json)
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
            val assets = release.optJSONArray("assets") ?: continue
            var apkName = ""
            var apkUrl = ""
            var checksumsUrl = ""
            var manifestUrl: String? = null
            var sha256: String? = null
            var size: Long? = null
            var bestScore = -1
            for (assetIndex in 0 until assets.length()) {
                val asset = assets.optJSONObject(assetIndex) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (!trustedAsset(url)) continue
                val lower = name.lowercase()
                val score = when {
                    "q7" in lower -> 3
                    "android6" in lower || "android-6" in lower -> 2
                    listOf("arm64", "x86", "tv", "automotive", "maphost", "source-only").any { it in lower } -> -1
                    "diplay" in lower -> 1
                    else -> -1
                }
                if (safeApkName(name) && score > bestScore) {
                    bestScore = score
                    apkName = name
                    apkUrl = url
                    sha256 = asset.optString("digest").removePrefix("sha256:")
                        .takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }?.lowercase()
                    size = asset.optLong("size").takeIf { it > 0 }
                }
                if (name.equals(CHECKSUMS_FILE, ignoreCase = true)) checksumsUrl = url
                if (name == MANIFEST_FILE) manifestUrl = url
            }
            if (apkUrl.isEmpty() || (checksumsUrl.isEmpty() && sha256 == null && manifestUrl == null)) continue
            result.add(UpdateRelease(release.optString("tag_name"), apkName, apkUrl, checksumsUrl,
                sha256 = sha256, manifestUrl = manifestUrl, size = size))
        }
        return result
    }
}
