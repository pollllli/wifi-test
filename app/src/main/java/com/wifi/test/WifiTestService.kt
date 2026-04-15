package com.wifi.test

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.wifi.test.data.TestData
import com.wifi.test.data.TestDatabase
import com.wifi.test.data.TestSummary
import com.wifi.test.utils.NetworkManager
import com.wifi.test.utils.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

class WifiTestService : Service() {
    private val CHANNEL_ID = "wifi_test_channel"
    private val NOTIFICATION_ID = 1

    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var wifiManager: WifiManager
    private lateinit var networkManager: NetworkManager
    private lateinit var testDatabase: TestDatabase

    private var testJob: Job? = null
    private var isTesting = false
    private var testStartTime: Long = 0
    private var totalSentPackets = 0
    private var totalReceivedPackets = 0
    private var totalLostPackets = 0
    private var totalReconnectCount = 0
    private var totalDisconnectDuration = 0L
    private var latencyList = ConcurrentLinkedQueue<Long>()
    private var signalStrengthList = ConcurrentLinkedQueue<Int>()

    private var testConfig: TestConfig? = null

    data class TestConfig(
        val targetIp: String,
        val targetPort: Int,
        val testDuration: Long,
        val packetInterval: Long,
        val packetSize: Int,
        val protocol: String
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        wifiManager = WifiManager(this)
        networkManager = NetworkManager()
        testDatabase = TestDatabase.getDatabase(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiTest:WakeLock")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val targetIp = intent.getStringExtra(EXTRA_TARGET_IP) ?: ""
                val targetPort = intent.getIntExtra(EXTRA_TARGET_PORT, 8080)
                val testDuration = intent.getLongExtra(EXTRA_TEST_DURATION, 600000)
                val packetInterval = intent.getLongExtra(EXTRA_PACKET_INTERVAL, 1000)
                val packetSize = intent.getIntExtra(EXTRA_PACKET_SIZE, 1024)
                val protocol = intent.getStringExtra(EXTRA_PROTOCOL) ?: "TCP"

                testConfig = TestConfig(targetIp, targetPort, testDuration, packetInterval, packetSize, protocol)
                startTest()
            }
            ACTION_STOP -> {
                stopTest()
            }
        }
        return START_STICKY
    }

    private fun startTest() {
        if (isTesting) return

        isTesting = true
        testStartTime = System.currentTimeMillis()
        totalSentPackets = 0
        totalReceivedPackets = 0
        totalLostPackets = 0
        totalReconnectCount = 0
        totalDisconnectDuration = 0L
        latencyList.clear()
        signalStrengthList.clear()

        wakeLock.acquire()
        startForeground(NOTIFICATION_ID, createNotification())

        testJob = CoroutineScope(Dispatchers.Default).launch {
            val endTime = testStartTime + testConfig!!.testDuration
            var lastPacketTime = 0L

            while (isTesting && System.currentTimeMillis() < endTime) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastPacketTime >= testConfig!!.packetInterval) {
                    sendPacket()
                    lastPacketTime = currentTime
                }

                // 每5分钟记录一次数据
                if (currentTime - testStartTime > 0 && (currentTime - testStartTime) % 300000 == 0L) {
                    recordTestData()
                }

                delay(100)
            }

            // 测试结束，记录最终数据
            recordTestData()
            generateTestSummary()
            stopSelf()
        }
    }

    private suspend fun sendPacket() {
        val data = ByteArray(testConfig!!.packetSize) { 0x41 } // 'A' byte
        val result = when (testConfig!!.protocol) {
            "TCP" -> networkManager.sendTcpPacket(testConfig!!.targetIp, testConfig!!.targetPort, data)
            "UDP" -> networkManager.sendUdpPacket(testConfig!!.targetIp, testConfig!!.targetPort, data)
            else -> networkManager.sendTcpPacket(testConfig!!.targetIp, testConfig!!.targetPort, data)
        }

        totalSentPackets++
        if (result.first) {
            totalReceivedPackets++
            latencyList.offer(result.second)
        } else {
            totalLostPackets++
            // 检查WiFi连接状态
            if (!wifiManager.isWifiConnected()) {
                val reconnectStartTime = System.currentTimeMillis()
                // 尝试重连
                // 这里可以添加重连逻辑
                totalReconnectCount++
                totalDisconnectDuration += System.currentTimeMillis() - reconnectStartTime
            }
        }

        signalStrengthList.offer(wifiManager.getSignalStrength())
    }

    private suspend fun recordTestData() {
        val currentTime = System.currentTimeMillis()
        val signalStrength = wifiManager.getSignalStrength()

        val testData = TestData(
            timestamp = currentTime,
            sentPackets = totalSentPackets,
            receivedPackets = totalReceivedPackets,
            lostPackets = totalLostPackets,
            averageLatency = if (latencyList.isNotEmpty()) latencyList.average() else 0.0,
            maxLatency = if (latencyList.isNotEmpty()) latencyList.maxOrNull()?.toDouble() ?: 0.0 else 0.0,
            minLatency = if (latencyList.isNotEmpty()) latencyList.minOrNull()?.toDouble() ?: 0.0 else 0.0,
            jitter = calculateJitter(),
            signalStrength = signalStrength,
            reconnectCount = totalReconnectCount,
            disconnectDuration = totalDisconnectDuration
        )

        withContext(Dispatchers.IO) {
            testDatabase.testDao().insertTestData(testData)
        }
    }

    private suspend fun generateTestSummary() {
        val endTime = System.currentTimeMillis()
        val totalDuration = endTime - testStartTime
        val packetLossRate = if (totalSentPackets > 0) (totalLostPackets.toDouble() / totalSentPackets) * 100 else 0.0
        val avgLatency = if (latencyList.isNotEmpty()) latencyList.average() else 0.0
        val maxLatency = if (latencyList.isNotEmpty()) latencyList.maxOrNull()?.toDouble() ?: 0.0 else 0.0
        val minLatency = if (latencyList.isNotEmpty()) latencyList.minOrNull()?.toDouble() ?: 0.0 else 0.0
        val avgJitter = calculateJitter()
        val avgSignalStrength = if (signalStrengthList.isNotEmpty()) signalStrengthList.average().toInt() else 0

        val testSummary = TestSummary(
            startTime = testStartTime,
            endTime = endTime,
            totalDuration = totalDuration,
            totalSentPackets = totalSentPackets,
            totalReceivedPackets = totalReceivedPackets,
            totalLostPackets = totalLostPackets,
            packetLossRate = packetLossRate,
            avgLatency = avgLatency,
            maxLatency = maxLatency,
            minLatency = minLatency,
            avgJitter = avgJitter,
            reconnectCount = totalReconnectCount,
            avgSignalStrength = avgSignalStrength
        )

        withContext(Dispatchers.IO) {
            testDatabase.testDao().insertTestSummary(testSummary)
        }
    }

    private fun calculateJitter(): Double {
        if (latencyList.size < 2) return 0.0

        val latencies = latencyList.toList()
        var jitterSum = 0.0
        for (i in 1 until latencies.size) {
            jitterSum += Math.abs(latencies[i] - latencies[i - 1])
        }
        return jitterSum / (latencies.size - 1)
    }

    private fun stopTest() {
        isTesting = false
        testJob?.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        networkManager.closeConnections()
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WiFi测试服务",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi测试中")
            .setContentText("正在测试WiFi连接稳定性")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    companion object {
        const val ACTION_START = "com.wifi.test.ACTION_START"
        const val ACTION_STOP = "com.wifi.test.ACTION_STOP"
        const val EXTRA_TARGET_IP = "target_ip"
        const val EXTRA_TARGET_PORT = "target_port"
        const val EXTRA_TEST_DURATION = "test_duration"
        const val EXTRA_PACKET_INTERVAL = "packet_interval"
        const val EXTRA_PACKET_SIZE = "packet_size"
        const val EXTRA_PROTOCOL = "protocol"
    }
}