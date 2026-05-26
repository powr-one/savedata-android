package com.savedata.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.TrafficStats
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AppRepository(
    private val ruleDao: AppRuleDao,
    private val trafficDao: TrafficRecordDao,
    private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("savedata_prefs", Context.MODE_PRIVATE)

    val allRules: Flow<List<AppRule>> = ruleDao.getAllRules()
    val allTrafficRecords: Flow<List<TrafficRecord>> = trafficDao.getAllRecords()

    suspend fun setBlocked(packageName: String, blocked: Boolean) {
        val existing = ruleDao.getRule(packageName)
        if (existing != null) {
            ruleDao.setBlocked(packageName, blocked)
        } else {
            ruleDao.insertRule(AppRule(packageName = packageName, blocked = blocked))
        }
    }

    suspend fun isBlocked(packageName: String): Boolean =
        ruleDao.getRule(packageName)?.blocked ?: false

    suspend fun getBlockedPackages(): List<String> =
        ruleDao.getBlockedPackages()

    suspend fun updateTrafficForUid(packageName: String, uid: Int) {
        val rxNow = TrafficStats.getUidRxBytes(uid)
        val txNow = TrafficStats.getUidTxBytes(uid)
        if (rxNow == TrafficStats.UNSUPPORTED.toLong() || txNow == TrafficStats.UNSUPPORTED.toLong()) return

        val record = trafficDao.getRecord(packageName)
        if (record == null) {
            trafficDao.insertRecord(
                TrafficRecord(
                    packageName = packageName,
                    uid = uid,
                    rxBytes = rxNow,
                    txBytes = txNow,
                    rxBytesBaseline = rxNow,
                    txBytesBaseline = txNow,
                    periodStartMs = System.currentTimeMillis()
                )
            )
        } else {
            trafficDao.updateTraffic(packageName, rxNow, txNow)
        }
    }

    suspend fun resetAllTraffic() {
        val startMs = System.currentTimeMillis()
        trafficDao.resetAllBaselines(startMs)
    }

    fun getPeriodHours(): Int = prefs.getInt("period_hours", 24)

    fun setPeriodHours(hours: Int) {
        prefs.edit().putInt("period_hours", hours).apply()
    }

    fun isVpnEnabled(): Boolean = prefs.getBoolean("vpn_enabled", false)

    fun setVpnEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("vpn_enabled", enabled).apply()
    }

    fun isShowSystemApps(): Boolean = prefs.getBoolean("show_system_apps", true)

    fun setShowSystemApps(show: Boolean) {
        prefs.edit().putBoolean("show_system_apps", show).apply()
    }
}
