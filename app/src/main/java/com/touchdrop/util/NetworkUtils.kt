package com.touchdrop.util

import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    /**
     * Yerel ağ IPv4 adresini öncelik sırasına göre bulur:
     * 1. wlan, ap, softap, swlan (Wi-Fi ve Hotspot)
     * 2. Diğer fiziksel IPv4 arayüzleri
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // 1. Öncelik: Wi-Fi veya Hotspot arayüzleri
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan")) {
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            // 2. İkinci öncelik: Hücresel ve sanal hariç diğer IPv4
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                if (!name.startsWith("rmnet") && !name.startsWith("dummy")) {
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            // 3. Son çare: Herhangi bir loopback olmayan IPv4
            for (intf in interfaces) {
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
