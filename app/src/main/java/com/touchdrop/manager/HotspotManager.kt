package com.touchdrop.manager

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.touchdrop.service.TouchDropNfcService
import com.touchdrop.util.NetworkUtils

/**
 * Android Local-Only Hotspot (Hızlı Köprü) Yaşam Döngüsü Yöneticisi.
 * Dışarıda Wi-Fi ağı yokken telefonlar arasında geçici, şifreli bir yerel köprü ağı kurar.
 */
class HotspotManager(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    var isHotspotActive: Boolean = false
        private set

    var hotspotSsid: String? = null
        private set

    var hotspotPass: String? = null
        private set

    fun startBridge(
        onStarted: (ssid: String, pass: String, shareUrl: String) -> Unit,
        onFailed: (reason: Int) -> Unit,
        onStopped: () -> Unit
    ) {
        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    hotspotReservation = reservation
                    isHotspotActive = true

                    val config = reservation.softApConfiguration
                    val ssid = config.ssid ?: "TouchDrop"
                    val pass = config.passphrase ?: ""

                    hotspotSsid = ssid
                    hotspotPass = pass

                    val hotspotIp = NetworkUtils.getLocalIpAddress() ?: "192.168.43.1"
                    val activeShareUrl = "http://$hotspotIp:8080"

                    TouchDropNfcService.currentShareUrl = activeShareUrl
                    TouchDropNfcService.hotspotSsid = ssid
                    TouchDropNfcService.hotspotPass = pass
                    TouchDropNfcService.isHotspotActive = true
                    TouchDropNfcService.autoStep = 1

                    onStarted(ssid, pass, activeShareUrl)
                }

                override fun onStopped() {
                    stopBridge()
                    onStopped()
                }

                override fun onFailed(reason: Int) {
                    stopBridge()
                    onFailed(reason)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            e.printStackTrace()
            stopBridge()
            onFailed(-1)
        }
    }

    fun stopBridge() {
        try {
            hotspotReservation?.close()
            hotspotReservation = null
            isHotspotActive = false
            hotspotSsid = null
            hotspotPass = null

            TouchDropNfcService.hotspotSsid = null
            TouchDropNfcService.hotspotPass = null
            TouchDropNfcService.isHotspotActive = false
            TouchDropNfcService.autoStep = 1
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openHotspotSettings() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.android.settings", "com.android.settings.TetherSettings")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (ex: Exception) {
                // Fallback
            }
        }
    }
}
