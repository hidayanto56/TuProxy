package com.example.tuproxy.utils

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object IPUtils {
    /** Semua IPv4 non-loopback: Wi-Fi/LAN, Tailscale, hotspot, dll. */
    fun getAvailableIPv4Addresses(context: Context): List<String> {
        val ips = LinkedHashSet<String>()
        try {
            for (iface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                try {
                    if (!iface.isUp || iface.isLoopback) continue
                    for (addr in Collections.list(iface.inetAddresses)) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            addr.hostAddress?.let { ips.add("$it  (${iface.displayName})") }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        // Fallback Wi-Fi bila NetworkInterface terlewat di sebagian ROM.
        try {
            @Suppress("DEPRECATION")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ip = wm?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                val s = "%d.%d.%d.%d".format(ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
                if (ips.none { it.startsWith(s) }) ips.add("$s  (wlan)")
            }
        } catch (_: Exception) {
        }
        return ips.sorted().ifEmpty { listOf("0.0.0.0 (semua interface)") }
    }
}
