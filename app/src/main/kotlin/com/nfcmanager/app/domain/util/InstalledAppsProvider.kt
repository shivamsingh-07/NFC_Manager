package com.nfcmanager.app.domain.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class AppEntry(val packageName: String, val label: String)

    /**
     * Returns launchable apps visible to us via the `<queries>` manifest
     * declaration. Sorted by label for a predictable picker order. The
     * caller's own package is excluded so users can't accidentally bind a
     * tag to "open NFC Manager".
     */
    fun list(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val ownPackage = context.packageName
        return resolveInfos
            .asSequence()
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == ownPackage) return@mapNotNull null
                val label = runCatching { ri.loadLabel(pm).toString() }.getOrNull() ?: pkg
                AppEntry(pkg, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
