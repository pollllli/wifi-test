package com.wifi.test

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wifi.test.utils.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var btnScanWifi: Button
    private lateinit var etTargetIp: EditText
    private lateinit var etTargetPort: EditText
    private lateinit var etTestDuration: EditText
    private lateinit var etPacketInterval: EditText
    private lateinit var etPacketSize: EditText
    private lateinit var rgProtocol: RadioGroup
    private lateinit var btnStartTest: Button
    private lateinit var lvWifiNetworks: ListView

    private var isTesting = false
    private val wifiNetworks = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = WifiManager(this)
        initViews()
        setupListeners()

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiNetworks)
        lvWifiNetworks.adapter = adapter
    }

    private fun initViews() {
        btnScanWifi = findViewById(R.id.btn_scan_wifi)
        etTargetIp = findViewById(R.id.et_target_ip)
        etTargetPort = findViewById(R.id.et_target_port)
        etTestDuration = findViewById(R.id.et_test_duration)
        etPacketInterval = findViewById(R.id.et_packet_interval)
        etPacketSize = findViewById(R.id.et_packet_size)
        rgProtocol = findViewById(R.id.rg_protocol)
        btnStartTest = findViewById(R.id.btn_start_test)
        lvWifiNetworks = findViewById(R.id.lv_wifi_networks)
    }

    private fun setupListeners() {
        btnScanWifi.setOnClickListener {
            scanWifiNetworks()
        }

        btnStartTest.setOnClickListener {
            if (!isTesting) {
                startTest()
            } else {
                stopTest()
            }
        }
    }

    private fun scanWifiNetworks() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val networks = wifiManager.scanWifiNetworks()
                wifiNetworks.clear()
                wifiNetworks.addAll(networks.map { it.SSID })
                adapter.notifyDataSetChanged()
                Toast.makeText(this@MainActivity, "扫描完成，发现 ${networks.size} 个WiFi网络", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "扫描失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startTest() {
        val targetIp = etTargetIp.text.toString()
        val targetPort = etTargetPort.text.toString().toIntOrNull() ?: 8080
        val testDuration = etTestDuration.text.toString().toLongOrNull() ?: 600000
        val packetInterval = etPacketInterval.text.toString().toLongOrNull() ?: 1000
        val packetSize = etPacketSize.text.toString().toIntOrNull() ?: 1024
        val selectedProtocolId = rgProtocol.checkedRadioButtonId
        val selectedProtocol = if (selectedProtocolId == R.id.rb_tcp) "TCP" else "UDP"

        val intent = Intent(this, WifiTestService::class.java).apply {
            action = WifiTestService.ACTION_START
            putExtra(WifiTestService.EXTRA_TARGET_IP, targetIp)
            putExtra(WifiTestService.EXTRA_TARGET_PORT, targetPort)
            putExtra(WifiTestService.EXTRA_TEST_DURATION, testDuration)
            putExtra(WifiTestService.EXTRA_PACKET_INTERVAL, packetInterval)
            putExtra(WifiTestService.EXTRA_PACKET_SIZE, packetSize)
            putExtra(WifiTestService.EXTRA_PROTOCOL, selectedProtocol)
        }

        startService(intent)
        isTesting = true
        btnStartTest.text = "停止测试"
        Toast.makeText(this, "测试已开始", Toast.LENGTH_SHORT).show()
    }

    private fun stopTest() {
        val intent = Intent(this, WifiTestService::class.java).apply {
            action = WifiTestService.ACTION_STOP
        }
        stopService(intent)
        isTesting = false
        btnStartTest.text = "开始测试"
        Toast.makeText(this, "测试已停止", Toast.LENGTH_SHORT).show()
    }
}