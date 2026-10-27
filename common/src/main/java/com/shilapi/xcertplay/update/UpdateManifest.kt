package com.shilapi.xcertplay.update

import android.os.Build
import org.json.JSONObject

/** Small release attachment; also reachable when the GitHub releases API is blocked. */
internal object UpdateManifest {
    fun parse(text: String, expectedTag: String? = null, sdk: Int = Build.VERSION.SDK_INT): UpdateRelease {
        val json = JSONObject(text)
        require(json.getInt("schemaVersion") == 1) { "Unsupported update manifest" }
        require(json.getString("packageName") == "com.shihab.diplay.hudtest") { "Wrong update package" }
        require(json.getInt("minSdk") in 1..sdk) { "Update requires a newer Android version" }
        val tag = json.getString("tagName")
        require(expectedTag == null || tag == expectedTag) { "Update manifest belongs to a different release" }
        val code = json.getLong("versionCode")
        val name = json.getString("apkName")
        val url = json.getString("apkUrl")
        val sha = json.getString("sha256").lowercase()
        val size = json.getLong("size")
        require(code > 0 && size > 0 && tag.isNotBlank()) { "Incomplete update manifest" }
        require(UpdateCatalog.safeApkName(name) && UpdateCatalog.trustedAsset(url)) { "Invalid update asset" }
        require(sha.matches(Regex("[0-9a-f]{64}"))) { "Invalid update checksum" }
        return UpdateRelease(tag, name, url, "", sha256 = sha, versionCode = code, size = size)
    }
}
