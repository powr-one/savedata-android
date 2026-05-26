package com.savedata.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrafficRecordDao {
    @Query("SELECT * FROM traffic_records")
    fun getAllRecords(): Flow<List<TrafficRecord>>

    @Query("SELECT * FROM traffic_records WHERE packageName = :packageName")
    suspend fun getRecord(packageName: String): TrafficRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TrafficRecord)

    @Query("UPDATE traffic_records SET rxBytes = :rxBytes, txBytes = :txBytes WHERE packageName = :packageName")
    suspend fun updateTraffic(packageName: String, rxBytes: Long, txBytes: Long)

    @Query("UPDATE traffic_records SET rxBytesBaseline = :rx, txBytesBaseline = :tx, periodStartMs = :startMs WHERE packageName = :packageName")
    suspend fun resetBaseline(packageName: String, rx: Long, tx: Long, startMs: Long)

    @Query("UPDATE traffic_records SET rxBytesBaseline = rxBytes, txBytesBaseline = txBytes, periodStartMs = :startMs")
    suspend fun resetAllBaselines(startMs: Long)

    @Query("DELETE FROM traffic_records")
    suspend fun deleteAll()
}
