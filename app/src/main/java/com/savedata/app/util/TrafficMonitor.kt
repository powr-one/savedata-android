package com.savedata.app.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
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
    val txSincePeriod: Long
)

class TrafficMonitor(private val scope: CoroutineScope) {

    private val _traffic = MutableStateFlow<Map<Int, AppTrafficInfo>>(emptyMap())
    val traffic: StateFlow<Map<Int, AppTrafficInfo>> = _traffic.asStateFlow()

    private val baselines = HashMap<Int, Pair<Long, Long>>()
    private var job: Job? = null

    // Package list cache — expensive to load, refresh every 60s
    private var cachedPackages: List<PackageInfo> = emptyList()
    private var packageCacheTime = 0L

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                updateTraffic()
                delay(5_000L) // 5s is plenty for live stats, saves CPU vs 2s
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

    private fun getPackages(): List<PackageInfo> {
        val now = System.currentTimeMillis()
        if (now - packageCacheTime > 60_000L || cachedPackages.isEmpty()) {
            cachedPackages = try {
                // Flag 0: fastest possible — no metadata, but applicationInfo.uid is always present
                @Suppress("DEPRECATION")
                App.instance.packageManager.getInstalledPackages(0)
            } catch (e: Exception) {
                Log.e("TrafficMonitor", "getInstalledPackages failed", e)
                emptyList()
            }
            packageCacheTime = now
            Log.d("TrafficMonitor", "Package cache refreshed: ${cachedPackages.size} packages")
        }
        return cachedPackages
    }

    private fun updateTraffic() {
        val result = HashMap<Int, AppTrafficInfo>()
        try {
            for (pkg in getPackages()) {
                val uid = pkg.applicationInfo?.uid ?: continue
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx == TrafficStats.UNSUPPORTED.toLong() || rx < 0) continue

                val (baseRx, baseTx) = baselines.getOrPut(uid) { Pair(rx, tx) }
                result[uid] = AppTrafficInfo(
                    packageName = pkg.packageName,
                    uid = uid,
                    rxTotal = rx,
                    txTotal = tx,
                    rxSincePeriod = maxOf(0L, rx - baseRx),
                    txSincePeriod = maxOf(0L, tx - baseTx)
                )
            }
        } catch (e: Exception) {
            Log.e("TrafficMonitor", "updateTraffic failed", e)
        }
        _traffic.value = result
    }
}
