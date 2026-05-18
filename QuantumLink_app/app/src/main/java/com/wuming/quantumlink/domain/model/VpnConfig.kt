package com.wuming.quantumlink.domain.model

/**
 * VPN 配置 — WireGuard 配置参数
 */
data class VpnConfig(
    val isEnabled: Boolean = false,
    val serverAddress: String = "",
    val serverPublicKey: String = "",
    val clientPrivateKey: String = "",
    val clientAddress: String = "",
    val dnsServers: String = "1.1.1.1, 8.8.8.8",
    val allowedApps: List<String> = emptyList(), // 空 = 全局代理
    val isConnected: Boolean = false,
    val trafficUp: Long = 0,    // 上传字节数
    val trafficDown: Long = 0,  // 下载字节数
    val latencyMs: Int = 0
)
