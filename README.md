# 量子飞信 (QuantumLink)

> 轻量级安全通信平台 — IM + 密信 + 论坛 + VPN

**量子飞信** 是一款面向个人/小团体的自部署即时通讯软件，强调**隐私安全**与**轻量化**。

- **IM 即时通讯**：WebSocket 实时消息，二进制协议高效传输
- **密信 (Secret Chat)**：服务端计时到期销毁界面显示，但**依法保留服务端记录**
- **论坛**：支持发帖、回复、点赞
- **VPN 集成**：WireGuard 配置下发（可选）
- **全平台**：Go 服务端 + Android 客户端

---

## 📦 项目结构

```
QuantumLink/
├── README.md
├── build.ps1                    # 一键编译脚本
│
├── QuantumLink_server/          # Go 服务端
│   ├── main.go                  # 入口
│   ├── go.mod / go.sum
│   ├── config/                  # 配置
│   ├── internal/
│   │   ├── api/                 # REST API (chi router)
│   │   ├── db/                  # SQLite 数据库层
│   │   ├── middleware/          # JWT 鉴权 / CORS
│   │   ├── model/               # 数据模型
│   │   └── ws/                  # WebSocket (gorilla/websocket)
│   ├── scripts/
│   │   ├── migrate.sql          # 数据库建表 SQL
│   │   └── setup.sh             # Linux 一键部署
│   └── Makefile
│
└── QuantumLink_app/             # Android 客户端
    └── app/src/main/java/com/wuming/quantumlink/
        ├── core/                # Constants, IMManager, ServiceLocator
        ├── data/
        │   ├── local/           # Room 数据库 (DAO, Entity)
        │   └── remote/          # WebSocket + HTTP API
        ├── domain/              # 领域模型
        ├── manager/             # LoginManager, ConnectionManager
        └── ui/                  # Compose UI
            ├── auth/            # 登录/注册
            ├── forum/           # 论坛
            ├── im/              # 聊天/通讯录
            ├── secretchat/      # 密信
            ├── settings/        # 设置
            ├── theme/           # 主题
            └── vpn/             # VPN
```

---

## 🚀 快速开始

### 环境要求

| 组件 | 版本 |
|------|------|
| Go | 1.22+ |
| Android SDK | 35 |
| JDK | 17+ |

### 1. 启动服务端

```bash
cd QuantumLink_server

# 设置国内代理（可选）
export GOPROXY=https://goproxy.cn,direct

# 下载依赖
go mod tidy

# 编译当前平台
go build -ldflags="-s -w" -trimpath -o quantumlink-server .

# 启动
./quantumlink-server
```

启动后控制台会打印本机所有 IP 地址和端口信息。

### 2. 编译 Android 客户端

```bash
cd QuantumLink_app
./gradlew assembleDebug --offline
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. 一键编译（根目录）

```powershell
# Windows
.\build.ps1                # 全量编译
.\build.ps1 -Target debug  # 快速编译（仅当前平台 + Debug APK）
.\build.ps1 -Target server # 仅服务端
.\build.ps1 -Target android # 仅 Android
```

---

## 🔧 配置

### 服务端配置（环境变量）

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `WS_PORT` | `8081` | WebSocket 端口 |
| `REST_PORT` | `8082` | REST API 端口 |
| `DB_PATH` | `quantumlink.db` | 数据库路径 |
| `JWT_SECRET` | `quantumlink-change-me-in-production` | JWT 签名密钥 |
| `SECRET_EXPIRE_MIN` | `30` | 密信过期时间（分钟） |
| `CLEANUP_INTERVAL_MIN` | `5` | 清理间隔（分钟） |

### 客户端配置

在 App 内 **设置 → 网络设置** 中配置服务器地址和端口。

---

## 📱 功能说明

### 登录/注册
- 注册需填写：用户名、密码、昵称
- 支持选填手机号
- 密码使用 bcrypt 加密存储
- UID 为注册顺序自增（1, 2, 3...）

### 通讯录
- 点击右上角 `+` 搜索用户
- 支持按 UID、用户名、昵称搜索
- 双向添加联系人

### 即时通讯
- WebSocket 实时消息推送
- 二进制协议高效传输
- 消息持久化到本地 Room 数据库

### 密信 (Secret Chat)
- **服务端计时**：到期服务端推送销毁通知
- **服务端依法保留记录**：仅客户端清除界面显示
- **导出功能**：销毁前可通过导出按钮保存记录
- **强制 VPN**（可选）：密信对话需通过 VPN

### 论坛
- 发帖、回复、点赞
- 帖子列表支持分页

### VPN (WireGuard)
- 服务端下发 VPN 配置
- 客户端一键连接/断开

---

## 🔒 法律合规说明

本软件遵循中国法律法规关于网络安全和数据处理的要求：

1. **密码安全**：所有密码使用 bcrypt 加密存储，不存明文
2. **消息记录**：服务端依法保留所有消息记录，不可删除
3. **密信机制**：密信到期仅客户端销毁显示，服务端仍保留记录
4. **导出功能**：用户可在密信到期前通过导出功能获取自己的消息记录
5. **数据备份**：SQLite 单文件，便于备份和审计

---

## 🌐 部署到公网

### 方案一：Caddy 反向代理（推荐）

```caddyfile
# Caddyfile
im.example.com {
    reverse_proxy localhost:8082
}
```

### 方案二：Docker（即将支持）

```bash
# 一键部署脚本
bash scripts/setup.sh
```

### 交叉编译 Linux 版本

```bash
cd QuantumLink_server
GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -trimpath -o quantumlink-server-linux-amd64 .
GOOS=linux GOARCH=arm64 go build -ldflags="-s -w" -trimpath -o quantumlink-server-linux-arm64 .
```

---

## 🛠 技术栈

| 层 | 技术 |
|----|------|
| **服务端语言** | Go 1.22+ |
| **REST 框架** | chi/v5 |
| **WebSocket** | gorilla/websocket |
| **数据库** | SQLite (modernc.org/sqlite，纯 Go 无 CGO) |
| **鉴权** | JWT (HS256) |
| **密码** | bcrypt |
| **客户端** | Kotlin + Jetpack Compose |
| **客户端 DB** | Room |
| **客户端 WS** | OkHttp |

---

## 📄 License

MIT License - 详见 [LICENSE](LICENSE)
