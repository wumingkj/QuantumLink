package com.wuming.quantumlink.data.remote.websocket

import com.wuming.quantumlink.domain.model.Message
import com.wuming.quantumlink.domain.model.MessageStatus
import com.wuming.quantumlink.domain.model.MessageType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * 二进制消息协议 — 比 JSON/Protobuf 更轻量
 *
 * 协议格式（小端序）:
 * ┌─────────┬──────────┬──────────┬────────────┬──────────┐
 * │ type(1) │ flags(1) │ convId(8)│ timestamp(8)│ len(2)   │
 * ├─────────┴──────────┴──────────┴────────────┴──────────┤
 * │ content (len bytes, UTF-8)                            │
 * └───────────────────────────────────────────────────────┘
 *
 * type:    消息类型 (0=TEXT, 1=IMAGE, 2=FILE, 3=SYSTEM, 4=FORUM)
 * flags:   标志位 (bit0=isMine, bit1-7 预留)
 * convId:  会话ID (long, 8字节)
 * timestamp: 时间戳 (long, 8字节)
 * len:     内容长度 (ushort, 2字节, 最大65535)
 * content: UTF-8 编码的内容
 */
object BinaryMessageProtocol {

    private const val HEADER_SIZE = 20 // 1+1+8+8+2

    fun encode(msg: Message): ByteArray {
        val contentBytes = msg.content.toByteArray(StandardCharsets.UTF_8)
        val buf = ByteBuffer.allocate(HEADER_SIZE + contentBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)

        buf.put(msg.type.ordinal.toByte())
        val flags = if (msg.isMine) 0x01 else 0x00
        buf.put(flags.toByte())
        buf.putLong(msg.conversationId)
        buf.putLong(msg.timestamp)
        buf.putShort(contentBytes.size.toShort())
        buf.put(contentBytes)

        return buf.array()
    }

    fun decode(data: ByteArray): Message? {
        if (data.size < HEADER_SIZE) return null

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val typeOrdinal = buf.get().toInt() and 0xFF
        val flags = buf.get().toInt() and 0xFF
        val convId = buf.getLong()
        val timestamp = buf.getLong()
        val contentLen = buf.getShort().toInt() and 0xFFFF

        if (contentLen < 0 || data.size < HEADER_SIZE + contentLen) return null

        val contentBytes = ByteArray(contentLen)
        buf.get(contentBytes)
        val content = String(contentBytes, StandardCharsets.UTF_8)

        return Message(
            conversationId = convId,
            type = MessageType.entries.getOrElse(typeOrdinal) { MessageType.TEXT },
            content = content,
            status = MessageStatus.SENT,
            timestamp = timestamp,
            isMine = (flags and 0x01) != 0
        )
    }

    /** 心跳包 (1字节) */
    fun encodePing(): ByteArray = byteArrayOf(0xFF.toByte())

    /** 是否为心跳包 */
    fun isPing(data: ByteArray): Boolean =
        data.size == 1 && data[0] == 0xFF.toByte()

    // ==================== 密信协议扩展 ====================

    /** 服务端WS消息类型 */
    const val SERVER_MSG_SECRET_DESTROY = 0x0A
    const val SERVER_MSG_SECRET_EXPIRY_INFO = 0x0B
    const val CLIENT_MSG_EXPORT_REQUEST = 0x0C
    const val SERVER_MSG_EXPORT_RESPONSE = 0x0D

    /**
     * 解码密信销毁通知
     * Payload: [会话ID:36字节]
     * @return 会话ID
     */
    fun decodeSecretDestroy(data: ByteArray): String? {
        if (data.size < 37) return null
        // 跳过1字节type + 4字节length
        val offset = 5
        return if (data.size >= offset + 36) {
            String(data, offset, 36, StandardCharsets.UTF_8).trimEnd('\u0000')
        } else null
    }

    /**
     * 解码密信过期时间信息
     * Payload(服务端): [会话ID:36字节][到期时间戳:8字节BigEndian]
     * @return Pair(会话ID, 到期时间戳毫秒)
     */
    fun decodeSecretExpiryInfo(data: ByteArray): Pair<String, Long>? {
        if (data.size < 49) return null
        val offset = 5
        val convId = String(data, offset, 36, StandardCharsets.UTF_8).trimEnd('\u0000')
        val expiresAt = ByteBuffer.wrap(data, offset + 36, 8)
            .order(ByteOrder.BIG_ENDIAN).long
        return Pair(convId, expiresAt * 1000) // 秒→毫秒
    }
}
