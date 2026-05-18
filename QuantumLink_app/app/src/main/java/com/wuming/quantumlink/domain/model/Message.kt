package com.wuming.quantumlink.domain.model

/**
 * 消息类型
 */
enum class MessageType {
    TEXT,       // 文本消息
    IMAGE,      // 图片消息
    FILE,       // 文件消息
    SYSTEM,     // 系统消息
    FORUM_POST  // 论坛帖子
}

/**
 * 消息状态
 */
enum class MessageStatus {
    SENDING,    // 发送中
    SENT,       // 已发送
    DELIVERED,  // 已送达
    READ,       // 已读
    FAILED      // 发送失败
}

/**
 * 聊天消息 — 轻量级设计，减少对象开销
 */
data class Message(
    val id: Long = 0,
    val conversationId: Long = 0,
    val senderId: String = "",
    val senderName: String = "",
    val type: MessageType = MessageType.TEXT,
    val content: String = "",
    val status: MessageStatus = MessageStatus.SENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean = false
)
