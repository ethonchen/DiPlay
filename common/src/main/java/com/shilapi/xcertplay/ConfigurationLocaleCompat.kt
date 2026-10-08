// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/** Android 6 has a single locale; LocaleList was introduced in Android 7. */
@Suppress("DEPRECATION")
internal fun Configuration.primaryLocale(): Locale =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) locales[0] ?: Locale.getDefault()
    else locale ?: Locale.getDefault()

internal fun Configuration.copyLocalesFrom(source: Configuration) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) setLocales(source.locales)
    else setLocale(source.primaryLocale())
    setLayoutDirection(source.primaryLocale())
}
