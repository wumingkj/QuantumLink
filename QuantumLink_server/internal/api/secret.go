package api

import (
	"fmt"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"quantumlink-server/internal/db"
	"quantumlink-server/internal/middleware"
	"quantumlink-server/internal/model"
)

// SecretHandler 密信相关的 HTTP 处理器
type SecretHandler struct {
	hub interface{} // WS Hub 引用（留作扩展）
}

// NewSecretHandler 创建 SecretHandler
func NewSecretHandler() *SecretHandler {
	return &SecretHandler{}
}

// ExportMessages 导出密信会话记录（客户端在销毁前调用）
// 响应格式：JSON 数组，每条消息包含 sender、content、timestamp
// 根据中国法律要求，服务端始终保留完整记录
func (h *SecretHandler) ExportMessages(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())
	convID := chi.URLParam(r, "id")

	// 验证用户是会话成员
	members, err := db.GetConversationMembers(convID)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "获取会话信息失败"})
		return
	}

	isMember := false
	for _, m := range members {
		if m == userID {
			isMember = true
			break
		}
	}
	if !isMember {
		writeJSON(w, http.StatusForbidden, model.ErrorResponse{Error: "你不是该会话的成员"})
		return
	}

	// 获取会话详情（验证是否为密信会话）
	conv, err := db.GetConversation(convID)
	if err != nil || conv == nil {
		writeJSON(w, http.StatusNotFound, model.ErrorResponse{Error: "会话不存在"})
		return
	}

	// 获取所有消息
	messages, err := db.GetMessages(convID, time.Now().Unix()+1, 10000)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "导出失败"})
		return
	}

	// 构造导出数据
	type ExportItem struct {
		SenderID  string `json:"sender_id"`
		Content   string `json:"content"`
		MsgType   int    `json:"msg_type"`
		CreatedAt int64  `json:"created_at"`
		IsSecret  int    `json:"is_secret"`
	}

	var exportData []*ExportItem
	for _, msg := range messages {
		if msg.ConversationID == convID {
			exportData = append(exportData, &ExportItem{
				SenderID:  msg.SenderID,
				Content:   string(msg.Content),
				MsgType:   msg.MsgType,
				CreatedAt: msg.CreatedAt,
				IsSecret:  msg.IsSecret,
			})
		}
	}

	// 如果用户请求的是 txt 格式，返回纯文本
	if r.URL.Query().Get("format") == "txt" {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.Header().Set("Content-Disposition",
			fmt.Sprintf(`attachment; filename="secret_export_%s.txt"`, convID[:8]))
		for _, item := range exportData {
			t := time.Unix(item.CreatedAt, 0).Format("2006-01-02 15:04:05")
			fmt.Fprintf(w, "[%s] %s: %s\n", t, item.SenderID, item.Content)
		}
		return
	}

	// 默认返回 JSON
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Content-Disposition",
		fmt.Sprintf(`attachment; filename="secret_export_%s.json"`, convID[:8]))
	writeJSON(w, http.StatusOK, exportData)
}
