package com.savedata.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "traffic_records")
data class TrafficRecord(
    @PrimaryKey val packageName: String,
    val uid: Int,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxBytesBaseline: Long = 0L,
    val txBytesBaseline: Long = 0L,
    val periodStartMs: Long = System.currentTimeMillis()
)
