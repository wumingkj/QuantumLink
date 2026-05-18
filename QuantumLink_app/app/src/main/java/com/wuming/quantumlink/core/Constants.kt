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
    // Cloudflare: ws1→WS ws2→REST | 局域网: 直接填同一个 IP
    object Server {
        var host: String = "91s.us.ci"
        var port: Int = 443
        val wsUrl get() = if (host.contains('.'))
            "wss://ws1.$host/ws" else "ws://$host:$port/ws"
        val apiUrl get() = if (host.contains('.'))
            "https://ws2.$host/api" else "http://$host:$port/api"
    }
}
