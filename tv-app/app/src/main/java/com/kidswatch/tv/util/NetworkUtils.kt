package com.kidswatch.tv.util

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getDeviceIp(context: Context): String? {
        // Try WifiManager first
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ip = wifiInfo?.ipAddress ?: 0
            if (ip != 0) {
                return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
            }
        } catch (e: Exception) {
            AppLogger.warn("WifiManager failed: ${e.message}")
        }

        // Fallback: enumerate network interfaces
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                iface.inetAddresses?.toList()?.forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.warn("NetworkInterface failed: ${e.message}")
        }

        return null
    }

    fun buildConnectUrl(ip: String, port: Int = 8080): String {
        return "http://$ip:$port"
    }
}
