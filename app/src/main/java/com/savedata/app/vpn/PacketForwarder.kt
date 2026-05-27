package com.savedata.app.vpn

import android.net.VpnService
import android.util.Log
import com.savedata.app.util.IpPacketUtils
import kotlinx.coroutines.*
import java.io.FileOutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

data class TcpKey(
    val srcIp: Int, val srcPort: Int,
    val dstIp: Int, val dstPort: Int
)

class TcpSession(
    val channel: SocketChannel,
    var localSeq: Long,
    var remoteSeq: Long,
    var lastSeen: Long = System.currentTimeMillis()
)

class PacketForwarder(private val vpnService: VpnService) {

    private val tcpSessions = ConcurrentHashMap<TcpKey, TcpSession>()
    private val udpChannels = ConcurrentHashMap<TcpKey, DatagramChannel>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handleTcpPacket(
        packet: ByteArray,
        ipHeader: IpPacketUtils.Ipv4Header,
        tcpHeader: IpPacketUtils.TcpHeader,
        out: FileOutputStream
    ) {
        val key = TcpKey(ipHeader.srcIp, tcpHeader.srcPort, ipHeader.dstIp, tcpHeader.dstPort)

        if (tcpHeader.isSyn && !tcpHeader.isAck) {
            scope.launch {
                establishTcpSession(key, ipHeader, tcpHeader, packet, out)
            }
            return
        }

        val session = tcpSessions[key] ?: return
        session.lastSeen = System.currentTimeMillis()

        if (tcpHeader.isFin || tcpHeader.isRst) {
            closeTcpSession(key)
            return
        }

        val dataOffset = ipHeader.headerLen + tcpHeader.dataOffset
        if (dataOffset < packet.size) {
            val data = packet.copyOfRange(dataOffset, packet.size)
            scope.launch {
                try {
                    val buf = ByteBuffer.wrap(data)
                    while (buf.hasRemaining()) {
                        session.channel.write(buf)
                    }
                } catch (e: Exception) {
                    closeTcpSession(key)
                }
            }
        }
    }

    private suspend fun establishTcpSession(
        key: TcpKey,
        ipHeader: IpPacketUtils.Ipv4Header,
        tcpHeader: IpPacketUtils.TcpHeader,
        originalPacket: ByteArray,
        out: FileOutputStream
    ) {
        try {
            val channel = SocketChannel.open()
            channel.configureBlocking(false)
            vpnService.protect(channel.socket())

            val dstIp = IpPacketUtils.intToIp(ipHeader.dstIp)
            val dstAddr = InetSocketAddress(InetAddress.getByName(dstIp), tcpHeader.dstPort)
            channel.connect(dstAddr)

            var attempts = 0
            while (!channel.finishConnect() && attempts++ < 50) {
                delay(20)
            }
            if (!channel.isConnected) {
                channel.close()
                return
            }

            val localSeq = (Math.random() * Int.MAX_VALUE).toLong()
            val session = TcpSession(channel, localSeq, tcpHeader.seqNum + 1)
            tcpSessions[key] = session

            val synAck = buildSynAck(ipHeader, tcpHeader, localSeq)
            withContext(Dispatchers.IO) {
                try { out.write(synAck) } catch (e: Exception) { }
            }

            scope.launch { readFromSocket(key, session, ipHeader, tcpHeader, out) }
        } catch (e: Exception) {
            Log.w(SaveDataVpnService.TAG, "TCP session failed: ${e.message}")
        }
    }

    private suspend fun readFromSocket(
        key: TcpKey,
        session: TcpSession,
        ipHeader: IpPacketUtils.Ipv4Header,
        tcpHeader: IpPacketUtils.TcpHeader,
        out: FileOutputStream
    ) {
        // Use blocking mode so we don't busy-spin when no data is available
        try { session.channel.configureBlocking(true) } catch (_: Exception) { }
        val buf = ByteBuffer.allocate(16384)
        try {
            while (tcpSessions.containsKey(key)) {
                buf.clear()
                val read = withContext(Dispatchers.IO) {
                    try { session.channel.read(buf) } catch (e: Exception) { -1 }
                }
                if (read < 0) break
                if (read > 0) {
                    buf.flip()
                    val data = ByteArray(buf.limit())
                    buf.get(data)
                    val responsePacket = buildTcpData(ipHeader, tcpHeader, session, data)
                    session.localSeq = (session.localSeq + data.size) and 0xFFFFFFFFL
                    withContext(Dispatchers.IO) {
                        try { out.write(responsePacket) } catch (e: Exception) { }
                    }
                }
                // no delay() needed — blocking read parks the coroutine until data arrives
            }
        } catch (e: Exception) {
            Log.w(SaveDataVpnService.TAG, "Read from socket error: ${e.message}")
        } finally {
            closeTcpSession(key)
        }
    }

