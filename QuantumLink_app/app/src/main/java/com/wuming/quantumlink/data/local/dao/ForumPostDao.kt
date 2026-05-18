package com.wuming.quantumlink.data.local.dao

import androidx.room.*
import com.wuming.quantumlink.data.local.entity.ForumPostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ForumPostDao {
    @Query("SELECT * FROM forum_posts ORDER BY is_pinned DESC, timestamp DESC")
    fun getAllPosts(): Flow<List<ForumPostEntity>>

    @Query("SELECT * FROM forum_posts WHERE id = :id")
    suspend fun getById(id: Long): ForumPostEntity?

    @Query("SELECT * FROM forum_posts WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<ForumPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: ForumPostEntity): Long

    @Update
    suspend fun update(post: ForumPostEntity)

    @Query("UPDATE forum_posts SET reply_count = reply_count + 1 WHERE id = :postId")
    suspend fun incrementReplyCount(postId: Long)

    @Query("UPDATE forum_posts SET like_count = like_count + 1 WHERE id = :postId")
    suspend fun incrementLikeCount(postId: Long)

    @Delete
    suspend fun delete(post: ForumPostEntity)
}
