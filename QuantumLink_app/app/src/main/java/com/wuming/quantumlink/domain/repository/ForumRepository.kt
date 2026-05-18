package com.wuming.quantumlink.domain.repository

import com.wuming.quantumlink.domain.model.ForumPost
import kotlinx.coroutines.flow.Flow

interface ForumRepository {
    fun getAllPosts(): Flow<List<ForumPost>>
    fun searchPosts(query: String): Flow<List<ForumPost>>
    suspend fun createPost(post: ForumPost): Long
    suspend fun likePost(postId: Long)
}
