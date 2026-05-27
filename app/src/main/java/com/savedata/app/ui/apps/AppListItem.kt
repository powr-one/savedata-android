package com.savedata.app.ui.apps

import android.graphics.drawable.Drawable

data class AppListItem(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isBlocked: Boolean,
    val rxBytes: Long,
    val txBytes: Long,
    val rxWifi: Long = 0L,
    val txWifi: Long = 0L,
    val rxMobile: Long = 0L,
    val txMobile: Long = 0L
) {
    val totalBytes: Long get() = rxBytes + txBytes
}
