package com.wifi.test.utils

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager as AndroidWifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

class WifiManager(private val context: Context) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as AndroidWifiManager
    private var lastScanTime = 0L
    private val scanResultsCache = CopyOnWriteArrayList<ScanResult>()
    private val SCAN_INTERVAL = 5000 // 5秒扫描间隔

    suspend fun scanWifiNetworks(): List<ScanResult> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime < SCAN_INTERVAL) {
            return@withContext scanResultsCache
        }

        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
        }
        wifiManager.startScan()
        val results = wifiManager.scanResults
        scanResultsCache.clear()
        scanResultsCache.addAll(results)
        lastScanTime = currentTime
        results
    }

    fun getCurrentWifiInfo() = wifiManager.connectionInfo

    fun connectToWifi(ssid: String, password: String): Boolean {
        val wifiConfig = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            preSharedKey = "\"$password\""
        }

        val networkId = wifiManager.addNetwork(wifiConfig)
        return if (networkId != -1) {
            wifiManager.disconnect()
            wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()
            true
        } else {
            false
        }
    }

    fun isWifiConnected(): Boolean {
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo.networkId != -1
    }

    fun getSignalStrength(): Int {
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo.rssi
    }

    fun getWifiSsid(): String? {
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid
        return if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid.substring(1, ssid.length - 1)
        } else {
            ssid
        }
    }
}