package com.wifi.test.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NetworkManager {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val tcpSockets = ConcurrentHashMap<String, Socket>()

    fun sendTcpPacket(ip: String, port: Int, data: ByteArray): Pair<Boolean, Long> {
        val startTime = System.currentTimeMillis()
        var success = false

        try {
            val key = "$ip:$port"
            val socket = tcpSockets.getOrPut(key) { 
                val newSocket = Socket()
                newSocket.soTimeout = 2000
                newSocket
            }
            
            if (!socket.isConnected) {
                socket.connect(java.net.InetSocketAddress(ip, port), 3000)
            }

            socket.outputStream.write(data)
            socket.outputStream.flush()
            
            // 读取响应
            val buffer = ByteArray(1024)
            val bytesRead = socket.inputStream.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                success = true
            }
        } catch (e: Exception) {
            val key = "$ip:$port"
            tcpSockets.remove(key)?.close()
            e.printStackTrace()
        }

        val endTime = System.currentTimeMillis()
        return Pair(success, endTime - startTime)
    }

    fun sendUdpPacket(ip: String, port: Int, data: ByteArray): Pair<Boolean, Long> {
        val startTime = System.currentTimeMillis()
        var success = false

        try {
            val socket = DatagramSocket()
            socket.soTimeout = 2000
            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(data, data.size, address, port)
            socket.send(packet)
            success = true
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val endTime = System.currentTimeMillis()
        return Pair(success, endTime - startTime)
    }

    fun sendHttpRequest(url: String): Pair<Boolean, Long> {
        val startTime = System.currentTimeMillis()
        var success = false

        try {
            val request = Request.Builder()
                .url(url)
                .build()

            val response: Response = okHttpClient.newCall(request).execute()
            success = response.isSuccessful
            response.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val endTime = System.currentTimeMillis()
        return Pair(success, endTime - startTime)
    }

    fun closeConnections() {
        tcpSockets.forEach { (_, socket) ->
            try {
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        tcpSockets.clear()
    }
}