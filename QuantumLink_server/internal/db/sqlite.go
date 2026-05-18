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

	-- 只在首次创建时建表，已有表不做删除（避免每次重启数据丢失）
	CREATE TABLE IF NOT EXISTS users (
		id          INTEGER PRIMARY KEY AUTOINCREMENT,
		username    TEXT UNIQUE NOT NULL,
		phone       TEXT DEFAULT '',
		password    TEXT NOT NULL,
		nickname    TEXT NOT NULL,
		avatar      TEXT DEFAULT '',
		public_key  TEXT DEFAULT '',
		status      INTEGER NOT NULL DEFAULT 0,
		created_at  INTEGER NOT NULL,
		last_seen   INTEGER NOT NULL DEFAULT 0
	);

	CREATE TABLE IF NOT EXISTS contacts (
		user_id     INTEGER NOT NULL REFERENCES users(id),
		contact_id  INTEGER NOT NULL REFERENCES users(id),
		added_at    INTEGER NOT NULL,
		PRIMARY KEY (user_id, contact_id)
	);

	CREATE TABLE IF NOT EXISTS conversations (
		id              TEXT PRIMARY KEY,
		type            INTEGER NOT NULL DEFAULT 0,
		name            TEXT DEFAULT '',
		created_at      INTEGER NOT NULL,
		expires_at      INTEGER DEFAULT 0
	);

	CREATE TABLE IF NOT EXISTS conversation_members (
		conversation_id TEXT NOT NULL REFERENCES conversations(id),
		user_id         INTEGER NOT NULL REFERENCES users(id),
		joined_at       INTEGER NOT NULL,
		PRIMARY KEY (conversation_id, user_id)
	);

	CREATE TABLE IF NOT EXISTS messages (
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

	CREATE TABLE IF NOT EXISTS forum_posts (
		id          INTEGER PRIMARY KEY AUTOINCREMENT,
		author_id   INTEGER NOT NULL REFERENCES users(id),
		title       TEXT NOT NULL,
		content     TEXT NOT NULL,
		is_pinned   INTEGER DEFAULT 0,
		reply_count INTEGER DEFAULT 0,
		like_count  INTEGER DEFAULT 0,
		created_at  INTEGER NOT NULL
	);

	CREATE TABLE IF NOT EXISTS forum_replies (
		id          INTEGER PRIMARY KEY AUTOINCREMENT,
		post_id     INTEGER NOT NULL REFERENCES forum_posts(id),
		author_id   INTEGER NOT NULL REFERENCES users(id),
		content     TEXT NOT NULL,
		created_at  INTEGER NOT NULL
	);

	-- UID 从 1000 开始（仅首次）
	INSERT OR IGNORE INTO sqlite_sequence (name, seq) VALUES ('users', 1000);

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
