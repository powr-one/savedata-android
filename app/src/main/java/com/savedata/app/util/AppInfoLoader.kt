package com.savedata.app.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import com.savedata.app.App

data class AppInfo(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val icon: Drawable?,
    val isSystem: Boolean
)

object AppInfoLoader {

    private const val TAG = "AppInfoLoader"

    fun loadApps(): List<AppInfo> {
        val pm = App.instance.packageManager
        return try {
            @Suppress("DEPRECATION")
            val packages = pm.getInstalledPackages(0)
            Log.d(TAG, "getInstalledPackages(0) returned ${packages.size} packages")
            packages.mapNotNull { pkg ->
                try {
                    val appInfo = pkg.applicationInfo
                    if (appInfo == null) {
                        // fallback: try to get applicationInfo directly
                        val ai = try {
                            pm.getApplicationInfo(pkg.packageName, 0)
                        } catch (_: Exception) { null } ?: return@mapNotNull null
                        val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        AppInfo(
                            packageName = pkg.packageName,
                            appName = pm.getApplicationLabel(ai).toString(),
                            uid = ai.uid,
                            icon = null,
                            isSystem = isSystem
                        )
                    } else {
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        AppInfo(
                            packageName = pkg.packageName,
                            appName = pm.getApplicationLabel(appInfo).toString(),
                            uid = appInfo.uid,
                            icon = null,
                            isSystem = isSystem
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping ${pkg.packageName}: ${e.message}")
                    null
                }
            }.sortedWith(compareBy<AppInfo> { it.isSystem }.thenBy { it.appName.lowercase() })
        } catch (e: Exception) {
            Log.e(TAG, "loadApps failed", e)
            emptyList()
        }
    }

    fun loadIconFor(packageName: String): Drawable? =
        try { App.instance.packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }

    fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "${bytes} B"
        bytes < 1024L * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}
