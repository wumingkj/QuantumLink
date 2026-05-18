package com.wuming.quantumlink.domain.repository

import com.wuming.quantumlink.domain.model.Conversation
import com.wuming.quantumlink.domain.model.Message
import com.wuming.quantumlink.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessages(conversationId: Long, limit: Int = 50, offset: Int = 0): Flow<List<Message>>
    suspend fun sendMessage(message: Message): Long
    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus)
    fun getConversations(): Flow<List<Conversation>>
    suspend fun createConversation(name: String, isGroup: Boolean): Long
    suspend fun clearUnread(conversationId: Long)
}
