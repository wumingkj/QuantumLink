package com.wuming.quantumlink.data.local.dao

import androidx.room.*
import com.wuming.quantumlink.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY last_message_time DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET last_message = :msg, last_message_time = :time WHERE id = :convId")
    suspend fun updateLastMessage(convId: Long, msg: String, time: Long)

    @Query("UPDATE conversations SET unread_count = unread_count + 1 WHERE id = :convId")
    suspend fun incrementUnread(convId: Long)

    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :convId")
    suspend fun clearUnread(convId: Long)

    @Delete
    suspend fun delete(conversation: ConversationEntity)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}
