package com.shilapi.xcertplay.update

internal object UpdateVersion {
    private val TRIPLE = Regex("(\\d+)\\.(\\d+)\\.(\\d+)")

    internal fun parse(value: String): Triple<Int, Int, Int>? {
        val match = TRIPLE.find(value) ?: return null
        val parts = (1..3).map { match.groupValues[it].toIntOrNull() ?: return null }
        return Triple(parts[0], parts[1], parts[2])
    }

    internal fun isNewer(release: UpdateRelease, installed: String, installedCode: Long): Boolean =
        release.versionCode?.let { it > installedCode } ?: isNewer(release.tagName, installed)

    internal fun isNewer(remote: String, installed: String): Boolean {
        val target = parse(remote) ?: return false
        val current = parse(installed) ?: return true
        if (target.first != current.first) return target.first > current.first
        if (target.second != current.second) return target.second > current.second
        if (target.third != current.third) return target.third > current.third
        // Legacy releases have no manifest. Recognize revisions of the same Q7 series,
        // ignoring build-only suffixes, without treating arbitrary prereleases as upgrades.
        val revision = Regex("android6-q7-([a-z]+)(\\d+)")
        val remoteRevision = revision.find(remote) ?: return false
        val localRevision = revision.find(installed) ?: return false
        return remoteRevision.groupValues[1] == localRevision.groupValues[1] &&
            (remoteRevision.groupValues[2].toLongOrNull() ?: 0) > (localRevision.groupValues[2].toLongOrNull() ?: 0)
    }
}
