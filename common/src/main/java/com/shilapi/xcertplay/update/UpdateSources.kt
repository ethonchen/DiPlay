package com.shilapi.xcertplay.update

import java.net.URL

/** Only wrap GitHub URLs; keep the original URL intact, including its query string. */
internal object UpdateSources {
    private val proxies = listOf("https://gh-proxy.com/", "https://ghproxy.net/")

    fun candidates(url: String): List<String> {
        val host = URL(url).host.lowercase()
        if (host !in setOf("github.com", "api.github.com", "raw.githubusercontent.com")) return listOf(url)
        val mirrors = proxies.map { it + url }
        // Read live metadata first. Downloads use the owner's preferred proxy order.
        return if (host == "api.github.com") listOf(url) + mirrors else mirrors + url
    }
}
