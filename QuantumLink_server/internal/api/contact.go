package api

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"quantumlink-server/internal/db"
	"quantumlink-server/internal/middleware"
	"quantumlink-server/internal/model"
)

// ContactHandler 联系人相关的 HTTP 处理器
type ContactHandler struct{}

// NewContactHandler 创建 ContactHandler
func NewContactHandler() *ContactHandler {
	return &ContactHandler{}
}

// List 获取联系人列表
func (h *ContactHandler) List(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())

	// 通过子查询获取联系人的详细信息
	rows, err := db.DB.Query(
		`SELECT u.id, u.username, u.nickname, u.avatar, u.public_key, u.created_at, u.last_seen, c.added_at
		FROM contacts c
		JOIN users u ON c.contact_id = u.id
		WHERE c.user_id = ?
		ORDER BY c.added_at DESC`, userID,
	)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "获取联系人列表失败"})
		return
	}
	defer rows.Close()

	type ContactWithUser struct {
		model.User
		AddedAt int64 `json:"added_at"`
	}

	var contacts []*ContactWithUser
	for rows.Next() {
		c := &ContactWithUser{}
		if err := rows.Scan(&c.ID, &c.Username, &c.Nickname, &c.Avatar,
			&c.PublicKey, &c.CreatedAt, &c.LastSeen, &c.AddedAt); err != nil {
			writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "数据读取错误"})
			return
		}
		contacts = append(contacts, c)
	}
	if contacts == nil {
		contacts = []*ContactWithUser{}
	}

	writeJSON(w, http.StatusOK, contacts)
}

// Add 添加联系人
func (h *ContactHandler) Add(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())

	var req model.AddContactRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "请求格式错误"})
		return
	}

	// 检查目标用户是否存在
	target, _ := db.GetUserByIDStr(req.ContactID)
	if target == nil {
		writeJSON(w, http.StatusNotFound, model.ErrorResponse{Error: "用户不存在"})
		return
	}

	// 不能添加自己为联系人（userID 是 JWT 中的字符串 ID）
	if req.ContactID == userID {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "不能添加自己为联系人"})
		return
	}

	contact := &model.Contact{
		UserID:    userID,
		ContactID: req.ContactID,
		AddedAt:   time.Now().Unix(),
	}

	// 双向添加联系人
	if err := db.CreateContact(contact); err != nil {
		writeJSON(w, http.StatusConflict, model.ErrorResponse{Error: "联系人已存在"})
		return
	}

	// 添加反向关系
	db.CreateContact(&model.Contact{
		UserID:    req.ContactID,
		ContactID: userID,
		AddedAt:   time.Now().Unix(),
	})

	writeJSON(w, http.StatusCreated, contact)
}

// Remove 删除联系人
func (h *ContactHandler) Remove(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())
	contactID := chi.URLParam(r, "id")

	if err := db.DeleteContact(userID, contactID); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "删除联系人失败"})
		return
	}

	// 同时删除反向关系
	db.DeleteContact(contactID, userID)

	writeJSON(w, http.StatusOK, model.SuccessResponse{Message: "已删除联系人"})
}
