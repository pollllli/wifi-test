package com.wifi.test.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_data")
data class TestData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val sentPackets: Int,
    val receivedPackets: Int,
    val lostPackets: Int,
    val averageLatency: Double,
    val maxLatency: Double,
    val minLatency: Double,
    val jitter: Double,
    val signalStrength: Int,
    val reconnectCount: Int,
    val disconnectDuration: Long
)

@Entity(tableName = "test_summary")
data class TestSummary(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val totalDuration: Long,
    val totalSentPackets: Int,
    val totalReceivedPackets: Int,
    val totalLostPackets: Int,
    val packetLossRate: Double,
    val avgLatency: Double,
    val maxLatency: Double,
    val minLatency: Double,
    val avgJitter: Double,
    val reconnectCount: Int,
    val avgSignalStrength: Int
)