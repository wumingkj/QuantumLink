package ws

import (
	"encoding/binary"
	"errors"
	"quantumlink-server/internal/model"
)

var (
	ErrInvalidPacket = errors.New("无效的数据包")
	ErrShortPacket   = errors.New("数据包太短")
)

// EncodePacket 编码二进制协议包
// 格式: [1字节类型] [4字节长度(大端)] [负载数据]
func EncodePacket(pkt *model.WSPacket) []byte {
	length := len(pkt.Payload)
	buf := make([]byte, 1+4+length)
	buf[0] = pkt.Type
	binary.BigEndian.PutUint32(buf[1:5], uint32(length))
	copy(buf[5:], pkt.Payload)
	return buf
}

// DecodePacket 解码二进制协议包
func DecodePacket(data []byte) (*model.WSPacket, error) {
	if len(data) < 5 {
		return nil, ErrShortPacket
	}

	pktType := data[0]
	length := binary.BigEndian.Uint32(data[1:5])

	if len(data) < int(5+length) {
		return nil, ErrInvalidPacket
	}

	payload := make([]byte, length)
	copy(payload, data[5:5+length])

	return &model.WSPacket{
		Type:    pktType,
		Payload: payload,
	}, nil
}

// MakeMessagePacket 构建消息包
func MakeMessagePacket(data []byte) []byte {
	return EncodePacket(&model.WSPacket{
		Type:    model.MsgTypeMessage,
		Payload: data,
	})
}

// MakePongPacket 构建心跳回复包
func MakePongPacket() []byte {
	return EncodePacket(&model.WSPacket{
		Type:    model.MsgTypePong,
		Payload: []byte{},
	})
}

// MakeOnlinePacket 构建在线状态通知包
func MakeOnlinePacket(data []byte) []byte {
	return EncodePacket(&model.WSPacket{
		Type:    model.MsgTypeOnline,
		Payload: data,
	})
}

// MakeErrorPacket 构建错误包
func MakeErrorPacket(msg string) []byte {
	return EncodePacket(&model.WSPacket{
		Type:    model.MsgTypeError,
		Payload: []byte(msg),
	})
}

// MakeSecretExpiredPacket 构建密信会话过期提示包
func MakeSecretExpiredPacket(data []byte) []byte {
	return EncodePacket(&model.WSPacket{
		Type:    model.MsgTypeSecretExpired,
		Payload: data,
	})
}

// MakeSecretDestroyPacket 构建密信销毁通知包（服务端→客户端）
// 服务端保留记录，仅通知客户端清除界面显示
// Payload 格式: [会话ID:36字节]
func MakeSecretDestroyPacket(conversationID string) []byte {
	return EncodePacket(&model.WSPacket{
		Type:    model.MsgTypeSecretDestroy,
		Payload: []byte(conversationID),
	})
}

// MakeSecretExpiryInfoPacket 构建密信过期时间信息包
// Payload 格式: [会话ID:36字节][到期时间戳:8字节BigEndian]
func MakeSecretExpiryInfoPacket(conversationID string, expiresAt int64) []byte {
	payload := make([]byte, 44)
	copy(payload[0:36], []byte(conversationID))
	binary.BigEndian.PutUint64(payload[36:44], uint64(expiresAt))
	return EncodePacket(&model.WSPacket{
		Type:    model.MsgTypeSecretExpiryInfo,
		Payload: payload,
	})
}
