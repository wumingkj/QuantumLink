package com.wuming.quantumlink.manager

import android.util.Log
import com.wuming.quantumlink.domain.model.SecretChatSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 密信销毁管理器
 *
 * 职责：
 * 1. 监听服务端的 SECRET_DESTROY 通知
 * 2. 维护密信会话的销毁状态
 * 3. 提供导出功能入口
 *
 * 法律合规说明：
 * - 服务端保留所有记录，客户端不删除本地数据
 * - 销毁仅指客户端界面上的"灰显/锁定"状态
 * - 用户可在销毁前导出记录
 */
class SecretDestroyManager {

    companion object {
        private const val TAG = "SecretDestroyMgr"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** 已被通知销毁的会话ID集合 */
    private val _destroyedSessions = MutableStateFlow<Set<String>>(emptySet())
    val destroyedSessions: StateFlow<Set<String>> = _destroyedSessions.asStateFlow()

    /** 会话到期时间映射 convId → expiresAt */
    private val _expiryMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val expiryMap: StateFlow<Map<String, Long>> = _expiryMap.asStateFlow()

    /**
     * 处理服务端的销毁通知
     * @param conversationId 被销毁的密信会话ID
     * @return 是否需要通知UI更新
     */
    fun handleDestroyNotification(conversationId: String): Boolean {
        val current = _destroyedSessions.value.toMutableSet()
        val added = current.add(conversationId)
        if (added) {
            _destroyedSessions.value = current
            Log.d(TAG, "收到销毁通知: $conversationId")
        }
        return added
    }

    /**
     * 更新服务端下发的过期时间
     */
    fun updateExpiryInfo(conversationId: String, expiresAt: Long) {
        val current = _expiryMap.value.toMutableMap()
        current[conversationId] = expiresAt
        _expiryMap.value = current
        Log.d(TAG, "更新过期时间: $conversationId → $expiresAt")
    }

    /**
     * 检查会话是否已被销毁
     */
    fun isDestroyed(conversationId: String): Boolean {
        return _destroyedSessions.value.contains(conversationId)
    }

    /**
     * 获取会话剩余毫秒数（考虑服务端下发的到期时间）
     */
    fun getRemainingMs(session: SecretChatSession): Long {
        if (session.isDestroyed || isDestroyed(session.id)) return 0L
        val expiresAt = _expiryMap.value[session.id]
            ?: session.serverExpiresAt
            ?: (session.lastMessageAt + SecretChatSession.AUTO_DELETE_MS)
        return expiresAt - System.currentTimeMillis()
    }

    /**
     * 销毁资源
     */
    fun destroy() {
        scope.cancel()
        _destroyedSessions.value = emptySet()
        _expiryMap.value = emptyMap()
    }
}
