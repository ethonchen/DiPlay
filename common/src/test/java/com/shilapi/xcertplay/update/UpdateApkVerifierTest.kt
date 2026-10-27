package com.shilapi.xcertplay.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.io.File
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29])
class UpdateApkVerifierTest {
    private val name = "com.shihab.diplay.hudtest"
    private fun info(code: Int, pkg: String = name, signer: String = "010203") = PackageInfo().apply {
        packageName = pkg
        versionCode = code
        signatures = arrayOf(Signature(signer))
    }

    private fun verify(apk: PackageInfo?) {
        val context = mock(Context::class.java)
        val manager = mock(PackageManager::class.java)
        `when`(context.packageName).thenReturn(name)
        `when`(context.packageManager).thenReturn(manager)
        `when`(manager.getPackageInfo(name, PackageManager.GET_SIGNATURES)).thenReturn(info(49))
        `when`(manager.getPackageArchiveInfo("/tmp/update.apk", PackageManager.GET_SIGNATURES)).thenReturn(apk)
        UpdateApkVerifier.verify(context, File("/tmp/update.apk"), 50)
    }

    @Test fun acceptsANewerApkWithTheInstalledPackageAndSigner() = verify(info(50))

    @Test fun rejectsNonApkAnotherAppWrongSignerAndDowngrades() {
        for (apk in listOf(null, info(50, pkg = "other.app"), info(50, signer = "040506"), info(49), info(51))) {
            assertThrows(IOException::class.java) { verify(apk) }
        }
    }
}
