package com.savedata.app.util

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

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                updateTraffic()
                delay(2000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun resetBaselines() {
        baselines.clear()
        updateTraffic()
    }

    private fun updateTraffic() {
        val pm = App.instance.packageManager
        val result = HashMap<Int, AppTrafficInfo>()
        try {
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            for (pkg in packages) {
                val uid = pkg.applicationInfo?.uid ?: continue
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx == TrafficStats.UNSUPPORTED.toLong()) continue

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
            Log.e("TrafficMonitor", "Error updating traffic", e)
        }
        _traffic.value = result
    }
}
