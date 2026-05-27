package com.savedata.app.util

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.net.ConnectivityManager
import android.net.NetworkStats
import android.net.NetworkStatsManager
import android.net.TrafficStats
import android.os.Process
import android.util.Log
import com.savedata.app.App
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppTrafficInfo(
    val packageName: String,
    val uid: Int,
    val rxTotal: Long,
    val txTotal: Long,
    val rxSincePeriod: Long,
    val txSincePeriod: Long,
    val rxWifi: Long = 0L,
    val txWifi: Long = 0L,
    val rxMobile: Long = 0L,
    val txMobile: Long = 0L
)

private data class Baseline(
    val totalRx: Long, val totalTx: Long,
    val wifiRx: Long = 0L, val wifiTx: Long = 0L,
    val mobileRx: Long = 0L, val mobileTx: Long = 0L
)

class TrafficMonitor(private val scope: CoroutineScope) {

    private val _traffic = MutableStateFlow<Map<Int, AppTrafficInfo>>(emptyMap())
    val traffic: StateFlow<Map<Int, AppTrafficInfo>> = _traffic.asStateFlow()

    private val baselines = HashMap<Int, Baseline>()
    private var job: Job? = null

    private var cachedPackages: List<PackageInfo> = emptyList()
    private var packageCacheTime = 0L

    private val networkStatsManager: NetworkStatsManager by lazy {
        App.instance.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                updateTraffic()
                delay(5_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun resetBaselines() {
        baselines.clear()
        scope.launch(Dispatchers.IO) { updateTraffic() }
    }

    private fun hasUsageStatsPermission(): Boolean = try {
        val appOps = App.instance.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            App.instance.packageName
        ) == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    private fun getPackages(): List<PackageInfo> {
        val now = System.currentTimeMillis()
        if (now - packageCacheTime > 60_000L || cachedPackages.isEmpty()) {
            cachedPackages = try {
                @Suppress("DEPRECATION")
                App.instance.packageManager.getInstalledPackages(0)
            } catch (e: Exception) {
                Log.e("TrafficMonitor", "getInstalledPackages failed", e)
                emptyList()
            }
            packageCacheTime = now
        }
        return cachedPackages
    }

    /** Query all UIDs for one network type in a single call — much faster than per-UID queries. */
    @Suppress("DEPRECATION")
    private fun queryAllByNetwork(networkType: Int): Map<Int, Pair<Long, Long>> {
        return try {
            val end = System.currentTimeMillis()
            val stats = networkStatsManager.queryDetails(networkType, null, 0L, end)
            val result = HashMap<Int, Pair<Long, Long>>()
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                if (uid <= 0) continue
                val cur = result.getOrDefault(uid, 0L to 0L)
                result[uid] = (cur.first + bucket.rxBytes) to (cur.second + bucket.txBytes)
            }
            stats.close()
            result
        } catch (e: Exception) {
            Log.w("TrafficMonitor", "queryDetails type=$networkType failed: ${e.message}")
            emptyMap()
        }
    }

    private fun updateTraffic() {
        val useNetStats = hasUsageStatsPermission()

        val wifiMap = if (useNetStats) queryAllByNetwork(ConnectivityManager.TYPE_WIFI) else emptyMap()
        val mobileMap = if (useNetStats) queryAllByNetwork(ConnectivityManager.TYPE_MOBILE) else emptyMap()

        val result = HashMap<Int, AppTrafficInfo>()
        try {
            for (pkg in getPackages()) {
                val uid = pkg.applicationInfo?.uid ?: continue
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx == TrafficStats.UNSUPPORTED.toLong() || rx < 0) continue

                val wifiRx = wifiMap[uid]?.first ?: 0L
                val wifiTx = wifiMap[uid]?.second ?: 0L
                val mobileRx = mobileMap[uid]?.first ?: 0L
                val mobileTx = mobileMap[uid]?.second ?: 0L

                val bl = baselines.getOrPut(uid) {
                    Baseline(rx, tx, wifiRx, wifiTx, mobileRx, mobileTx)
                }
                result[uid] = AppTrafficInfo(
                    packageName = pkg.packageName,
                    uid = uid,
                    rxTotal = rx,
                    txTotal = tx,
                    rxSincePeriod = maxOf(0L, rx - bl.totalRx),
                    txSincePeriod = maxOf(0L, tx - bl.totalTx),
                    rxWifi   = maxOf(0L, wifiRx   - bl.wifiRx),
                    txWifi   = maxOf(0L, wifiTx   - bl.wifiTx),
                    rxMobile = maxOf(0L, mobileRx - bl.mobileRx),
                    txMobile = maxOf(0L, mobileTx - bl.mobileTx)
                )
            }
        } catch (e: Exception) {
            Log.e("TrafficMonitor", "updateTraffic failed", e)
        }
        _traffic.value = result
    }
}
