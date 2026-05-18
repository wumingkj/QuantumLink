package api

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"quantumlink-server/internal/db"
	"quantumlink-server/internal/middleware"
	"quantumlink-server/internal/model"
)

// UserHandler 用户相关的 HTTP 处理器
type UserHandler struct{}

// NewUserHandler 创建 UserHandler
func NewUserHandler() *UserHandler {
	return &UserHandler{}
}

// GetMe 获取当前用户信息
func (h *UserHandler) GetMe(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())
	if userID == "" {
		writeJSON(w, http.StatusUnauthorized, model.ErrorResponse{Error: "未认证"})
		return
	}

	user, err := db.GetUserByIDStr(userID)
	if err != nil || user == nil {
		writeJSON(w, http.StatusNotFound, model.ErrorResponse{Error: "用户不存在"})
		return
	}

	writeJSON(w, http.StatusOK, user)
}

// UpdateMe 更新当前用户信息
func (h *UserHandler) UpdateMe(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())
	if userID == "" {
		writeJSON(w, http.StatusUnauthorized, model.ErrorResponse{Error: "未认证"})
		return
	}

	var req model.UpdateProfileRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "请求格式错误"})
		return
	}

	user, err := db.GetUserByIDStr(userID)
	if err != nil || user == nil {
		writeJSON(w, http.StatusNotFound, model.ErrorResponse{Error: "用户不存在"})
		return
	}

	if req.Nickname != "" {
		user.Nickname = req.Nickname
	}
	if req.Avatar != "" {
		user.Avatar = req.Avatar
	}
	if req.PublicKey != "" {
		user.PublicKey = req.PublicKey
	}

	if err := db.UpdateUser(user); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "更新失败"})
		return
	}

	writeJSON(w, http.StatusOK, user)
}

// Search 搜索用户
func (h *UserHandler) Search(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("q")
	if query == "" {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "搜索关键词不能为空"})
		return
	}

	users, err := db.SearchUsers(query, 20)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "搜索失败"})
		return
	}

	if users == nil {
		users = []*model.User{}
	}

	writeJSON(w, http.StatusOK, users)
}

// GetConversations 获取用户会话列表
func (h *UserHandler) GetConversations(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())
	convs, err := db.GetUserConversations(userID)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "获取会话列表失败"})
		return
	}
	if convs == nil {
		convs = []*model.Conversation{}
	}
	writeJSON(w, http.StatusOK, convs)
}

// CreateConversation 创建会话
func (h *UserHandler) CreateConversation(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())

	var req model.CreateConversationRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "请求格式错误"})
		return
	}

	now := time.Now().Unix()
	convID := uuid.New().String()

	conv := &model.Conversation{
		ID:        convID,
		Type:      req.Type,
		Name:      req.Name,
		CreatedAt: now,
	}

	// 密信会话设置过期时间
	if req.Type == 2 {
		conv.ExpiresAt = now + 30*60 // 30 分钟后过期
	}

	if err := db.CreateConversation(conv); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "创建会话失败"})
		return
	}

	// 添加创建者
	db.AddConversationMember(convID, userID, now)
	// 添加其他成员
	for _, memberID := range req.MemberIDs {
		db.AddConversationMember(convID, memberID, now)
	}

	writeJSON(w, http.StatusCreated, conv)
}

// GetConversationDetail 获取会话详情
func (h *UserHandler) GetConversationDetail(w http.ResponseWriter, r *http.Request) {
	convID := chi.URLParam(r, "id")
	conv, err := db.GetConversation(convID)
	if err != nil || conv == nil {
		writeJSON(w, http.StatusNotFound, model.ErrorResponse{Error: "会话不存在"})
		return
	}
	writeJSON(w, http.StatusOK, conv)
}

// DeleteConversation 删除会话
func (h *UserHandler) DeleteConversation(w http.ResponseWriter, r *http.Request) {
	convID := chi.URLParam(r, "id")
	userID := middleware.GetUserID(r.Context())

	// 移除当前用户
	if err := db.RemoveConversationMember(convID, userID); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "退出会话失败"})
		return
	}

	writeJSON(w, http.StatusOK, model.SuccessResponse{Message: "已退出会话"})
}

// GetMessages 获取历史消息
func (h *UserHandler) GetMessages(w http.ResponseWriter, r *http.Request) {
	convID := chi.URLParam(r, "id")
	before := int64(0)
	limit := 50

	if b := r.URL.Query().Get("before"); b != "" {
		// 从 query 解析 before 时间戳
		_ = b
	}

	messages, err := db.GetMessages(convID, before, limit)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "获取消息失败"})
		return
	}
	if messages == nil {
		messages = []*model.Message{}
	}
	writeJSON(w, http.StatusOK, messages)
}
