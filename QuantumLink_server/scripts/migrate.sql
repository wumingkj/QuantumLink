-- 量子飞信 数据库初始化脚本

CREATE TABLE IF NOT EXISTS users (
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
