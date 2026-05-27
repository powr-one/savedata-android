package com.savedata.app.util

import android.util.Log
import java.io.File

/**
 * Looks up the UID for a given local port by reading /proc/net/tcp[6] and /proc/net/udp[6].
 *
 * The cache is rebuilt at most every CACHE_TTL_MS. Under heavy traffic the per-packet
 * cost was previously one full file read each time; now it is a single HashMap lookup.
 */
object ProcNetUtils {

    private const val TAG = "ProcNetUtils"
    private const val CACHE_TTL_MS = 250L // rebuild at most 4× per second

    // port (Int) → uid (Int)
    @Volatile private var tcpCache: Map<Int, Int> = emptyMap()
    @Volatile private var udpCache: Map<Int, Int> = emptyMap()
    @Volatile private var tcpCacheTime = 0L
    @Volatile private var udpCacheTime = 0L

    fun getUidForTcp(srcPort: Int): Int {
        refreshTcpIfNeeded()
        return tcpCache[srcPort] ?: -1
    }

    fun getUidForUdp(srcPort: Int): Int {
        refreshUdpIfNeeded()
        return udpCache[srcPort] ?: -1
    }

    private fun refreshTcpIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - tcpCacheTime < CACHE_TTL_MS) return
        val merged = HashMap<Int, Int>()
        merged.putAll(buildPortMap("/proc/net/tcp"))
        merged.putAll(buildPortMap("/proc/net/tcp6"))
        tcpCache = merged
        tcpCacheTime = now
    }

    private fun refreshUdpIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - udpCacheTime < CACHE_TTL_MS) return
        val merged = HashMap<Int, Int>()
        merged.putAll(buildPortMap("/proc/net/udp"))
        merged.putAll(buildPortMap("/proc/net/udp6"))
        udpCache = merged
        udpCacheTime = now
    }

    private fun buildPortMap(path: String): Map<Int, Int> {
        return try {
            val file = File(path)
            if (!file.exists()) return emptyMap()
            val result = HashMap<Int, Int>()
            file.bufferedReader().use { reader ->
                reader.readLine() // skip header
                reader.lineSequence().forEach { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size < 8) return@forEach
                    val uid = cols[7].toIntOrNull() ?: return@forEach
                    val portHex = cols[1].substringAfter(':')
                    val port = portHex.toIntOrNull(16) ?: return@forEach
                    result[port] = uid
                }
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read $path: ${e.message}")
            emptyMap()
        }
    }
}
