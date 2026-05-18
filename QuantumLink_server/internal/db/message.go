package db

import (
	"database/sql"
	"quantumlink-server/internal/model"
)

// CreateConversation 创建会话
func CreateConversation(conv *model.Conversation) error {
	_, err := DB.Exec(
		`INSERT INTO conversations (id, type, name, created_at, expires_at) VALUES (?, ?, ?, ?, ?)`,
		conv.ID, conv.Type, conv.Name, conv.CreatedAt, conv.ExpiresAt,
	)
	return err
}

// AddConversationMember 添加会话成员
func AddConversationMember(convID, userID string, joinedAt int64) error {
	_, err := DB.Exec(
		`INSERT INTO conversation_members (conversation_id, user_id, joined_at) VALUES (?, ?, ?)`,
		convID, userID, joinedAt,
	)
	return err
}

// GetConversation 获取会话详情
func GetConversation(id string) (*model.Conversation, error) {
	conv := &model.Conversation{}
	err := DB.QueryRow(
		`SELECT id, type, name, created_at, expires_at FROM conversations WHERE id = ?`, id,
	).Scan(&conv.ID, &conv.Type, &conv.Name, &conv.CreatedAt, &conv.ExpiresAt)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return conv, err
}

// GetUserConversations 获取用户的所有会话
func GetUserConversations(userID string) ([]*model.Conversation, error) {
	rows, err := DB.Query(
		`SELECT c.id, c.type, c.name, c.created_at, c.expires_at
		FROM conversations c
		JOIN conversation_members cm ON c.id = cm.conversation_id
		WHERE cm.user_id = ?
		ORDER BY c.created_at DESC`, userID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var convs []*model.Conversation
	for rows.Next() {
		c := &model.Conversation{}
		if err := rows.Scan(&c.ID, &c.Type, &c.Name, &c.CreatedAt, &c.ExpiresAt); err != nil {
			return nil, err
		}
		convs = append(convs, c)
	}
	return convs, rows.Err()
}

// SaveMessage 保存消息
func SaveMessage(msg *model.Message) error {
	_, err := DB.Exec(
		`INSERT INTO messages (conversation_id, sender_id, content, msg_type, created_at, is_secret, expires_at)
		VALUES (?, ?, ?, ?, ?, ?, ?)`,
		msg.ConversationID, msg.SenderID, msg.Content, msg.MsgType, msg.CreatedAt, msg.IsSecret, msg.ExpiresAt,
	)
	return err
}

// GetMessages 获取会话中的历史消息（分页）
// before=0 时返回最新消息；导出场景用 before 设为一个未来的时间戳获取全部
func GetMessages(conversationID string, before int64, limit int) ([]*model.Message, error) {
	if limit <= 0 {
		limit = 50
	}
	if limit > 10000 {
		limit = 10000
	}
	rows, err := DB.Query(
		`SELECT id, conversation_id, sender_id, content, msg_type, created_at, is_secret, expires_at
		FROM messages
		WHERE conversation_id = ? AND (created_at < ? OR ? = 0)
		ORDER BY created_at DESC
		LIMIT ?`, conversationID, before, before, limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var msgs []*model.Message
	for rows.Next() {
		m := &model.Message{}
		if err := rows.Scan(&m.ID, &m.ConversationID, &m.SenderID, &m.Content,
			&m.MsgType, &m.CreatedAt, &m.IsSecret, &m.ExpiresAt); err != nil {
			return nil, err
		}
		msgs = append(msgs, m)
	}
	return msgs, rows.Err()
}

// DeleteExpiredSecretMessages 删除过期的密信
func DeleteExpiredSecretMessages(now int64) (int64, error) {
	result, err := DB.Exec(
		`DELETE FROM messages WHERE is_secret = 1 AND expires_at > 0 AND expires_at < ?`, now,
	)
	if err != nil {
		return 0, err
	}
	return result.RowsAffected()
}

// GetConversationMembers 获取会话成员列表
func GetConversationMembers(convID string) ([]string, error) {
	rows, err := DB.Query(
		`SELECT user_id FROM conversation_members WHERE conversation_id = ?`, convID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var members []string
	for rows.Next() {
		var uid string
		if err := rows.Scan(&uid); err != nil {
			return nil, err
		}
		members = append(members, uid)
	}
	return members, rows.Err()
}

// DeleteConversation 删除会话
func DeleteConversation(id string) error {
	_, err := DB.Exec(`DELETE FROM conversations WHERE id = ?`, id)
	return err
}

// RemoveConversationMember 移除会话成员
func RemoveConversationMember(convID, userID string) error {
	_, err := DB.Exec(
		`DELETE FROM conversation_members WHERE conversation_id = ? AND user_id = ?`,
		convID, userID,
	)
	return err
}
