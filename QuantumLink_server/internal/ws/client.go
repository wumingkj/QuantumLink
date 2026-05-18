package ws

import (
	"encoding/base64"
	"log"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/gorilla/websocket"
	"quantumlink-server/internal/model"
)

// JWTSecret 用于验证 WS 端 AUTH 帧中的 token
// 从 config 中读取，在 main.go 启动时设置
var JWTSecret = ""

const (
	// 写入等待时间
	writeWait = 10 * time.Second

	// Pong 等待时间（必须大于服务端 Ping 间隔）
	pongWait = 60 * time.Second

	// 服务端 Ping 间隔（必须小于 pongWait）
	pingPeriod = (pongWait * 9) / 10

	// 最大消息大小
	maxMessageSize = 4096

	// 发送缓冲区大小
	sendBufferSize = 256
)

// Client 单个 WebSocket 连接
type Client struct {
	Hub    *Hub
	Conn   *websocket.Conn
	Send   chan []byte
	UserID string
	ConnID string
}

// NewClient 创建新客户端
func NewClient(hub *Hub, conn *websocket.Conn, connID string) *Client {
	return &Client{
		Hub:    hub,
		Conn:   conn,
		Send:   make(chan []byte, sendBufferSize),
		ConnID: connID,
	}
}

// ReadPump 读取协程：从 WebSocket 连接读取消息
func (c *Client) ReadPump() {
	defer func() {
		c.Hub.unregister <- c
		c.Conn.Close()
	}()

	c.Conn.SetReadLimit(maxMessageSize)
	c.Conn.SetReadDeadline(time.Now().Add(pongWait))
	c.Conn.SetPongHandler(func(string) error {
		c.Conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		msgType, message, err := c.Conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseNormalClosure) {
				log.Printf("[Client] 读取错误: %v", err)
			}
			break
		}

		// Android 客户端发送 Base64 编码的文本帧，需要先解码
		var raw []byte
		if msgType == websocket.TextMessage {
			raw, err = base64.StdEncoding.DecodeString(string(message))
			if err != nil {
				// 尝试无填充解码
				raw, err = base64.RawStdEncoding.DecodeString(string(message))
				if err != nil {
					log.Printf("[Client] Base64 解码失败: %v", err)
					continue
				}
			}
		} else {
			raw = message
		}

		// 解码二进制协议包
		pkt, err := DecodePacket(raw)
		if err != nil {
			log.Printf("[Client] 解码失败: %v", err)
			continue
		}

		c.handlePacket(pkt)
	}
}

// WritePump 写入协程：将消息写入 WebSocket 连接
func (c *Client) WritePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.Conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.Send:
			c.Conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				// Hub 关闭了通道
				c.Conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			if err := c.Conn.WriteMessage(websocket.BinaryMessage, message); err != nil {
				log.Printf("[Client] 写入错误: %v", err)
				return
			}

		case <-ticker.C:
			c.Conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.Conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

// handlePacket 处理接收到的协议包
func (c *Client) handlePacket(pkt *model.WSPacket) {
	switch pkt.Type {
	case model.MsgTypePing:
		c.handlePing()
	case model.MsgTypeAuth:
		c.handleAuth(pkt.Payload)
	case model.MsgTypeMessage:
		c.handleMessage(pkt.Payload)
	case model.MsgTypeTyping:
		c.handleTyping(pkt.Payload)
	default:
		log.Printf("[Client] 未知消息类型: 0x%02x", pkt.Type)
	}
}

func (c *Client) handlePing() {
	select {
	case c.Send <- MakePongPacket():
	default:
	}
}

func (c *Client) handleAuth(payload []byte) {
	// 认证逻辑：payload 为 JWT token 的字节
	token := string(payload)
	if token == "" {
		c.Send <- MakeErrorPacket("认证失败：token 为空")
		return
	}

	// 验证 JWT token，提取 userID
	// 注意：实际验证由中间件完成，这里先占位
	userID := validateToken(token)
	if userID == "" {
		c.Send <- MakeErrorPacket("认证失败：无效的 token")
		return
	}

	c.UserID = userID
	c.Hub.register <- c
}

func (c *Client) handleMessage(payload []byte) {
	if c.UserID == "" {
		c.Send <- MakeErrorPacket("请先认证")
		return
	}

	// 解析消息内容
	// 格式: [会话ID(36字节)] [消息内容...]
	// 实际消息解析在应用层处理，这里只是透传
	// 将消息写入数据库并转发给会话其他成员
	_ = payload

	// TODO: 解析 payload，提取 conversation_id 和内容
	// 调用 db.SaveMessage()
	// 查询会话成员，调用 Hub.SendToUsers()
}

func (c *Client) handleTyping(payload []byte) {
	if c.UserID == "" {
		return
	}
	// 透传 typing 事件给会话其他成员
	_ = payload
}

// validateToken 验证 JWT token 并返回 userID
func validateToken(tokenStr string) string {
	if JWTSecret == "" {
		log.Println("[validateToken] JWTSecret 未配置，无法验证 token")
		return ""
	}
	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, jwt.ErrSignatureInvalid
		}
		return []byte(JWTSecret), nil
	})
	if err != nil || !token.Valid {
		log.Printf("[validateToken] token 无效: %v", err)
		return ""
	}
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return ""
	}
	sub, _ := claims["sub"].(string)
	return sub
}
