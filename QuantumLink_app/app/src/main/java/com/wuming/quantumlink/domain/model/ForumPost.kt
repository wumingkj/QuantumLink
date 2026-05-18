package com.wuming.quantumlink.domain.model

/**
 * 论坛帖子
 */
data class ForumPost(
    val id: Long = 0,
    val title: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val replyCount: Int = 0,
    val likeCount: Int = 0,
    val isPinned: Boolean = false
)

data class ForumReply(
    val id: Long = 0,
    val postId: Long = 0,
    val authorId: String = "",
    val authorName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
