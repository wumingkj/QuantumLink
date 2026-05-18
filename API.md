# 量子飞信 API 文档

**基础地址**: `http://<server>:8082/api`

**认证方式**: JWT Bearer Token（登录后获得）

---

## 一、认证

### POST /auth/register

注册新用户。

**请求体：**
```json
{
    "username": "alice",        // 必填
    "password": "123456",       // 必填
    "nickname": "爱丽丝",       // 必填
    "phone": "13800138000"      // 选填
}
```

**响应 (201)：**
```json
{
    "id": 1,
    "username": "alice",
    "nickname": "爱丽丝",
    "phone": "13800138000",
    "avatar": "",
    "created_at": 1717056000,
    "last_seen": 1717056000
}
```

### POST /auth/login

登录获取 JWT Token。

**请求体：**
```json
{
    "username": "alice",
    "password": "123456"
}
```

**响应 (200)：**
```json
{
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "user": {
        "id": 1,
        "username": "alice",
        "nickname": "爱丽丝",
        "avatar": "",
        "created_at": 1717056000,
        "last_seen": 1717056000
    }
}
```

### POST /auth/refresh

刷新 Token。

**请求体：**
```json
{
    "token": "eyJhbGciOiJIUzI1NiIs..."
}
```

---

## 二、用户

以下接口需要 `Authorization: Bearer <token>` 头。

### GET /users/me

获取当前用户信息。

### PUT /users/me

更新个人信息。

**请求体：**
```json
{
    "nickname": "新昵称",
    "avatar": "data:image/png;base64,...",
    "phone": "13900001111"
}
```

### GET /users/search?q=关键词

搜索用户（支持 UID、用户名、昵称）。

---

## 三、联系人

### GET /contacts

获取联系人列表。

### POST /contacts

添加联系人。

**请求体：**
```json
{
    "contact_id": "2"
}
```

### DELETE /contacts/:id

删除联系人。

---

## 四、会话

### GET /conversations

获取会话列表。

### POST /conversations

创建会话。

**请求体：**
```json
{
    "type": 0,
    "name": "群聊名称",
    "member_ids": ["2", "3"]
}
```

- `type`: 0=单人 1=群组 2=密信

### GET /conversations/:id

获取会话详情。

### DELETE /conversations/:id

退出/删除会话。

### GET /conversations/:id/messages?before=时间戳&limit=50

获取历史消息。

### GET /conversations/:id/export

导出会话记录（支持 `?format=txt` 导出纯文本）。

---

## 五、论坛

### GET /posts?page=1&page_size=20

帖子列表。

### POST /posts

发帖。

**请求体：**
```json
{
    "title": "帖子标题",
    "content": "帖子内容"
}
```

### GET /posts/:id

帖子详情（含回复）。

### POST /posts/:id/like

点赞。

### POST /posts/:id/reply

回复。

**请求体：**
```json
{
    "content": "回复内容"
}
```

---

## 六、WebSocket 协议

**连接地址**: `ws://<server>:8082/ws`

### 二进制协议格式

```
[1字节类型] [4字节长度(大端)] [负载数据]
```

### 消息类型

| 类型字节 | 名称 | 方向 | 说明 |
|----------|------|------|------|
| 0x01 | MSG | 双向 | 消息发送/接收 |
| 0x02 | PING | C→S | 心跳 |
| 0x03 | PONG | S→C | 心跳回复 |
| 0x04 | AUTH | C→S | 认证 (payload=JWT token) |
| 0x05 | ONLINE | S→C | 联系人上线/下线通知 |
| 0x06 | ERROR | S→C | 错误提示 |
| 0x07 | CREATE_CONV | C→S | 创建会话 |
| 0x08 | TYPING | C→S | 正在输入 |
| 0x09 | SECRET_EXPIRED | S→C | 密信过期提示 |
| **0x0A** | **SECRET_DESTROY** | **S→C** | **密信销毁通知** |
| **0x0B** | **SECRET_EXPIRY_INFO** | **S→C** | **密信过期时间信息** |

### 认证流程

1. 客户端建立 WS 连接
2. 立即发送 AUTH 帧（payload=JWT token 的 UTF-8 字节）
3. 服务端验证通过后开始转发消息

### 客户端发送格式（Base64）

Android 客户端将二进制包 Base64 编码后以 Text 帧发送，服务端自动解码。