    fun handleUdpPacket(
        packet: ByteArray,
        ipHeader: IpPacketUtils.Ipv4Header,
        udpHeader: IpPacketUtils.UdpHeader,
        out: FileOutputStream
    ) {
        val key = TcpKey(ipHeader.srcIp, udpHeader.srcPort, ipHeader.dstIp, udpHeader.dstPort)
        val dataOffset = ipHeader.headerLen + 8
        if (dataOffset >= packet.size) return
        val data = packet.copyOfRange(dataOffset, packet.size)

        scope.launch {
            try {
                val channel = udpChannels.getOrPut(key) {
                    val ch = DatagramChannel.open()
                    ch.configureBlocking(false)
                    vpnService.protect(ch.socket())
                    ch
                }
                val dstIp = IpPacketUtils.intToIp(ipHeader.dstIp)
                val dst = InetSocketAddress(InetAddress.getByName(dstIp), udpHeader.dstPort)
                channel.send(ByteBuffer.wrap(data), dst)

                // Switch to blocking mode to receive the response without busy-polling
                channel.configureBlocking(true)
                channel.socket().soTimeout = 2000
                val recvBuf = ByteBuffer.allocate(16384)
                recvBuf.clear()
                val from = withContext(Dispatchers.IO) {
                    try { channel.receive(recvBuf) } catch (_: Exception) { null }
                }
                channel.configureBlocking(false)
                if (from != null) {
                    recvBuf.flip()
                    val responseData = ByteArray(recvBuf.limit())
                    recvBuf.get(responseData)
                    val responsePacket = buildUdpResponse(ipHeader, udpHeader, responseData)
                    withContext(Dispatchers.IO) {
                        try { out.write(responsePacket) } catch (e: Exception) { }
                    }
                }
            } catch (e: Exception) {
                Log.w(SaveDataVpnService.TAG, "UDP forward error: ${e.message}")
            }
        }
    }

    fun forwardRaw(packet: ByteArray, out: FileOutputStream) {
        scope.launch(Dispatchers.IO) {
            try { out.write(packet) } catch (e: Exception) { }
        }
    }

    fun closeAll() {
        tcpSessions.values.forEach { try { it.channel.close() } catch (e: Exception) { } }
        tcpSessions.clear()
        udpChannels.values.forEach { try { it.close() } catch (e: Exception) { } }
        udpChannels.clear()
        scope.coroutineContext.cancelChildren()
    }

    private fun closeTcpSession(key: TcpKey) {
        val session = tcpSessions.remove(key)
        try { session?.channel?.close() } catch (e: Exception) { }
    }

    private fun buildSynAck(
        ipHeader: IpPacketUtils.Ipv4Header,
        tcpHeader: IpPacketUtils.TcpHeader,
        serverSeq: Long
    ): ByteArray {
        val totalLen = 40
        val buf = ByteBuffer.allocate(totalLen)
        buf.put(0x45.toByte()); buf.put(0)
        buf.putShort(totalLen.toShort())
        buf.putShort(0); buf.putShort(0x40.toShort())
        buf.put(64); buf.put(IpPacketUtils.PROTO_TCP.toByte())
        buf.putShort(0)
        buf.putInt(ipHeader.dstIp); buf.putInt(ipHeader.srcIp)
        buf.putShort(tcpHeader.dstPort.toShort())
        buf.putShort(tcpHeader.srcPort.toShort())
        buf.putInt(serverSeq.toInt())
        buf.putInt((tcpHeader.seqNum + 1).toInt())
        buf.put(0x50.toByte()); buf.put(0x12.toByte()) // SYN+ACK
        buf.putShort(65535.toShort())
        buf.putShort(0); buf.putShort(0)
        val bytes = buf.array()
        patchChecksums(bytes)
        return bytes
    }

