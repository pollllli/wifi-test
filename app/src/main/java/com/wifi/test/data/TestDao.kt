package com.wifi.test.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TestDao {
    @Insert
    fun insertTestData(testData: TestData)

    @Insert
    fun insertTestSummary(testSummary: TestSummary)

    @Query("SELECT * FROM test_data WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp")
    fun getTestDataByTimeRange(startTime: Long, endTime: Long): List<TestData>

    @Query("SELECT * FROM test_summary ORDER BY id DESC LIMIT 1")
    fun getLatestTestSummary(): TestSummary?

    @Query("DELETE FROM test_data")
    fun clearTestData()

    @Query("DELETE FROM test_summary")
    fun clearTestSummaries()
}