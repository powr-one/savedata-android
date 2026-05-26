package com.savedata.app.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.savedata.app.App

data class AppInfo(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val icon: Drawable?,
    val isSystem: Boolean
)

object AppInfoLoader {

    fun loadApps(includeSystem: Boolean): List<AppInfo> {
        val pm = App.instance.packageManager
        val packages = pm.getInstalledPackages(0)
        return packages.mapNotNull { pkg ->
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystem && isSystem) return@mapNotNull null
            AppInfo(
                packageName = pkg.packageName,
                appName = pm.getApplicationLabel(appInfo).toString(),
                uid = appInfo.uid,
                icon = try { pm.getApplicationIcon(pkg.packageName) } catch (e: Exception) { null },
                isSystem = isSystem
            )
        }.sortedWith(
            compareBy<AppInfo> { it.isSystem }.thenBy { it.appName.lowercase() }
        )
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
}
