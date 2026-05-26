package com.savedata.app.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.savedata.app.App

data class AppInfo(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val icon: Drawable?,
    val isSystem: Boolean
)

object AppInfoLoader {

    fun loadApps(): List<AppInfo> {
        val pm = App.instance.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }
        return packages.mapNotNull { pkg ->
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppInfo(
                packageName = pkg.packageName,
                appName = pm.getApplicationLabel(appInfo).toString(),
                uid = appInfo.uid,
                icon = null,
                isSystem = isSystem
            )
        }.sortedWith(compareBy<AppInfo> { it.isSystem }.thenBy { it.appName.lowercase() })
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
