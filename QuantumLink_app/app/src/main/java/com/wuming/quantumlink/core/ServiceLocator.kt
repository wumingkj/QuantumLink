package com.wuming.quantumlink.core

import android.content.Context
import com.wuming.quantumlink.data.local.AppDatabase
import com.wuming.quantumlink.data.remote.websocket.WebSocketClient
import com.wuming.quantumlink.data.repository.ContactRepositoryImpl
import com.wuming.quantumlink.data.repository.ForumRepositoryImpl
import com.wuming.quantumlink.data.repository.MessageRepositoryImpl
import com.wuming.quantumlink.domain.repository.ContactRepository
import com.wuming.quantumlink.domain.repository.ForumRepository
import com.wuming.quantumlink.domain.repository.MessageRepository

/**
 * 轻量级服务定位器 — 替代 Hilt/Dagger，减少编译开销和包体积
 */
object ServiceLocator {

    private var db: AppDatabase? = null
    private var _webSocket: WebSocketClient? = null

    val webSocket: WebSocketClient get() {
        return _webSocket ?: synchronized(this) {
            _webSocket ?: WebSocketClient().also { _webSocket = it }
        }
    }

    fun getMessageRepository(context: Context): MessageRepository {
        val database = getDatabase(context)
        return MessageRepositoryImpl(
            messageDao = database.messageDao(),
            conversationDao = database.conversationDao(),
            webSocket = webSocket
        )
    }

    fun getContactRepository(context: Context): ContactRepository {
        return ContactRepositoryImpl(
            contactDao = getDatabase(context).contactDao()
        )
    }

    fun getForumRepository(context: Context): ForumRepository {
        return ForumRepositoryImpl(
            forumPostDao = getDatabase(context).forumPostDao()
        )
    }

    private fun getDatabase(context: Context): AppDatabase {
        return db ?: synchronized(this) {
            db ?: AppDatabase.getInstance(context).also { db = it }
        }
    }

    fun destroy() {
        _webSocket?.destroy()
        _webSocket = null
        db = null
    }
}
