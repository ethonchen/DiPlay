package com.shilapi.xcertplay.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.IOException

/** Fail before offering installation when a mirror returns another app or an older build. */
internal object UpdateApkVerifier {
    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File, expectedCode: Long?) {
        val manager = context.packageManager
        val apk = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
            ?: throw IOException("The downloaded file is not an Android APK")
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        if (apk.packageName != installed.packageName) throw IOException("APK package does not match this DiPlay installation")
        val code = PackageInfoCompat.getLongVersionCode(apk)
        if (code <= PackageInfoCompat.getLongVersionCode(installed) || (expectedCode != null && code != expectedCode)) {
            throw IOException("APK versionCode is not a newer matching build")
        }
        if (Build.VERSION.SDK_INT >= 24 && (apk.applicationInfo?.minSdkVersion ?: 1) > Build.VERSION.SDK_INT) {
            throw IOException("APK requires a newer Android version")
        }
        val currentSignatures = installed.signatures?.toSet().orEmpty()
        val downloadedSignatures = apk.signatures?.toSet().orEmpty()
        if (currentSignatures.isEmpty() || currentSignatures != downloadedSignatures) {
            throw IOException("APK signing certificate does not match the installed DiPlay")
        }
    }
}
