package com.wuming.quantumlink.data.remote.websocket

import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 高性能 WebSocket 客户端
 *
 * 性能特性：
 * - 使用专用 HandlerThread 处理网络事件，不阻塞主线程
 * - 自定义二进制协议(Base64编码传输)，减少带宽和解析开销
 * - 自动重连 + 指数退避
 * - 心跳保活 (15s 间隔)
 * - 消息发送队列，防止背压
 */
class WebSocketClient {

    companion object {
        private const val TAG = "WSClient"
        private const val PING_INTERVAL_MS = 15_000L
        private const val BASE_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val WRITE_TIMEOUT_MS = 5_000L
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private var webSocket: WebSocket? = null
    private var serverUrl: String = ""

    private val _isConnected = AtomicBoolean(false)
    val isConnected: Boolean get() = _isConnected.get()

    private val reconnectAttempt = AtomicInteger(0)
    private var reconnectJob: Job? = null

    private val _messages = MutableSharedFlow<MessageEvent>(extraBufferCapacity = 64)
    val messages: Flow<MessageEvent> = _messages.asSharedFlow()

    // 消息发送队列，确保顺序发送
    private val sendChannel = Channel<String>(Channel.BUFFERED)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val wsHandlerThread = HandlerThread("ws-handler").apply { start() }
    private val wsHandler = Handler(wsHandlerThread.looper)

    /**
     * 连接到服务器
     */
    fun connect(url: String) {
        serverUrl = url
        wsHandler.post { doConnect() }
        scope.launch { processSendQueue() }
    }

    private fun doConnect() {
        Log.d(TAG, "正在连接: $serverUrl")
        val request = Request.Builder()
            .url(serverUrl)
            .header("Connection", "Upgrade")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "连接成功")
                _isConnected.set(true)
                reconnectAttempt.set(0)
                _messages.tryEmit(MessageEvent.Connected)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                    val raw = try {
                        Base64.decode(text, Base64.NO_WRAP)
                    } catch (e: Exception) {
                        _messages.tryEmit(MessageEvent.TextMessage(text))
                        return
                    }
                    if (BinaryMessageProtocol.isPing(raw)) return

                    // 处理密信协议消息
                    if (raw.size > 0) {
                        when (raw[0].toInt() and 0xFF) {
                            BinaryMessageProtocol.SERVER_MSG_SECRET_DESTROY -> {
                                val convId = BinaryMessageProtocol.decodeSecretDestroy(raw)
                                if (convId != null) {
                                    _messages.tryEmit(MessageEvent.SecretDestroy(convId))
                                }
                                return
                            }
                            BinaryMessageProtocol.SERVER_MSG_SECRET_EXPIRY_INFO -> {
                                val info = BinaryMessageProtocol.decodeSecretExpiryInfo(raw)
                                if (info != null) {
                                    _messages.tryEmit(MessageEvent.SecretExpiryInfo(info.first, info.second))
                                }
                                return
                            }
                            BinaryMessageProtocol.SERVER_MSG_EXPORT_RESPONSE -> {
                                // payload 为导出数据的 JSON 字符串
                                val payload = if (raw.size > 5) {
                                    String(raw, 5, raw.size - 5, Charsets.UTF_8)
                                } else ""
                                _messages.tryEmit(MessageEvent.ExportResponse(payload))
                                return
                            }
                        }
                    }

                    val msg = BinaryMessageProtocol.decode(raw)
                    if (msg != null) {
                        _messages.tryEmit(MessageEvent.MessageReceived(msg))
                    }
                }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "连接关闭中: code=$code reason=$reason")
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "连接已关闭: code=$code")
                _isConnected.set(false)
                _messages.tryEmit(MessageEvent.Disconnected)
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "连接失败: ${t.message}")
                Log.e(TAG, "URL: $serverUrl")
                Log.e(TAG, "Response: ${response?.code} ${response?.message}")
                if (response != null) {
                    Log.e(TAG, "Headers: ${response.headers}")
                }
                _isConnected.set(false)
                val errMsg = if (response != null) "HTTP ${response.code}" else t.localizedMessage ?: "未知错误"
                _messages.tryEmit(MessageEvent.Error(errMsg))
                scheduleReconnect()
            }
        })
    }

    /**
     * 发送消息 — 异步，不阻塞调用线程
     */
    fun send(msg: MessageEvent.SendMessage) {
        scope.launch {
            val encoded = Base64.encodeToString(
                BinaryMessageProtocol.encode(msg.message),
                Base64.NO_WRAP
            )
            sendChannel.send(encoded)
        }
    }

    fun sendText(text: String) {
        scope.launch {
            sendChannel.send(text)
        }
    }

    /** 发送原始 Base64 编码数据（用于 AUTH 帧等非 Message 类型） */
    fun sendRaw(base64Data: String) {
        scope.launch {
            sendChannel.send(base64Data)
        }
    }

    /**
     * 发送队列处理器 — 保证消息按序发送
     */
    private suspend fun processSendQueue() {
        for (data in sendChannel) {
            val ws = webSocket ?: continue
            val success = withContext(Dispatchers.IO) {
                try {
                    ws.send(data)
                } catch (e: Exception) {
                    Log.e(TAG, "发送失败", e)
                    false
                }
            }
            if (!success) {
                _messages.tryEmit(MessageEvent.SendFailed)
            }
        }
    }

    /**
     * 指数退避重连
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val attempt = reconnectAttempt.getAndIncrement()
        val delay = (BASE_RECONNECT_DELAY_MS shl attempt.coerceAtMost(5))
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)

        Log.d(TAG, "计划重连: 第${attempt + 1}次, ${delay}ms后")
        reconnectJob = scope.launch {
            delay(delay)
            wsHandler.post { doConnect() }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "用户断开")
        webSocket = null
        _isConnected.set(false)
    }

    fun destroy() {
        disconnect()
        sendChannel.close()
        scope.cancel()
        wsHandlerThread.quitSafely()
    }
}

/**
 * WebSocket 事件
 */
sealed class MessageEvent {
    data object Connected : MessageEvent()
    data object Disconnected : MessageEvent()
    data class MessageReceived(val message: com.wuming.quantumlink.domain.model.Message) : MessageEvent()
    data class TextMessage(val text: String) : MessageEvent()
    data class SendMessage(val message: com.wuming.quantumlink.domain.model.Message) : MessageEvent()
    data object SendFailed : MessageEvent()
    data class Error(val message: String) : MessageEvent()

    // ===== 密信事件 =====
    /** 服务端通知：密信会话已销毁（客户端清除界面显示） */
    data class SecretDestroy(val conversationId: String) : MessageEvent()

    /** 服务端通知：密信过期时间信息 */
    data class SecretExpiryInfo(val conversationId: String, val expiresAt: Long) : MessageEvent()

    /** 服务端回复：导出数据 */
    data class ExportResponse(val data: String) : MessageEvent()
}
