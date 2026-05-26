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
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}
