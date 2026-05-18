package com.wuming.quantumlink.data.repository

import com.wuming.quantumlink.data.local.dao.ForumPostDao
import com.wuming.quantumlink.data.local.entity.ForumPostEntity
import com.wuming.quantumlink.domain.model.ForumPost
import com.wuming.quantumlink.domain.repository.ForumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ForumRepositoryImpl(
    private val forumPostDao: ForumPostDao
) : ForumRepository {

    override fun getAllPosts(): Flow<List<ForumPost>> {
        return forumPostDao.getAllPosts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchPosts(query: String): Flow<List<ForumPost>> {
        return forumPostDao.search(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createPost(post: ForumPost): Long {
        return forumPostDao.insert(ForumPostEntity.fromDomain(post))
    }

    override suspend fun likePost(postId: Long) {
        forumPostDao.incrementLikeCount(postId)
    }
}
