package com.savedata.app.util

import android.util.Log
import java.io.File

object ProcNetUtils {

    private const val TAG = "ProcNetUtils"

    fun getUidForTcp(srcPort: Int): Int {
        return getUidFromFile("/proc/net/tcp", srcPort)
            .takeIf { it >= 0 }
            ?: getUidFromFile("/proc/net/tcp6", srcPort)
    }

    fun getUidForUdp(srcPort: Int): Int {
        return getUidFromFile("/proc/net/udp", srcPort)
            .takeIf { it >= 0 }
            ?: getUidFromFile("/proc/net/udp6", srcPort)
    }

    private fun getUidFromFile(path: String, srcPort: Int): Int {
        return try {
            val file = File(path)
            if (!file.exists()) return -1
            file.bufferedReader().use { reader ->
                reader.readLine() // skip header
                reader.lineSequence().forEach { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size < 8) return@forEach
                    val localAddr = cols[1]
                    val uid = cols[7].toIntOrNull() ?: return@forEach
                    val portHex = localAddr.substringAfter(':')
                    val port = portHex.toIntOrNull(16) ?: return@forEach
                    if (port == srcPort) return uid
                }
                -1
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read $path: ${e.message}")
            -1
        }
    }
}
