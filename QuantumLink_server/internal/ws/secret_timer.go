package ws

import (
	"log"
	"sync"
	"time"
)

// SecretTimer 密信会话计时器
// 服务端管理每个密信会话的销毁计时
// 到期后发 SECRET_DESTROY 通知给客户端，但服务端保留记录

type SecretTimer struct {
	hub        *Hub
	mu         sync.Mutex
	timers     map[string]*time.Timer // conversationID → timer
	stopChs    map[string]chan struct{}
}

// NewSecretTimer 创建密信计时器管理器
func NewSecretTimer(hub *Hub) *SecretTimer {
	return &SecretTimer{
		hub:     hub,
		timers:  make(map[string]*time.Timer),
		stopChs: make(map[string]chan struct{}),
	}
}

// StartTimer 启动密信会话计时器
// duration: 倒计时时长
// memberIDs: 会话成员列表（用于通知）
func (st *SecretTimer) StartTimer(conversationID string, duration time.Duration, memberIDs []string, expiresAt int64) {
	st.mu.Lock()
	defer st.mu.Unlock()

	// 如果已存在计时器，先停止
	if oldTimer, ok := st.timers[conversationID]; ok {
		oldTimer.Stop()
		if ch, ok := st.stopChs[conversationID]; ok {
			close(ch)
		}
	}

	stopCh := make(chan struct{})
	st.stopChs[conversationID] = stopCh

	log.Printf("[SecretTimer] 密信会话 %s 计时器启动: %v", conversationID, duration)

	timer := time.AfterFunc(duration, func() {
		st.mu.Lock()
		delete(st.timers, conversationID)
		delete(st.stopChs, conversationID)
		st.mu.Unlock()

		log.Printf("[SecretTimer] 密信会话 %s 到期，发送销毁通知", conversationID)

		// 发送销毁通知给所有成员
		destroyData := MakeSecretDestroyPacket(conversationID)
		for _, uid := range memberIDs {
			st.hub.SendToUser(uid, destroyData)
		}
	})

	st.timers[conversationID] = timer
}

// StopTimer 停止密信会话计时器（例如会话提前结束后）
func (st *SecretTimer) StopTimer(conversationID string) {
	st.mu.Lock()
	defer st.mu.Unlock()

	if timer, ok := st.timers[conversationID]; ok {
		timer.Stop()
		delete(st.timers, conversationID)
		if ch, ok := st.stopChs[conversationID]; ok {
			close(ch)
			delete(st.stopChs, conversationID)
		}
		log.Printf("[SecretTimer] 密信会话 %s 计时器已停止", conversationID)
	}
}

// ResetTimer 重置密信会话计时器（有新消息时调用）
func (st *SecretTimer) ResetTimer(conversationID string, duration time.Duration, memberIDs []string, expiresAt int64) {
	log.Printf("[SecretTimer] 密信会话 %s 计时器重置: %v", conversationID, duration)
	st.StartTimer(conversationID, duration, memberIDs, expiresAt)
}
