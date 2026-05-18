package db

import (
	"quantumlink-server/internal/model"
)

// CreatePost 创建帖子
func CreatePost(post *model.ForumPost) error {
	_, err := DB.Exec(
		`INSERT INTO forum_posts (author_id, title, content, is_pinned, created_at)
		VALUES (?, ?, ?, ?, ?)`,
		post.AuthorID, post.Title, post.Content, post.IsPinned, post.CreatedAt,
	)
	return err
}

// GetPost 获取帖子详情
func GetPost(id int64) (*model.ForumPost, error) {
	post := &model.ForumPost{}
	err := DB.QueryRow(
		`SELECT id, author_id, title, content, is_pinned, reply_count, like_count, created_at
		FROM forum_posts WHERE id = ?`, id,
	).Scan(&post.ID, &post.AuthorID, &post.Title, &post.Content,
		&post.IsPinned, &post.ReplyCount, &post.LikeCount, &post.CreatedAt)
	if err != nil {
		return nil, err
	}
	return post, nil
}

// ListPosts 获取帖子列表
func ListPosts(page, pageSize int) ([]*model.ForumPost, int, error) {
	if page <= 0 {
		page = 1
	}
	if pageSize <= 0 {
		pageSize = 20
	}
	offset := (page - 1) * pageSize

	// 获取总数
	var total int
	err := DB.QueryRow(`SELECT COUNT(*) FROM forum_posts`).Scan(&total)
	if err != nil {
		return nil, 0, err
	}

	rows, err := DB.Query(
		`SELECT id, author_id, title, content, is_pinned, reply_count, like_count, created_at
		FROM forum_posts
		ORDER BY is_pinned DESC, created_at DESC
		LIMIT ? OFFSET ?`, pageSize, offset,
	)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var posts []*model.ForumPost
	for rows.Next() {
		p := &model.ForumPost{}
		if err := rows.Scan(&p.ID, &p.AuthorID, &p.Title, &p.Content,
			&p.IsPinned, &p.ReplyCount, &p.LikeCount, &p.CreatedAt); err != nil {
			return nil, 0, err
		}
		posts = append(posts, p)
	}
	return posts, total, rows.Err()
}

// IncrementReplyCount 增加回复数
func IncrementReplyCount(postID int64) error {
	_, err := DB.Exec(
		`UPDATE forum_posts SET reply_count = reply_count + 1 WHERE id = ?`, postID,
	)
	return err
}

// IncrementLikeCount 增加点赞数
func IncrementLikeCount(postID int64) error {
	_, err := DB.Exec(
		`UPDATE forum_posts SET like_count = like_count + 1 WHERE id = ?`, postID,
	)
	return err
}

// CreateReply 创建回复
func CreateReply(reply *model.ForumReply) error {
	_, err := DB.Exec(
		`INSERT INTO forum_replies (post_id, author_id, content, created_at)
		VALUES (?, ?, ?, ?)`,
		reply.PostID, reply.AuthorID, reply.Content, reply.CreatedAt,
	)
	return err
}

// GetReplies 获取帖子回复列表
func GetReplies(postID int64) ([]*model.ForumReply, error) {
	rows, err := DB.Query(
		`SELECT id, post_id, author_id, content, created_at
		FROM forum_replies
		WHERE post_id = ?
		ORDER BY created_at ASC`, postID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var replies []*model.ForumReply
	for rows.Next() {
		r := &model.ForumReply{}
		if err := rows.Scan(&r.ID, &r.PostID, &r.AuthorID, &r.Content, &r.CreatedAt); err != nil {
			return nil, err
		}
		replies = append(replies, r)
	}
	return replies, rows.Err()
}
