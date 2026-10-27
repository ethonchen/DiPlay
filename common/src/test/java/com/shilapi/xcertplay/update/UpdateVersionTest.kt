package com.shilapi.xcertplay.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test fun androidBuildNumberTakesPrecedenceOverTheDisplayedVersion() {
        val release = UpdateRelease("v0.2.21-android6-q7-update2", "DiPlay.apk", "", "", versionCode = 50)
        assertTrue(UpdateVersion.isNewer(release, "0.2.21-android6-q7-update1-slim", 49))
        assertFalse(UpdateVersion.isNewer(release, "0.2.21-android6-q7-update2-slim", 50))
        assertFalse(UpdateVersion.isNewer(release, "0.2.21-android6-q7-update3-slim", 51))
    }

    @Test fun legacyQ7RevisionComparesNumericallyAndIgnoresSlimSuffix() {
        assertTrue(UpdateVersion.isNewer("v0.2.21-android6-q7-update10", "0.2.21-android6-q7-update9-slim"))
        assertFalse(UpdateVersion.isNewer("v0.2.21-android6-q7-update1", "0.2.21-android6-q7-update1-slim"))
    }

    @Test
    fun newerTagIsAnUpdate() {
        assertTrue(UpdateVersion.isNewer("v0.2.15", "0.2.14"))
    }

    @Test
    fun sameVersionIsNotAnUpdate() {
        assertFalse(UpdateVersion.isNewer("0.2.14", "0.2.14"))
    }

    @Test
    fun olderTagIsIgnored() {
        assertFalse(UpdateVersion.isNewer("v0.2.13", "0.2.14"))
    }

    @Test
    fun numericFieldsCompareLeftToRight() {
        assertTrue(UpdateVersion.isNewer("0.3.0", "0.2.14"))
        assertFalse(UpdateVersion.isNewer("0.2.9", "0.2.14"))
    }

    @Test
    fun preReleaseSuffixStillComparesByNumbers() {
        assertTrue(UpdateVersion.isNewer("0.2.15-beta.1", "0.2.14"))
        assertFalse(UpdateVersion.isNewer("0.2.14-rc.1", "0.2.14"))
    }

    @Test
    fun unparseableRemoteVersionIsNeverAnUpdate() {
        assertFalse(UpdateVersion.isNewer("public-preview", "0.2.14"))
    }

    @Test
    fun unparseableInstalledVersionAcceptsAnyParseableRemote() {
        assertTrue(UpdateVersion.isNewer("0.2.15", "psa.2"))
        assertFalse(UpdateVersion.isNewer("public-preview", "psa.2"))
    }
}
