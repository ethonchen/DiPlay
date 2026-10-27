package com.shilapi.xcertplay.update

import java.io.IOException
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29])
class Q7UpdateLookupTest {
    private val base = "https://github.com/ethonchen/DiPlay/releases/download/"
    private fun manifest(code: Long = 50, tag: String = "v0.2.21-android6-q7-update2") = JSONObject()
        .put("schemaVersion", 1).put("packageName", "com.shihab.diplay.hudtest")
        .put("minSdk", 23).put("versionCode", code).put("tagName", tag)
        .put("apkName", "DiPlay-Q7.apk").put("apkUrl", "${base}$tag/DiPlay-Q7.apk")
        .put("sha256", "a".repeat(64)).put("size", 123)

    private fun entry(code: Long = 50) = JSONObject()
        .put("tag_name", "v0.2.21-android6-q7-update2").put("assets", JSONArray()
            .put(JSONObject().put("name", "DiPlay-Q7.apk").put("browser_download_url", "${base}vtest/DiPlay-Q7.apk"))
            .put(JSONObject().put("name", "update.json").put("browser_download_url", "${base}vtest/update.json")))

    @Test fun sameUpstreamVersionUsesAndroidBuildNumberAndStopsNoticingAfterInstallation() {
        val factory = { url: URL -> UpdateResponse(url,
            (if (url.host == "api.github.com") JSONArray().put(entry()).toString() else manifest().toString()).toByteArray()) }
        assertEquals(50L, UpdateReleaseLookup.latest("0.2.21-android6-q7-update1-slim", "test", 49, factory)?.versionCode)
        assertNull(UpdateReleaseLookup.latest("0.2.21-android6-q7-update2-slim", "test", 50, factory))
    }

    @Test fun blockedApiAndInvalidFirstProxyFallBackToSecondProxyManifest() {
        val requests = mutableListOf<String>()
        val release = UpdateReleaseLookup.latest("0.2.21-android6-q7-update1-slim", "test", 49) { url ->
            requests.add(url.toString())
            if ("api.github.com" in url.toString()) throw IOException("API blocked")
            UpdateResponse(url, (if (url.host == "gh-proxy.com") "<html>blocked</html>" else manifest().toString()).toByteArray())
        }
        assertEquals(50L, release?.versionCode)
        assertEquals(5, requests.size)
        assertEquals("ghproxy.net", URL(requests.last()).host)
    }

    @Test fun missingAssetsOrPrereleaseDoNotHideTheNextStableQ7Release() {
        val stable = JSONObject().put("tag_name", "v0.2.22").put("assets", JSONArray()
            .put(JSONObject().put("name", "DiPlay-Q7.apk").put("browser_download_url", "${base}v0.2.22/DiPlay-Q7.apk")
                .put("digest", "sha256:" + "a".repeat(64))))
        val json = JSONArray().put(JSONObject().put("tag_name", "incomplete"))
            .put(entry().put("prerelease", true)).put(stable).toString()
        assertEquals("v0.2.22", UpdateCatalog.parse(json)?.tagName)
    }

    @Test fun otherPackageAndroidRequirementAndUnsafeAssetNameAreRejected() {
        for (bad in listOf(manifest().put("packageName", "other.app"), manifest().put("minSdk", 30),
            manifest().put("apkName", "../other.apk"), manifest().put("apkUrl", "https://example.com/app.apk"))) {
            assertThrows(IllegalArgumentException::class.java) { UpdateManifest.parse(bad.toString()) }
        }
    }

    @Test fun highestAndroidBuildWinsEvenWhenGithubListsAnOlderReleaseFirst() {
        val older = entry().put("tag_name", "v0.2.21-android6-q7-update1")
        val newer = entry()
        val manifestOld = manifest(49, "v0.2.21-android6-q7-update1")
        // Give each release's manifest its own URL, as GitHub does.
        older.getJSONArray("assets").getJSONObject(1).put("browser_download_url", "${base}old/update.json")
        val result = UpdateReleaseLookup.latest("0.2.21-android6-q7-upstream1-slim", "test", 48) { url ->
            val body = when {
                url.host == "api.github.com" -> JSONArray().put(older).put(newer).toString()
                "/old/" in url.toString() -> manifestOld.toString()
                else -> manifest().toString()
            }
            UpdateResponse(url, body.toByteArray())
        }
        assertEquals(50L, result?.versionCode)
    }
}
