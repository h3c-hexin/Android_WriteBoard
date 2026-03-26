package com.h3c.writeboard.data.common

import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

object NetworkUtils {

    /** 获取设备局域网 IPv4 地址，未连接时返回 null */
    fun getLocalIp(): String? = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { !it.isLoopback && it.isUp }
            ?.flatMap { it.inetAddresses.toList() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (_: Exception) { null }

    /** 生成随机 hex token */
    fun generateToken(byteLength: Int = 8): String {
        val bytes = ByteArray(byteLength).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
