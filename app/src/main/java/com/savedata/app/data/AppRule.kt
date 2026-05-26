package com.savedata.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val blocked: Boolean = false,
    val wifiBlocked: Boolean = false,
    val mobileBlocked: Boolean = false
)
