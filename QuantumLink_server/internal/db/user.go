package db

import (
	"database/sql"
	"strconv"

	"quantumlink-server/internal/model"
)

// GetUserByIDStr 通过字符串 ID 获取用户（兼容 JWT sub 格式）
func GetUserByIDStr(idStr string) (*model.User, error) {
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		return nil, err
	}
	return GetUserByID(id)
}

// CreateUser 创建用户（ID 由 SQLite 自动递增）
func CreateUser(user *model.User) (int64, error) {
	result, err := DB.Exec(
		`INSERT INTO users (username, phone, password, nickname, avatar, status, created_at, last_seen)
		VALUES (?, ?, ?, ?, ?, 0, ?, ?)`,
		user.Username, user.Phone, user.Password, user.Nickname, user.Avatar, user.CreatedAt, user.LastSeen,
	)
	if err != nil {
		return 0, err
	}
	return result.LastInsertId()
}

// GetUserByID 通过 ID 获取用户
func GetUserByID(id int64) (*model.User, error) {
	user := &model.User{}
	err := DB.QueryRow(
		`SELECT id, username, COALESCE(phone,''), password, nickname, avatar, public_key, status, created_at, last_seen
		FROM users WHERE id = ?`, id,
	).Scan(&user.ID, &user.Username, &user.Phone, &user.Password, &user.Nickname,
		&user.Avatar, &user.PublicKey, &user.Status, &user.CreatedAt, &user.LastSeen)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return user, err
}

// GetUserByUsername 通过用户名获取用户
func GetUserByUsername(username string) (*model.User, error) {
	user := &model.User{}
	err := DB.QueryRow(
		`SELECT id, username, COALESCE(phone,''), password, nickname, avatar, public_key, status, created_at, last_seen
		FROM users WHERE username = ?`, username,
	).Scan(&user.ID, &user.Username, &user.Phone, &user.Password, &user.Nickname,
		&user.Avatar, &user.PublicKey, &user.Status, &user.CreatedAt, &user.LastSeen)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return user, err
}

// GetUserByPhone 通过手机号查找用户
func GetUserByPhone(phone string) (*model.User, error) {
	user := &model.User{}
	err := DB.QueryRow(
		`SELECT id, username, COALESCE(phone,''), password, nickname, avatar, public_key, status, created_at, last_seen
		FROM users WHERE phone = ?`, phone,
	).Scan(&user.ID, &user.Username, &user.Phone, &user.Password, &user.Nickname,
		&user.Avatar, &user.PublicKey, &user.Status, &user.CreatedAt, &user.LastSeen)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return user, err
}

// SearchUsers 搜索用户（按 UID、用户名、昵称匹配）
func SearchUsers(query string, limit int) ([]*model.User, error) {
	if limit <= 0 {
		limit = 20
	}
	rows, err := DB.Query(
		`SELECT id, username, COALESCE(phone,''), nickname, avatar, status, created_at, last_seen
		FROM users WHERE status = 0 AND (username LIKE ? OR nickname LIKE ? OR CAST(id AS TEXT) LIKE ?)
		LIMIT ?`, "%"+query+"%", "%"+query+"%", "%"+query+"%", limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var users []*model.User
	for rows.Next() {
		u := &model.User{}
		if err := rows.Scan(&u.ID, &u.Username, &u.Phone, &u.Nickname, &u.Avatar, &u.Status, &u.CreatedAt, &u.LastSeen); err != nil {
			return nil, err
		}
		users = append(users, u)
	}
	return users, rows.Err()
}

// UpdateUser 更新用户信息
func UpdateUser(user *model.User) error {
	_, err := DB.Exec(
		`UPDATE users SET nickname = ?, avatar = ?, phone = ?, public_key = ? WHERE id = ?`,
		user.Nickname, user.Avatar, user.Phone, user.PublicKey, user.ID,
	)
	return err
}

// DeleteUser 注销用户（软删除：标记 status=1，清空敏感信息，保留记录）
func DeleteUser(userIDStr string) error {
	_, err := DB.Exec(
		`UPDATE users SET status = 1, password = '', nickname = '已注销',
		phone = '', avatar = '', public_key = '' WHERE id = ?`, userIDStr,
	)
	return err
}

// UpdateLastSeen 更新最后在线时间
func UpdateLastSeen(userID int64, lastSeen int64) error {
	_, err := DB.Exec(`UPDATE users SET last_seen = ? WHERE id = ?`, lastSeen, userID)
	return err
}
