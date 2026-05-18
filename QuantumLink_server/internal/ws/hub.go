package ws

import (
	"log"
	"sync"
)

// ClientInfo 客户端信息
type ClientInfo struct {
	UserID string
	ConnID string
}

// Hub 连接管理器 - 采用房间模型
type Hub struct {
	mu sync.RWMutex

	// 所有已认证的客户端连接
	clients map[string]*Client

	// 用户ID → 连接ID列表（一个用户可在多设备登录）
	userClients map[string]map[string]bool

	// 注册通道
	register chan *Client

	// 注销通道
	unregister chan *Client

	// 广播通道（按会话ID）
	broadcast chan *BroadcastMessage
}

// BroadcastMessage 广播消息
type BroadcastMessage struct {
	ConversationID string
	Data           []byte
	SenderID       string // 发送者 ID，不转发给发送者自己
}

// RegisterUnregistered 注册未认证的连接（临时占位）
// 等待客户端发送 AUTH 帧后更新 UserID
func (h *Hub) RegisterUnregistered(client *Client) {
	// 先不触发 register 通道（不上线通知），等 AUTH 后手动注册
	h.mu.Lock()
	h.clients[client.ConnID] = client
	// 使用空 userID 占位
	if _, ok := h.userClients[""]; !ok {
		h.userClients[""] = make(map[string]bool)
	}
	h.userClients[""][client.ConnID] = true
	h.mu.Unlock()
}

// NewHub 创建 Hub
func NewHub() *Hub {
	return &Hub{
		clients:     make(map[string]*Client),
		userClients: make(map[string]map[string]bool),
		register:    make(chan *Client, 256),
		unregister:  make(chan *Client, 256),
		broadcast:   make(chan *BroadcastMessage, 1024),
	}
}

// Run 启动 Hub 事件循环
func (h *Hub) Run() {
	for {
		select {
		case client := <-h.register:
			h.mu.Lock()
			h.clients[client.ConnID] = client
			if _, ok := h.userClients[client.UserID]; !ok {
				h.userClients[client.UserID] = make(map[string]bool)
			}
			h.userClients[client.UserID][client.ConnID] = true
			h.mu.Unlock()

			// 通知联系人该用户上线
			h.notifyOnlineStatus(client.UserID, true)
			log.Printf("[Hub] 用户 %s 上线 (连接: %s)", client.UserID, client.ConnID)

		case client := <-h.unregister:
			h.mu.Lock()
			if _, ok := h.clients[client.ConnID]; ok {
				delete(h.clients, client.ConnID)
				if clients, ok := h.userClients[client.UserID]; ok {
					delete(clients, client.ConnID)
					if len(clients) == 0 {
						delete(h.userClients, client.UserID)
					}
				}
			}
			h.mu.Unlock()

			// 通知联系人该用户下线
			h.notifyOnlineStatus(client.UserID, false)
			log.Printf("[Hub] 用户 %s 下线", client.UserID)

		case msg := <-h.broadcast:
			h.mu.RLock()
			// 获取会话所有成员
			// 注意：这里需要从数据库查询成员列表，实际使用时由调用方提供
			// 为了解耦，broadcast 的调用方应先查询成员列表再逐个发送
			h.mu.RUnlock()
			_ = msg
		}
	}
}

// SendToUser 向指定用户的所有设备发送消息
func (h *Hub) SendToUser(userID string, data []byte) {
	h.mu.RLock()
	defer h.mu.RUnlock()

	if conns, ok := h.userClients[userID]; ok {
		for connID := range conns {
			if client, ok := h.clients[connID]; ok {
				select {
				case client.Send <- data:
				default:
					log.Printf("[Hub] 用户 %s 的连接 %s 发送缓冲区已满，丢弃消息", userID, connID)
				}
			}
		}
	}
}

// SendToUsers 向多个用户发送消息
func (h *Hub) SendToUsers(userIDs []string, data []byte, excludeSenderID string) {
	h.mu.RLock()
	defer h.mu.RUnlock()

	for _, uid := range userIDs {
		if uid == excludeSenderID {
			continue // 不转发给发送者自己
		}
		if conns, ok := h.userClients[uid]; ok {
			for connID := range conns {
				if client, ok := h.clients[connID]; ok {
					select {
					case client.Send <- data:
					default:
						log.Printf("[Hub] 用户 %s 的连接 %s 发送缓冲区已满", uid, connID)
					}
				}
			}
		}
	}
}

// IsUserOnline 检查用户是否在线
func (h *Hub) IsUserOnline(userID string) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	_, ok := h.userClients[userID]
	return ok
}

// notifyOnlineStatus 通知联系人用户上线/下线状态
func (h *Hub) notifyOnlineStatus(userID string, online bool) {
	// 从数据库查询联系人列表并通知
	// 此处的实际实现在 API 层调用
	data := MakeOnlinePacket([]byte(userID + ":" + boolToStr(online)))
	_ = data
	// TODO: 通过 db 查询 contacts，然后调用 SendToUser
}

// GetOnlineUsers 获取所有在线用户 ID
func (h *Hub) GetOnlineUsers() []string {
	h.mu.RLock()
	defer h.mu.RUnlock()

	users := make([]string, 0, len(h.userClients))
	for uid := range h.userClients {
		users = append(users, uid)
	}
	return users
}

// ClientCount 获取在线客户端数量
func (h *Hub) ClientCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients)
}

// UserCount 获取在线用户数量
func (h *Hub) UserCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.userClients)
}

func boolToStr(b bool) string {
	if b {
		return "1"
	}
	return "0"
}