    private fun buildTcpData(
        origIpHeader: IpPacketUtils.Ipv4Header,
        origTcpHeader: IpPacketUtils.TcpHeader,
        session: TcpSession,
        data: ByteArray
    ): ByteArray {
        val ipLen = 20; val tcpLen = 20; val total = ipLen + tcpLen + data.size
        val buf = ByteBuffer.allocate(total)
        buf.put(0x45.toByte()); buf.put(0)
        buf.putShort(total.toShort())
        buf.putShort(0); buf.putShort(0x40.toShort())
        buf.put(64); buf.put(IpPacketUtils.PROTO_TCP.toByte())
        buf.putShort(0)
        buf.putInt(origIpHeader.dstIp); buf.putInt(origIpHeader.srcIp)
        buf.putShort(origTcpHeader.dstPort.toShort())
        buf.putShort(origTcpHeader.srcPort.toShort())
        buf.putInt(session.localSeq.toInt())
        buf.putInt(session.remoteSeq.toInt())
        buf.put(0x50.toByte()); buf.put(0x18.toByte()) // PSH+ACK
        buf.putShort(65535.toShort())
        buf.putShort(0); buf.putShort(0)
        buf.put(data)
        val bytes = buf.array()
        patchChecksums(bytes)
        return bytes
    }

    private fun buildUdpResponse(
        origIpHeader: IpPacketUtils.Ipv4Header,
        origUdpHeader: IpPacketUtils.UdpHeader,
        data: ByteArray
    ): ByteArray {
        val total = 20 + 8 + data.size
        val buf = ByteBuffer.allocate(total)
        buf.put(0x45.toByte()); buf.put(0)
        buf.putShort(total.toShort())
        buf.putShort(0); buf.putShort(0x40.toShort())
        buf.put(64); buf.put(IpPacketUtils.PROTO_UDP.toByte())
        buf.putShort(0)
        buf.putInt(origIpHeader.dstIp); buf.putInt(origIpHeader.srcIp)
        buf.putShort(origUdpHeader.dstPort.toShort())
        buf.putShort(origUdpHeader.srcPort.toShort())
        buf.putShort((8 + data.size).toShort())
        buf.putShort(0)
        buf.put(data)
        val bytes = buf.array()
        patchChecksums(bytes)
        return bytes
    }

    private fun patchChecksums(packet: ByteArray) {
        if (packet.size < 20) return
        packet[10] = 0; packet[11] = 0
        val ipCsum = headerChecksum(packet, 0, 20)
        packet[10] = (ipCsum shr 8).toByte(); packet[11] = ipCsum.toByte()
        val proto = packet[9].toInt() and 0xFF
        val ipHdrLen = (packet[0].toInt() and 0x0F) * 4
        val tcpUdpLen = packet.size - ipHdrLen
        if (proto == IpPacketUtils.PROTO_TCP && packet.size >= ipHdrLen + 20) {
            packet[ipHdrLen + 16] = 0; packet[ipHdrLen + 17] = 0
            val csum = tcpUdpChecksum(packet, ipHdrLen, tcpUdpLen, proto)
            packet[ipHdrLen + 16] = (csum shr 8).toByte(); packet[ipHdrLen + 17] = csum.toByte()
        } else if (proto == IpPacketUtils.PROTO_UDP && packet.size >= ipHdrLen + 8) {
            packet[ipHdrLen + 6] = 0; packet[ipHdrLen + 7] = 0
            val csum = tcpUdpChecksum(packet, ipHdrLen, tcpUdpLen, proto)
            packet[ipHdrLen + 6] = (csum shr 8).toByte(); packet[ipHdrLen + 7] = csum.toByte()
        }
    }

    private fun headerChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0; var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF); i += 2
        }
        if (length % 2 != 0) sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun tcpUdpChecksum(packet: ByteArray, tcpOffset: Int, tcpLen: Int, proto: Int): Int {
        var sum = 0
        sum += ((packet[12].toInt() and 0xFF) shl 8) or (packet[13].toInt() and 0xFF)
        sum += ((packet[14].toInt() and 0xFF) shl 8) or (packet[15].toInt() and 0xFF)
        sum += ((packet[16].toInt() and 0xFF) shl 8) or (packet[17].toInt() and 0xFF)
        sum += ((packet[18].toInt() and 0xFF) shl 8) or (packet[19].toInt() and 0xFF)
        sum += proto; sum += tcpLen
        var i = tcpOffset
        while (i < tcpOffset + tcpLen - 1) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF); i += 2
        }
        if (tcpLen % 2 != 0) sum += (packet[tcpOffset + tcpLen - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
