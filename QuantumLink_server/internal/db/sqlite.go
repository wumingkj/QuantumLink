package db

import (
	"database/sql"
	"fmt"
	"os"
	"path/filepath"

	_ "modernc.org/sqlite"
)

var DB *sql.DB

// Init 初始化 SQLite 数据库
func Init(dbPath string) error {
	// 确保目录存在
	dir := filepath.Dir(dbPath)
	if dir != "." {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return fmt.Errorf("创建数据库目录失败: %w", err)
		}
	}

	var err error
	DB, err = sql.Open("sqlite", dbPath)
	if err != nil {
		return fmt.Errorf("打开数据库失败: %w", err)
	}

	// 测试连接
	if err = DB.Ping(); err != nil {
		return fmt.Errorf("数据库连接失败: %w", err)
	}

	// 启用 WAL 模式提升并发性能
	if _, err = DB.Exec("PRAGMA journal_mode=WAL"); err != nil {
		return fmt.Errorf("启用 WAL 失败: %w", err)
	}
	if _, err = DB.Exec("PRAGMA foreign_keys=ON"); err != nil {
		return fmt.Errorf("启用外键约束失败: %w", err)
	}

	// 执行建表
	if err = migrate(); err != nil {
		return fmt.Errorf("数据库迁移失败: %w", err)
	}

	return nil
}

// migrate 执行建表 SQL
func migrate() error {
	schema := `
	PRAGMA writable_schema = ON;

	-- 全新建表（开发阶段删除重建）
	DROP TABLE IF EXISTS forum_replies;
	DROP TABLE IF EXISTS forum_posts;
	DROP TABLE IF EXISTS messages;
	DROP TABLE IF EXISTS conversation_members;
	DROP TABLE IF EXISTS conversations;
	DROP TABLE IF EXISTS contacts;
	DROP TABLE IF EXISTS users;

	CREATE TABLE users (
		id          INTEGER PRIMARY KEY AUTOINCREMENT,
		username    TEXT UNIQUE NOT NULL,
		phone       TEXT DEFAULT '',
		password    TEXT NOT NULL,
		nickname    TEXT NOT NULL,
		avatar      TEXT DEFAULT '',
		public_key  TEXT DEFAULT '',
		created_at  INTEGER NOT NULL,
		last_seen   INTEGER NOT NULL DEFAULT 0
	);

	CREATE TABLE contacts (
		user_id     INTEGER NOT NULL REFERENCES users(id),
		contact_id  INTEGER NOT NULL REFERENCES users(id),
		added_at    INTEGER NOT NULL,
		PRIMARY KEY (user_id, contact_id)
	);

	CREATE TABLE conversations (
		id              TEXT PRIMARY KEY,
		type            INTEGER NOT NULL DEFAULT 0,
		name            TEXT DEFAULT '',
		created_at      INTEGER NOT NULL,
		expires_at      INTEGER DEFAULT 0
	);

	CREATE TABLE conversation_members (
		conversation_id TEXT NOT NULL REFERENCES conversations(id),
		user_id         INTEGER NOT NULL REFERENCES users(id),
		joined_at       INTEGER NOT NULL,
		PRIMARY KEY (conversation_id, user_id)
	);

	CREATE TABLE messages (
		id              INTEGER PRIMARY KEY AUTOINCREMENT,
		conversation_id TEXT NOT NULL,
		sender_id       INTEGER NOT NULL REFERENCES users(id),
		content         BLOB NOT NULL,
		msg_type        INTEGER NOT NULL DEFAULT 0,
		created_at      INTEGER NOT NULL,
		is_secret       INTEGER DEFAULT 0,
		expires_at      INTEGER DEFAULT 0
	);
	CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conversation_id, created_at);

	CREATE TABLE forum_posts (
		id          INTEGER PRIMARY KEY AUTOINCREMENT,
		author_id   INTEGER NOT NULL REFERENCES users(id),
		title       TEXT NOT NULL,
		content     TEXT NOT NULL,
		is_pinned   INTEGER DEFAULT 0,
		reply_count INTEGER DEFAULT 0,
		like_count  INTEGER DEFAULT 0,
		created_at  INTEGER NOT NULL
	);

	CREATE TABLE forum_replies (
		id          INTEGER PRIMARY KEY AUTOINCREMENT,
		post_id     INTEGER NOT NULL REFERENCES forum_posts(id),
		author_id   INTEGER NOT NULL REFERENCES users(id),
		content     TEXT NOT NULL,
		created_at  INTEGER NOT NULL
	);

	PRAGMA writable_schema = OFF;
	`

	_, err := DB.Exec(schema)
	return err
}

// Close 关闭数据库
func Close() {
	if DB != nil {
		DB.Close()
	}
}
