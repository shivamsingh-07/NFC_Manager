package com.nfcmanager.app.domain.util

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates that a package name is:
 *  - syntactically valid (no injection into intent extras),
 *  - currently installed on the device,
 *  - has a launcher entry we can actually open.
 *
 * We intentionally avoid `PackageManager.getPackageInfo` without the launcher
 * filter so we can't be tricked into naming an installed-but-unlaunchable
 * support library as the action target.
 */
@Singleton
class PackageValidator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun isValidFormat(pkg: String): Boolean {
        if (pkg.isBlank() || pkg.length > MAX_PACKAGE_LENGTH) return false
        // Android package rules: segments separated by '.', each starting with
        // a letter, followed by letters/digits/underscore.
        return PACKAGE_REGEX.matches(pkg)
    }

    fun isInstalledAndLaunchable(pkg: String): Boolean {
        if (!isValidFormat(pkg)) return false
        val pm = context.packageManager
        return try {
            pm.getLaunchIntentForPackage(pkg) != null
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun labelFor(pkg: String): String? = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    }

    companion object {
        private const val MAX_PACKAGE_LENGTH = 255
        private val PACKAGE_REGEX =
            Regex("^[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    }
}
