package com.wuming.quantumlink.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wuming.quantumlink.domain.model.Conversation

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["last_message_time"])]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "avatar")
    val avatar: String = "",

    @ColumnInfo(name = "last_message")
    val lastMessage: String = "",

    @ColumnInfo(name = "last_message_time")
    val lastMessageTime: Long = 0,

    @ColumnInfo(name = "unread_count")
    val unreadCount: Int = 0,

    @ColumnInfo(name = "is_group")
    val isGroup: Boolean = false,

    @ColumnInfo(name = "member_count")
    val memberCount: Int = 0
) {
    fun toDomain(): Conversation = Conversation(
        id = id, name = name, avatar = avatar,
        lastMessage = lastMessage, lastMessageTime = lastMessageTime,
        unreadCount = unreadCount, isGroup = isGroup,
        memberCount = memberCount
    )

    companion object {
        fun fromDomain(c: Conversation): ConversationEntity = ConversationEntity(
            id = c.id, name = c.name, avatar = c.avatar,
            lastMessage = c.lastMessage, lastMessageTime = c.lastMessageTime,
            unreadCount = c.unreadCount, isGroup = c.isGroup,
            memberCount = c.memberCount
        )
    }
}
