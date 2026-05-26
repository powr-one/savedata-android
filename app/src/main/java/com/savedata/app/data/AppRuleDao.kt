package com.savedata.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules")
    fun getAllRules(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun getRule(packageName: String): AppRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AppRule)

    @Query("UPDATE app_rules SET blocked = :blocked WHERE packageName = :packageName")
    suspend fun setBlocked(packageName: String, blocked: Boolean)

    @Query("SELECT packageName FROM app_rules WHERE blocked = 1")
    suspend fun getBlockedPackages(): List<String>

    @Query("DELETE FROM app_rules WHERE packageName = :packageName")
    suspend fun deleteRule(packageName: String)
}
