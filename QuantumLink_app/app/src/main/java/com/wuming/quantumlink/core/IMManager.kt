package com.wuming.quantumlink.core

import android.content.Context
import android.util.Log
import com.wuming.quantumlink.data.remote.websocket.MessageEvent
import com.wuming.quantumlink.data.remote.websocket.WebSocketClient
import com.wuming.quantumlink.domain.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * IM 管理器 — 统一管理连接、消息收发
 */
class IMManager private constructor() {

    companion object {
        private const val TAG = "IMManager"

        @Volatile
        private var instance: IMManager? = null

        fun getInstance(): IMManager {
            return instance ?: synchronized(this) {
                instance ?: IMManager().also { instance = it }
            }
        }
    }

    private val wsClient: WebSocketClient get() = ServiceLocator.webSocket

    val isConnected: Boolean get() = wsClient.isConnected
    val messageEvents: Flow<MessageEvent> get() = wsClient.messages

    private var currentUser: String = ""
    private var authJob: kotlinx.coroutines.Job? = null
    private var connectScope: kotlinx.coroutines.CoroutineScope? = null

    fun connect(host: String, port: Int, userId: String) {
        // 避免重复连接同一个用户
        if (currentUser == userId && wsClient.isConnected) {
            Log.d(TAG, "已连接，跳过重复连接")
            return
        }
        // 先断开旧连接
        if (wsClient.isConnected) {
            wsClient.disconnect()
        }
        currentUser = userId
        Constants.Server.host = host
        Constants.Server.port = port

        // 取消旧的 AUTH 监听
        authJob?.cancel()
        connectScope?.cancel()
        connectScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        wsClient.connect(Constants.Server.wsUrl)
        Log.d(TAG, "IM 连接中: ${Constants.Server.wsUrl} user=$userId")

        // 监听 Connected 事件，连接成功后发送 AUTH 帧（仅一次）
        authJob = connectScope?.launch {
            messageEvents.collect { event ->
                if (event is MessageEvent.Connected) {
                    Log.d(TAG, "WS 已连接，发送 AUTH 帧")
                    val token = com.wuming.quantumlink.data.remote.api.ApiClient.token
                    if (token.isNotEmpty()) {
                        val authPayload = encodeAuthPacket(token)
                        wsClient.sendRaw(authPayload)
                    }
                }
            }
        }
    }

    /** 编码 AUTH 帧: [0x04][4字节长度BigEndian][token字节] */
    private fun encodeAuthPacket(token: String): String {
        val data = token.toByteArray(Charsets.UTF_8)
        val buf = java.nio.ByteBuffer.allocate(5 + data.size)
        buf.put(0x04.toByte())
        buf.putInt(data.size)
        buf.put(data)
        return android.util.Base64.encodeToString(buf.array(), android.util.Base64.NO_WRAP)
    }

    fun disconnect() {
        authJob?.cancel()
        connectScope?.cancel()
        wsClient.disconnect()
        Log.d(TAG, "IM 已断开")
    }

    fun sendMessage(message: Message) {
        wsClient.send(MessageEvent.SendMessage(message))
    }

    fun destroy() {
        disconnect()
        instance = null
    }
}
