package com.wuming.quantumlink.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wuming.quantumlink.domain.model.ForumPost

@Entity(
    tableName = "forum_posts",
    indices = [Index(value = ["timestamp"])]
)
data class ForumPostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "author_id")
    val authorId: String = "",

    @ColumnInfo(name = "author_name")
    val authorName: String = "",

    @ColumnInfo(name = "content")
    val content: String = "",

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "reply_count")
    val replyCount: Int = 0,

    @ColumnInfo(name = "like_count")
    val likeCount: Int = 0,

    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean = false
) {
    fun toDomain(): ForumPost = ForumPost(
        id = id, title = title, authorId = authorId,
        authorName = authorName, content = content,
        timestamp = timestamp, replyCount = replyCount,
        likeCount = likeCount, isPinned = isPinned
    )

    companion object {
        fun fromDomain(p: ForumPost): ForumPostEntity = ForumPostEntity(
            id = p.id, title = p.title, authorId = p.authorId,
            authorName = p.authorName, content = p.content,
            timestamp = p.timestamp, replyCount = p.replyCount,
            likeCount = p.likeCount, isPinned = p.isPinned
        )
    }
}
