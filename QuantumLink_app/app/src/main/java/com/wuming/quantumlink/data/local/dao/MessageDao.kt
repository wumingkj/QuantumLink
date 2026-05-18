package com.wuming.quantumlink.data.local.dao

import androidx.room.*
import com.wuming.quantumlink.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("""
        SELECT * FROM messages 
        WHERE conversation_id = :convId 
        ORDER BY timestamp DESC 
        LIMIT :limit OFFSET :offset
    """)
    fun getMessages(convId: Long, limit: Int = 50, offset: Int = 0): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :convId AND is_mine = 0 AND status = :status")
    suspend fun getUnreadCount(convId: Long, status: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(messages: List<MessageEntity>)

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :msgId")
    suspend fun updateStatus(msgId: Long, status: Int)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversation_id = :convId")
    suspend fun deleteConversationMessages(convId: Long)
}
