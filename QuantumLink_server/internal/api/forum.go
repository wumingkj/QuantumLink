package api

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"quantumlink-server/internal/db"
	"quantumlink-server/internal/middleware"
	"quantumlink-server/internal/model"
)

// ForumHandler 论坛相关的 HTTP 处理器
type ForumHandler struct{}

// NewForumHandler 创建 ForumHandler
func NewForumHandler() *ForumHandler {
	return &ForumHandler{}
}

// List 获取帖子列表
func (h *ForumHandler) List(w http.ResponseWriter, r *http.Request) {
	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	pageSize, _ := strconv.Atoi(r.URL.Query().Get("page_size"))

	if page <= 0 {
		page = 1
	}
	if pageSize <= 0 || pageSize > 50 {
		pageSize = 20
	}

	posts, total, err := db.ListPosts(page, pageSize)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "获取帖子列表失败"})
		return
	}
	if posts == nil {
		posts = []*model.ForumPost{}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"posts": posts,
		"total": total,
		"page":  page,
	})
}

// Create 创建帖子
func (h *ForumHandler) Create(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())

	var req model.CreatePostRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "请求格式错误"})
		return
	}

	if req.Title == "" || req.Content == "" {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "标题和内容不能为空"})
		return
	}

	post := &model.ForumPost{
		AuthorID:  userID,
		Title:     req.Title,
		Content:   req.Content,
		CreatedAt: time.Now().Unix(),
	}

	if err := db.CreatePost(post); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "发布失败"})
		return
	}

	writeJSON(w, http.StatusCreated, post)
}

// GetDetail 获取帖子详情（含回复）
func (h *ForumHandler) GetDetail(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "无效的帖子 ID"})
		return
	}

	post, err := db.GetPost(id)
	if err != nil {
		writeJSON(w, http.StatusNotFound, model.ErrorResponse{Error: "帖子不存在"})
		return
	}

	replies, err := db.GetReplies(id)
	if err != nil {
		replies = []*model.ForumReply{}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"post":    post,
		"replies": replies,
	})
}

// Like 点赞帖子
func (h *ForumHandler) Like(w http.ResponseWriter, r *http.Request) {
	idStr := chi.URLParam(r, "id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "无效的帖子 ID"})
		return
	}

	if err := db.IncrementLikeCount(id); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "点赞失败"})
		return
	}

	writeJSON(w, http.StatusOK, model.SuccessResponse{Message: "点赞成功"})
}

// Reply 回复帖子
func (h *ForumHandler) Reply(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())

	idStr := chi.URLParam(r, "id")
	postID, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "无效的帖子 ID"})
		return
	}

	var req model.CreateReplyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "请求格式错误"})
		return
	}

	if req.Content == "" {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "回复内容不能为空"})
		return
	}

	reply := &model.ForumReply{
		PostID:    postID,
		AuthorID:  userID,
		Content:   req.Content,
		CreatedAt: time.Now().Unix(),
	}

	if err := db.CreateReply(reply); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "回复失败"})
		return
	}

	// 更新帖子回复数
	db.IncrementReplyCount(postID)

	writeJSON(w, http.StatusCreated, reply)
}
