package com.wuming.quantumlink.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wuming.quantumlink.domain.model.Message
import com.wuming.quantumlink.domain.model.MessageStatus
import com.wuming.quantumlink.domain.model.MessageType

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversation_id", "timestamp"]),
        Index(value = ["sender_id"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "conversation_id")
    val conversationId: Long = 0,

    @ColumnInfo(name = "sender_id")
    val senderId: String = "",

    @ColumnInfo(name = "sender_name")
    val senderName: String = "",

    @ColumnInfo(name = "type")
    val type: Int = MessageType.TEXT.ordinal,

    @ColumnInfo(name = "content")
    val content: String = "",

    @ColumnInfo(name = "status")
    val status: Int = MessageStatus.SENDING.ordinal,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_mine")
    val isMine: Boolean = false
) {
    fun toDomain(): Message = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        type = MessageType.entries.getOrElse(type) { MessageType.TEXT },
        content = content,
        status = MessageStatus.entries.getOrElse(status) { MessageStatus.SENT },
        timestamp = timestamp,
        isMine = isMine
    )

    companion object {
        fun fromDomain(msg: Message): MessageEntity = MessageEntity(
            id = msg.id,
            conversationId = msg.conversationId,
            senderId = msg.senderId,
            senderName = msg.senderName,
            type = msg.type.ordinal,
            content = msg.content,
            status = msg.status.ordinal,
            timestamp = msg.timestamp,
            isMine = msg.isMine
        )
    }
}
