package com.wifi.test

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.wifi.test.data.TestDatabase
import com.wifi.test.data.TestSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private lateinit var tvTestSummary: TextView
    private lateinit var lineChartLatency: LineChart
    private lateinit var lineChartPacketLoss: LineChart
    private lateinit var lineChartSignalStrength: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        initViews()
        loadTestData()
    }

    private fun initViews() {
        tvTestSummary = findViewById(R.id.tv_test_summary)
        lineChartLatency = findViewById(R.id.line_chart_latency)
        lineChartPacketLoss = findViewById(R.id.line_chart_packet_loss)
        lineChartSignalStrength = findViewById(R.id.line_chart_signal_strength)
    }

    private fun loadTestData() {
        val testDatabase = TestDatabase.getDatabase(this)
        CoroutineScope(Dispatchers.Main).launch {
            val summary = testDatabase.testDao().getLatestTestSummary()
            summary?.let {
                displayTestSummary(it)
                // 这里可以添加图表数据的加载
            } ?: run {
                tvTestSummary.text = "暂无测试数据"
            }
        }
    }

    private fun displayTestSummary(summary: TestSummary) {
        val sb = StringBuilder()
        sb.append("测试摘要\n")
        sb.append("开始时间: ${formatTimestamp(summary.startTime)}\n")
        sb.append("结束时间: ${formatTimestamp(summary.endTime)}\n")
        sb.append("测试时长: ${formatDuration(summary.totalDuration)}\n")
        sb.append("总发送包数: ${summary.totalSentPackets}\n")
        sb.append("总接收包数: ${summary.totalReceivedPackets}\n")
        sb.append("总丢包数: ${summary.totalLostPackets}\n")
        sb.append("丢包率: ${String.format("%.2f%%", summary.packetLossRate)}\n")
        sb.append("平均延迟: ${String.format("%.2fms", summary.avgLatency)}\n")
        sb.append("最大延迟: ${String.format("%.2fms", summary.maxLatency)}\n")
        sb.append("最小延迟: ${String.format("%.2fms", summary.minLatency)}\n")
        sb.append("平均抖动: ${String.format("%.2fms", summary.avgJitter)}\n")
        sb.append("重连次数: ${summary.reconnectCount}\n")
        sb.append("平均信号强度: ${summary.avgSignalStrength}dBm\n")

        tvTestSummary.text = sb.toString()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun formatDuration(duration: Long): String {
        val hours = duration / 3600000
        val minutes = (duration % 3600000) / 60000
        val seconds = (duration % 60000) / 1000
        return "${hours}小时 ${minutes}分钟 ${seconds}秒"
    }
}