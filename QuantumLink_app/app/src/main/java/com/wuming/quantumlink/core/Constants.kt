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
    // Cloudflare: 填域名(如 91s.us.ci) → WSS/HTTPS
    // 局域网: 填 IP → WS/HTTP
    object Server {
        var host: String = "91s.us.ci"
        var port: Int = 443

        /** 判断是否为 IP 地址（不包含字母=IP） */
        private val isIp: Boolean get() = !host.any { it.isLetter() }

        val wsUrl get() = if (isIp) "ws://$host:$port/ws"
                          else "wss://ws1.$host/ws"
        val apiUrl get() = if (isIp) "http://$host:$port/api"
                          else "https://ws2.$host/api"
    }
}
