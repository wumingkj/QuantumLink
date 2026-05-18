package com.wuming.quantumlink.domain.model

/**
 * 会话 — 轻量级数据类
 */
data class Conversation(
    val id: Long = 0,
    val name: String = "",
    val avatar: String = "",
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val memberCount: Int = 0
)
