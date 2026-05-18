package com.wuming.quantumlink.data.repository

import com.wuming.quantumlink.data.local.dao.ConversationDao
import com.wuming.quantumlink.data.local.dao.MessageDao
import com.wuming.quantumlink.data.local.entity.ConversationEntity
import com.wuming.quantumlink.data.local.entity.MessageEntity
import com.wuming.quantumlink.data.remote.websocket.WebSocketClient
import com.wuming.quantumlink.domain.model.Conversation
import com.wuming.quantumlink.domain.model.Message
import com.wuming.quantumlink.domain.model.MessageStatus
import com.wuming.quantumlink.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepositoryImpl(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val webSocket: WebSocketClient
) : MessageRepository {

    override fun getMessages(conversationId: Long, limit: Int, offset: Int): Flow<List<Message>> {
        return messageDao.getMessages(conversationId, limit, offset)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun sendMessage(message: Message): Long {
        val id = messageDao.insert(MessageEntity.fromDomain(message))
        val sentMsg = message.copy(id = id)
        webSocket.send(
            com.wuming.quantumlink.data.remote.websocket.MessageEvent.SendMessage(sentMsg)
        )
        return id
    }

    override suspend fun updateMessageStatus(messageId: Long, status: MessageStatus) {
        messageDao.updateStatus(messageId, status.ordinal)
    }

    override fun getConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations()
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun createConversation(name: String, isGroup: Boolean): Long {
        return conversationDao.insert(
            ConversationEntity(name = name, isGroup = isGroup)
        )
    }

    override suspend fun clearUnread(conversationId: Long) {
        conversationDao.clearUnread(conversationId)
    }
}
