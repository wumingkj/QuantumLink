package com.wuming.quantumlink.domain.model

/**
 * 密信会话 — 服务端计算时长，到期服务端发销毁通知
 *
 * 法律合规说明：
 * - 服务端始终保留完整会话记录（不可删除）
 * - 客户端收到销毁通知后仅清除本地界面显示
 * - 客户端可在到期前通过导出功能保存记录
 */
data class SecretChatSession(
    val id: String = "",
    val peerName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessageAt: Long = System.currentTimeMillis(),
    val lastMessage: String = "",
    val isActive: Boolean = true,

    /** 服务端提供的到期时间戳（毫秒），由 SECRET_EXPIRY_INFO 包更新 */
    val serverExpiresAt: Long = 0L,

    /** 是否已被服务端通知销毁 */
    val isDestroyed: Boolean = false,

    /** 销毁时间（服务端通知时记录） */
    val destroyedAt: Long = 0L
) {
    /** 距离超时还剩多少毫秒（由服务端时间决定，<=0 表示已过期） */
    val remainingMs: Long
        get() {
            if (isDestroyed) return 0L
            val baseTime = if (serverExpiresAt > 0) serverExpiresAt
                           else lastMessageAt + AUTO_DELETE_MS
            return baseTime - System.currentTimeMillis()
        }

    val isExpired: Boolean get() = remainingMs <= 0

    /** 用户可操作的剩余时间（秒），界面显示用 */
    val remainingSeconds: Int
        get() = (remainingMs / 1000).toInt().coerceAtLeast(0)

    companion object {
        /** 默认30分钟，实际以服务端下发的 expiresAt 为准 */
        const val AUTO_DELETE_MS = 30 * 60 * 1000L
    }
}
