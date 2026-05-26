package com.savedata.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.savedata.app.App
import com.savedata.app.MainActivity
import com.savedata.app.R
import com.savedata.app.util.IpPacketUtils
import com.savedata.app.util.ProcNetUtils
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class SaveDataVpnService : VpnService() {

    companion object {
        const val TAG = "SaveDataVpnService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "savedata_vpn"
        const val ACTION_START = "com.savedata.app.START_VPN"
        const val ACTION_STOP = "com.savedata.app.STOP_VPN"

        @Volatile var isRunning = false
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val forwarder = PacketForwarder(this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val builder = Builder()
            .setSession("SaveData VPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")

        vpnInterface = builder.establish() ?: run {
            Log.e(TAG, "Failed to establish VPN interface")
            stopSelf()
            return
        }

        isRunning = true
        App.instance.repository.setVpnEnabled(true)
        Log.i(TAG, "VPN started")

        serviceScope.launch {
            runPacketLoop()
        }
    }

    private fun stopVpn() {
        isRunning = false
        App.instance.repository.setVpnEnabled(false)
        forwarder.closeAll()
        serviceScope.coroutineContext.cancelChildren()
        try { vpnInterface?.close() } catch (e: Exception) { }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN stopped")
    }

    private suspend fun runPacketLoop() {
        val vpnFd = vpnInterface ?: return
        val inputStream = FileInputStream(vpnFd.fileDescriptor)
        val outputStream = FileOutputStream(vpnFd.fileDescriptor)
        val packet = ByteArray(32767)

        val repository = App.instance.repository
        var blockedPackages = repository.getBlockedPackages().toSet()
        var lastRuleRefresh = System.currentTimeMillis()

        while (isRunning) {
            val len = withContext(Dispatchers.IO) {
                try { inputStream.read(packet) } catch (e: Exception) { -1 }
            }
            if (len <= 0) continue

            // Refresh blocked packages list every 3 seconds
            val now = System.currentTimeMillis()
            if (now - lastRuleRefresh > 3000) {
                blockedPackages = repository.getBlockedPackages().toSet()
                lastRuleRefresh = now
            }

            val ipHeader = IpPacketUtils.parseIpv4Header(packet) ?: continue
            val rawPacket = packet.copyOf(len)

            when (ipHeader.protocol) {
                IpPacketUtils.PROTO_TCP -> handleTcp(rawPacket, ipHeader, outputStream, blockedPackages)
                IpPacketUtils.PROTO_UDP -> handleUdp(rawPacket, ipHeader, outputStream, blockedPackages)
                else -> forwarder.forwardRaw(rawPacket, outputStream)
            }
        }
    }

    private suspend fun handleTcp(
        packet: ByteArray,
        ipHeader: IpPacketUtils.Ipv4Header,
        out: FileOutputStream,
        blockedPackages: Set<String>
    ) {
        val tcpHeader = IpPacketUtils.parseTcpHeader(packet, ipHeader.headerLen) ?: return

        if (tcpHeader.isSyn && !tcpHeader.isAck) {
            val uid = ProcNetUtils.getUidForTcp(tcpHeader.srcPort)
            val packageName = getPackageNameForUid(uid)
            if (packageName != null && blockedPackages.contains(packageName)) {
                val rst = IpPacketUtils.buildRstPacket(ipHeader, tcpHeader)
                withContext(Dispatchers.IO) {
                    try { out.write(rst) } catch (e: Exception) { }
                }
                return
            }
        }

        forwarder.handleTcpPacket(packet, ipHeader, tcpHeader, out)
    }

    private suspend fun handleUdp(
        packet: ByteArray,
        ipHeader: IpPacketUtils.Ipv4Header,
        out: FileOutputStream,
        blockedPackages: Set<String>
    ) {
        val udpHeader = IpPacketUtils.parseUdpHeader(packet, ipHeader.headerLen) ?: return
        val uid = ProcNetUtils.getUidForUdp(udpHeader.srcPort)
        val packageName = getPackageNameForUid(uid)

        if (packageName != null && blockedPackages.contains(packageName)) {
            val icmp = IpPacketUtils.buildIcmpUnreachable(ipHeader, packet)
            withContext(Dispatchers.IO) {
                try { out.write(icmp) } catch (e: Exception) { }
            }
            return
        }

        forwarder.handleUdpPacket(packet, ipHeader, udpHeader, out)
    }

    private fun getPackageNameForUid(uid: Int): String? {
        if (uid < 0) return null
        val packages = packageManager.getPackagesForUid(uid)
        return packages?.firstOrNull()
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SaveDataVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Save Data")
            .setContentText("VPN активен — контроль трафика включён")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Стоп", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Save Data VPN",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "VPN туннель активен"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }
}
