# 量子飞信 服务端方案

> 编译环境: Windows (已就绪)
> 部署环境: Linux (debian 13)
> 目标: 个人/小团体自部署

---

## 一、技术选型

| 组件 | 方案 | 理由 |
|------|------|------|
| **语言** | Go 1.22+ | 单二进制部署、协程天然支持WebSocket、交叉编译(W→L) |
| **WebSocket** | gorilla/websocket | 稳定成熟，Go标准库 |
| **REST框架** | 标准库 net/http + chi/router | 轻量，无额外依赖 |
| **数据库** | SQLite (via mattn/go-sqlite3) | 小团体无需MySQL，单文件备份 |
| **消息队列** | 内存Channel | 小规模够用，无需Redis/Kafka |
| **VPN** | WireGuard (系统级) | 内核模块，性能最优 |
| **部署** | Docker (可选) 或 systemd | 简单 |
| **TLS** | Caddy 自动申请 Let's Encrypt | 自动HTTPS/WSS |

> Go 交叉编译: `GOOS=linux GOARCH=amd64 go build` 在 Windows 上直接打出 Linux 二进制

---

## 二、整体架构

```
┌─────────────────────────────────────────┐
│            Caddy (反向代理+TLS)           │
│   wss://im.example.com  /  https://api   │
└──────────┬────────────────────┬──────────┘
           │                    │
┌──────────▼────┐     ┌────────▼────────┐
│  WS 服务       │     │  REST API       │
│  :8081         │     │  :8082          │
│                │     │                 │
│  ┌──────────┐  │     │  ┌───────────┐  │
│  │ 消息路由  │  │     │  │ 用户/论坛  │  │
│  │ 在线状态  │  │     │  │ 联系人    │  │
│  └──────────┘  │     │  └───────────┘  │
└──────────┬─────┘     └───────┬─────────┘
           │                    │
           └────────┬──────────┘
                    │
           ┌────────▼────────┐
           │   SQLite 数据库  │
           │   quantumlink.db │
           └─────────────────┘

┌─────────────────────────────────────────┐
│          WireGuard (系统服务)            │
│  通过 CLI 或 wg-quick 管理配置           │
└─────────────────────────────────────────┘
```

---

## 三、数据库设计

### 3.1 用户表
```sql
CREATE TABLE users (
    id          TEXT PRIMARY KEY,           -- UUID
    username    TEXT UNIQUE NOT NULL,
    password    TEXT NOT NULL,              -- bcrypt 哈希
    nickname    TEXT NOT NULL,
    avatar      TEXT DEFAULT '',
    public_key  TEXT DEFAULT '',            -- WireGuard 公钥
    created_at  INTEGER NOT NULL,
    last_seen   INTEGER NOT NULL DEFAULT 0
);
```

### 3.2 联系人表
```sql
CREATE TABLE contacts (
    user_id     TEXT NOT NULL REFERENCES users(id),
    contact_id  TEXT NOT NULL REFERENCES users(id),
    added_at    INTEGER NOT NULL,
    PRIMARY KEY (user_id, contact_id)
);
```

### 3.3 消息表（IM + 密信）
```sql
CREATE TABLE messages (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    sender_id       TEXT NOT NULL REFERENCES users(id),
    content         BLOB NOT NULL,          -- 二进制协议编码
    msg_type        INTEGER NOT NULL,       -- 0=普通 1=密信
    created_at      INTEGER NOT NULL,
    is_secret       INTEGER DEFAULT 0,      -- 是否为密信
    expires_at      INTEGER DEFAULT 0       -- 密信过期时间
);
CREATE INDEX idx_messages_conv ON messages(conversation_id, created_at);
```

### 3.4 会话表
```sql
CREATE TABLE conversations (
    id              TEXT PRIMARY KEY,
    type            INTEGER NOT NULL,       -- 0=单人 1=群组 2=密信
    name            TEXT DEFAULT '',
    created_at      INTEGER NOT NULL,
    expires_at      INTEGER DEFAULT 0       -- 密信会话过期时间
);

CREATE TABLE conversation_members (
    conversation_id TEXT NOT NULL REFERENCES conversations(id),
    user_id         TEXT NOT NULL REFERENCES users(id),
    joined_at       INTEGER NOT NULL,
    PRIMARY KEY (conversation_id, user_id)
);
```

### 3.5 论坛表
```sql
CREATE TABLE forum_posts (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    author_id   TEXT NOT NULL REFERENCES users(id),
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
    author_id   TEXT NOT NULL REFERENCES users(id),
    content     TEXT NOT NULL,
    created_at  INTEGER NOT NULL
);
```

---

## 四、WebSocket 协议

沿用客户端的 **二进制协议**（Base64 编码传输），新增服务端指令：

```
客户端 → 服务端:
  MSG        = 发送消息
  PING       = 心跳
  AUTH       = 认证 (token)
  CREATE_CONV = 创建会话
  TYPING     = 正在输入

服务端 → 客户端:
  MSG        = 收到消息并转发
  PONG       = 心跳回复
  ONLINE     = 联系人上线/下线通知
  ERROR      = 错误提示
  SECRET_EXPIRED = 密信会话已过期
```

每种消息用二进制协议第1字节区分（0x01=MSG, 0x02=PING, 0x03=AUTH...）

