package com.savedata.app.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

object IpPacketUtils {

    const val PROTO_TCP = 6
    const val PROTO_UDP = 17
    const val PROTO_ICMP = 1

    data class Ipv4Header(
        val version: Int,
        val ihl: Int,
        val totalLength: Int,
        val protocol: Int,
        val srcIp: Int,
        val dstIp: Int,
        val headerLen: Int
    )

    data class TcpHeader(
        val srcPort: Int,
        val dstPort: Int,
        val seqNum: Long,
        val ackNum: Long,
        val dataOffset: Int,
        val flags: Int,
        val window: Int
    ) {
        val isSyn get() = (flags and 0x02) != 0
        val isAck get() = (flags and 0x10) != 0
        val isFin get() = (flags and 0x01) != 0
        val isRst get() = (flags and 0x04) != 0
    }

    data class UdpHeader(
        val srcPort: Int,
        val dstPort: Int,
        val length: Int
    )

    fun parseIpv4Header(packet: ByteArray): Ipv4Header? {
        if (packet.size < 20) return null
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        val versionIhl = buf.get().toInt() and 0xFF
        val version = versionIhl shr 4
        if (version != 4) return null
        val ihl = (versionIhl and 0x0F) * 4
        if (packet.size < ihl) return null
        buf.get()
        val totalLength = buf.short.toInt() and 0xFFFF
        buf.position(9)
        val protocol = buf.get().toInt() and 0xFF
        buf.position(12)
        val srcIp = buf.int
        val dstIp = buf.int
        return Ipv4Header(version, ihl, totalLength, protocol, srcIp, dstIp, ihl)
    }

    fun parseTcpHeader(packet: ByteArray, ipHeaderLen: Int): TcpHeader? {
        if (packet.size < ipHeaderLen + 20) return null
        val buf = ByteBuffer.wrap(packet, ipHeaderLen, packet.size - ipHeaderLen).order(ByteOrder.BIG_ENDIAN)
        val srcPort = buf.short.toInt() and 0xFFFF
        val dstPort = buf.short.toInt() and 0xFFFF
        val seqNum = buf.int.toLong() and 0xFFFFFFFFL
        val ackNum = buf.int.toLong() and 0xFFFFFFFFL
        val dataOffsetFlags = buf.short.toInt() and 0xFFFF
        val dataOffset = (dataOffsetFlags shr 12) * 4
        val flags = dataOffsetFlags and 0x1FF
        val window = buf.short.toInt() and 0xFFFF
        return TcpHeader(srcPort, dstPort, seqNum, ackNum, dataOffset, flags, window)
    }

    fun parseUdpHeader(packet: ByteArray, ipHeaderLen: Int): UdpHeader? {
        if (packet.size < ipHeaderLen + 8) return null
        val buf = ByteBuffer.wrap(packet, ipHeaderLen, packet.size - ipHeaderLen).order(ByteOrder.BIG_ENDIAN)
        val srcPort = buf.short.toInt() and 0xFFFF
        val dstPort = buf.short.toInt() and 0xFFFF
        val length = buf.short.toInt() and 0xFFFF
        return UdpHeader(srcPort, dstPort, length)
    }

    fun buildRstPacket(ipHeader: Ipv4Header, tcpHeader: TcpHeader): ByteArray {
        val totalLen = 40
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        // IPv4 header
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(totalLen.toShort())
        buf.putShort(0)
        buf.putShort(0x40.toShort())
        buf.put(64)
        buf.put(PROTO_TCP.toByte())
        buf.putShort(0)
        buf.putInt(ipHeader.dstIp)
        buf.putInt(ipHeader.srcIp)

        // TCP RST header
        buf.putShort(tcpHeader.dstPort.toShort())
        buf.putShort(tcpHeader.srcPort.toShort())
        buf.putInt((tcpHeader.ackNum).toInt())
        buf.putInt(0)
        buf.put(0x50.toByte())
        buf.put(0x14.toByte()) // RST+ACK
        buf.putShort(0)
        buf.putShort(0)
        buf.putShort(0)

        val bytes = buf.array()
        setIpChecksum(bytes)
        setTcpChecksum(bytes, 20)
        return bytes
    }

    fun buildIcmpUnreachable(ipHeader: Ipv4Header, originalPacket: ByteArray): ByteArray {
        val origBytes = minOf(8 + ipHeader.headerLen + 8, originalPacket.size)
        val totalLen = 20 + 8 + origBytes
        val buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(totalLen.toShort())
        buf.putShort(0)
        buf.putShort(0x40.toShort())
        buf.put(64)
        buf.put(PROTO_ICMP.toByte())
        buf.putShort(0)
        buf.putInt(ipHeader.dstIp)
        buf.putInt(ipHeader.srcIp)

        buf.put(3) // type: destination unreachable
        buf.put(3) // code: port unreachable
        buf.putShort(0)
        buf.putInt(0)
        buf.put(originalPacket, 0, origBytes)

        val bytes = buf.array()
        setIpChecksum(bytes)
        setIcmpChecksum(bytes, 20)
        return bytes
    }

    private fun setIpChecksum(packet: ByteArray) {
        packet[10] = 0
        packet[11] = 0
        val checksum = headerChecksum(packet, 0, 20)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = checksum.toByte()
    }

    private fun setTcpChecksum(packet: ByteArray, tcpOffset: Int) {
        val tcpLength = packet.size - tcpOffset
        packet[tcpOffset + 16] = 0
        packet[tcpOffset + 17] = 0

        var sum = 0
        // pseudo-header
        sum += ((packet[12].toInt() and 0xFF) shl 8) or (packet[13].toInt() and 0xFF)
        sum += ((packet[14].toInt() and 0xFF) shl 8) or (packet[15].toInt() and 0xFF)
        sum += ((packet[16].toInt() and 0xFF) shl 8) or (packet[17].toInt() and 0xFF)
        sum += ((packet[18].toInt() and 0xFF) shl 8) or (packet[19].toInt() and 0xFF)
        sum += PROTO_TCP
        sum += tcpLength
        // TCP header + data
        var i = tcpOffset
        while (i < packet.size - 1) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (packet.size % 2 != tcpOffset % 2) {
            sum += (packet[packet.size - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.inv() and 0xFFFF
        packet[tcpOffset + 16] = (checksum shr 8).toByte()
        packet[tcpOffset + 17] = checksum.toByte()
    }

    private fun setIcmpChecksum(packet: ByteArray, icmpOffset: Int) {
        packet[icmpOffset + 2] = 0
        packet[icmpOffset + 3] = 0
        val checksum = headerChecksum(packet, icmpOffset, packet.size - icmpOffset)
        packet[icmpOffset + 2] = (checksum shr 8).toByte()
        packet[icmpOffset + 3] = checksum.toByte()
    }

    private fun headerChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (length % 2 != 0) sum += (data[end - 1].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    fun intToIp(ip: Int): String {
        return "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"
    }
}
