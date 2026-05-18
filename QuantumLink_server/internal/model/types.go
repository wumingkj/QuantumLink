package model

// ==================== 数据表模型 ====================

type User struct {
	ID        int64  `json:"id"`         // 自增 UID
	Username  string `json:"username"`
	Phone     string `json:"phone,omitempty"`
	Password  string `json:"-"`          // bcrypt hash，不返回给客户端
	Nickname  string `json:"nickname"`
	Avatar    string `json:"avatar"`
	PublicKey string `json:"public_key,omitempty"`
	Status    int    `json:"status"`     // 0=正常 1=已注销
	CreatedAt int64  `json:"created_at"`
	LastSeen  int64  `json:"last_seen"`
}

type Contact struct {
	UserID    string `json:"user_id"`
	ContactID string `json:"contact_id"`
	AddedAt   int64  `json:"added_at"`
}

type Message struct {
	ID             int64  `json:"id"`
	ConversationID string `json:"conversation_id"`
	SenderID       string `json:"sender_id"`
	Content        []byte `json:"content"`
	MsgType        int    `json:"msg_type"` // 0=普通 1=密信
	CreatedAt      int64  `json:"created_at"`
	IsSecret       int    `json:"is_secret"`
	ExpiresAt      int64  `json:"expires_at,omitempty"`
}

type Conversation struct {
	ID        string `json:"id"`
	Type      int    `json:"type"` // 0=单人 1=群组 2=密信
	Name      string `json:"name"`
	CreatedAt int64  `json:"created_at"`
	ExpiresAt int64  `json:"expires_at,omitempty"`
}

type ConversationMember struct {
	ConversationID string `json:"conversation_id"`
	UserID         string `json:"user_id"`
	JoinedAt       int64  `json:"joined_at"`
}

type ForumPost struct {
	ID         int64  `json:"id"`
	AuthorID   string `json:"author_id"`
	Title      string `json:"title"`
	Content    string `json:"content"`
	IsPinned   int    `json:"is_pinned"`
	ReplyCount int    `json:"reply_count"`
	LikeCount  int    `json:"like_count"`
	CreatedAt  int64  `json:"created_at"`
}

type ForumReply struct {
	ID        int64  `json:"id"`
	PostID    int64  `json:"post_id"`
	AuthorID  string `json:"author_id"`
	Content   string `json:"content"`
	CreatedAt int64  `json:"created_at"`
}

// ==================== WebSocket 协议 ====================

// 消息类型字节
const (
	MsgTypeMessage      byte = 0x01
	MsgTypePing         byte = 0x02
	MsgTypePong         byte = 0x03
	MsgTypeAuth         byte = 0x04
	MsgTypeOnline       byte = 0x05
	MsgTypeError        byte = 0x06
	MsgTypeCreateConv   byte = 0x07
	MsgTypeTyping       byte = 0x08
	MsgTypeSecretExpired    byte = 0x09
	MsgTypeSecretDestroy    byte = 0x0A // 服务端→客户端：密信会话已销毁（仅客户端清除显示）
	MsgTypeSecretExpiryInfo byte = 0x0B // 服务端→客户端：密信过期时间信息
	MsgTypeExportRequest    byte = 0x0C // 客户端→服务端：请求导出密信记录
	MsgTypeExportResponse   byte = 0x0D // 服务端→客户端：导出数据响应
)

// WSPacket 二进制协议包
type WSPacket struct {
	Type    byte
	Payload []byte
}

// ==================== API 请求/响应 ====================

type RegisterRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
	Nickname string `json:"nickname"`
	Phone    string `json:"phone,omitempty"`
}

type LoginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type LoginResponse struct {
	Token string `json:"token"`
	User  User   `json:"user"`
}

type TokenRefreshRequest struct {
	Token string `json:"token"`
}

type CreateConversationRequest struct {
	Type   int      `json:"type"` // 0=单人 1=群组 2=密信
	Name   string   `json:"name,omitempty"`
	MemberIDs []string `json:"member_ids"`
}

type SendMessageRequest struct {
	ConversationID string `json:"conversation_id"`
	Content        []byte `json:"content"`
	MsgType        int    `json:"msg_type"`
}

type CreatePostRequest struct {
	Title   string `json:"title"`
	Content string `json:"content"`
}

type CreateReplyRequest struct {
	Content string `json:"content"`
}

type UpdateProfileRequest struct {
	Nickname  string `json:"nickname,omitempty"`
	Avatar    string `json:"avatar,omitempty"`
	Phone     string `json:"phone,omitempty"`
	PublicKey string `json:"public_key,omitempty"`
}

type AddContactRequest struct {
	ContactID string `json:"contact_id"`
}

type UploadPubKeyRequest struct {
	PublicKey string `json:"public_key"`
}

// ==================== 通用 ====================

type ErrorResponse struct {
	Error string `json:"error"`
}

type SuccessResponse struct {
	Message string `json:"message"`
}