---

## 五、目录结构

```
quantumlink_server/
├── main.go                 # 入口，启动 WS + REST
├── go.mod / go.sum
├── config/
│   └── config.go           # 配置读取 (环境变量 + 配置文件)
├── internal/
│   ├── ws/
│   │   ├── hub.go          # 连接管理器 (房间模型)
│   │   ├── client.go       # 单个 WS 连接处理
│   │   └── protocol.go     # 二进制协议编解码
│   ├── api/
│   │   ├── router.go       # REST 路由
│   │   ├── auth.go         # 登录/注册/Token
│   │   ├── user.go         # 用户资料
│   │   ├── contact.go      # 联系人管理
│   │   ├── forum.go        # 论坛 CRUD
│   │   └── vpn.go          # VPN 配置下发
│   ├── db/
│   │   ├── sqlite.go       # 数据库初始化
│   │   ├── user.go         # 用户查询
│   │   ├── message.go      # 消息 CRUD
│   │   └── forum.go        # 论坛 CRUD
│   ├── model/
│   │   └── types.go        # 共享数据结构
│   └── middleware/
│       ├── auth.go         # JWT 验证中间件
│       └── cors.go         # CORS
├── scripts/
│   ├── migrate.sql         # 建表 SQL
│   └── setup.sh            # Linux 一键部署脚本
└── Makefile                # 编译 + 交叉编译命令
```

---

## 六、API 接口设计

### 6.1 认证
```
POST   /api/auth/register    # 注册
POST   /api/auth/login       # 登录 → 返回 JWT token
POST   /api/auth/refresh     # 刷新 token
```

### 6.2 用户
```
GET    /api/users/me          # 当前用户信息
PUT    /api/users/me          # 更新资料
GET    /api/users/search?q=   # 搜索用户
```

### 6.3 联系人
```
GET    /api/contacts                # 联系人列表
POST   /api/contacts                # 添加联系人
DELETE /api/contacts/:id            # 删除联系人
```

### 6.4 会话
```
GET    /api/conversations           # 会话列表
POST   /api/conversations           # 创建会话
GET    /api/conversations/:id       # 会话详情
DELETE /api/conversations/:id       # 删除会话/退出
GET    /api/conversations/:id/messages?before=&limit=  # 历史消息
```

### 6.5 论坛
```
GET    /api/posts                    # 帖子列表
POST   /api/posts                    # 发帖
GET    /api/posts/:id                # 帖子详情
POST   /api/posts/:id/like           # 点赞
POST   /api/posts/:id/reply          # 回复
```

### 6.6 VPN (WireGuard)
```
GET    /api/vpn/config               # 获取用户 VPN 配置
POST   /api/vpn/pubkey               # 上传客户端公钥
```

---

## 七、编译方式（Windows → Linux）

```powershell
# 在 Windows 编译 Linux amd64 二进制
cd quantumlink-server
set GOOS=linux
set GOARCH=amd64
go build -o quantumlink-server-linux .

# 然后传到服务器
scp quantumlink-server-linux root@YOUR_SERVER:/opt/quantumlink/
```

---

## 八、部署架构（单机够用）

```
Linux 服务器 (1C2G 以上)
├── Caddy (反向代理, 自动 HTTPS)
│   ├── wss://im.yourdomain.com → :8081
│   └── https://api.yourdomain.com → :8082
├── quantumlink-server (Go)
│   ├── WS :8081
│   └── REST :8082
├── SQLite (/data/quantumlink.db)
└── WireGuard (系统服务, wg-quick)
```

### 脚本 `setup.sh`

```bash
#!/bin/bash
# 1. 安装 Caddy
# 2. 部署 quantumlink-server 二进制
# 3. 创建 systemd 服务
# 4. 安装配置 WireGuard
# 5. 启动所有服务
```

---

## 九、安全与性能

| 关注点 | 方案 |
|--------|------|
| 密码存储 | bcrypt (cost=12) |
| 鉴权 | JWT (HS256, 7天过期) |
| WS 鉴权 | 连接时发送 AUTH 帧携带 token |
| 消息加密 | 二进制协议 + 传输层 WSS |
| 密信强制 VPN | 服务端检查 Client IP 是否经过 WG 接口 |
| 30min 自动删除 | 服务端定时协程扫描过期密信 |
| 防消息丢失 | 消息先写 SQLite 再转发，ACK 确认 |
| 并发 | Go goroutine 模型，单机支撑 1000+ 在线 |

---

## 十、开发计划

```
Phase 1: 基础架构
  ├── Go 项目初始化 + SQLite
  ├── WS Hub + Client 框架
  └── REST API 框架 + JWT 鉴权

Phase 2: IM 核心
  ├── 用户注册/登录
  ├── 发送/接收消息 (WS)
  ├── 历史消息拉取 (REST)
  └── 联系人管理

Phase 3: 密信
  ├── 创建密信会话
  ├── 强制 VPN 检测
  └── 30min 过期自动删除

Phase 4: 论坛
  ├── 帖子 CRUD
  └── 回复/点赞

Phase 5: VPN
  ├── WireGuard 配置下发 API
  └── Web 管理面板（可选）

Phase 6: 部署
  ├── Dockerfile
  └── 一键安装脚本
```
