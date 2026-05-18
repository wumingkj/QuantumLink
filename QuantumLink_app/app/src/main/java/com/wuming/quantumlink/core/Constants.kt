package com.wuming.quantumlink.core

/**
 * 应用全局常量
 */
object Constants {
    const val TAG = "量子飞信"

    // WebSocket
    const val WS_PING_INTERVAL = 15_000L

    // 数据库
    const val DB_NAME = "quantumlink.db"
    const val PAGE_SIZE = 50

    // 消息
    const val MAX_MESSAGE_LENGTH = 65535
    const val MESSAGE_RETRY_COUNT = 3

    // VPN (WireGuard)
    const val WG_TUN_MTU = 1280

    // 服务器 (默认值，可在设置中修改)
    object Server {
        var host: String = "192.168.1.100"
        var port: Int = 8082
        val wsUrl get() = "ws://$host:$port/ws"
        val apiUrl get() = "http://$host:$port/api"
    }
}
