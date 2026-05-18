package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"
	"quantumlink-server/internal/db"
	"quantumlink-server/internal/model"
)

// AuthHandler 认证相关的 HTTP 处理器
type AuthHandler struct {
	jwtSecret string
}

// NewAuthHandler 创建 AuthHandler
func NewAuthHandler(jwtSecret string) *AuthHandler {
	return &AuthHandler{jwtSecret: jwtSecret}
}

// Register 注册
func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	var req model.RegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "请求格式错误"})
		return
	}

	if req.Username == "" || req.Password == "" || req.Nickname == "" {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "用户名、密码、昵称不能为空"})
		return
	}

	// 检查用户名是否已存在
	existing, _ := db.GetUserByUsername(req.Username)
	if existing != nil {
		writeJSON(w, http.StatusConflict, model.ErrorResponse{Error: "用户名已存在"})
		return
	}

	// bcrypt 加密密码（从不是明文！）
	hashed, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "密码加密失败"})
		return
	}

	now := time.Now().Unix()
	user := &model.User{
		Username:  req.Username,
		Phone:     req.Phone,
		Password:  string(hashed),
		Nickname:  req.Nickname,
		Avatar:    "",
		CreatedAt: now,
		LastSeen:  now,
	}

	userID, err := db.CreateUser(user)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "创建用户失败"})
		return
	}
	user.ID = userID

	writeJSON(w, http.StatusCreated, user)
}

// Login 登录
func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req model.LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "请求格式错误"})
		return
	}

	user, err := db.GetUserByUsername(req.Username)
	if err != nil || user == nil {
		writeJSON(w, http.StatusUnauthorized, model.ErrorResponse{Error: "用户名或密码错误"})
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(req.Password)); err != nil {
		writeJSON(w, http.StatusUnauthorized, model.ErrorResponse{Error: "用户名或密码错误"})
		return
	}

	// 生成 JWT（sub 存字符串形式的 ID）
	token, err := h.generateToken(fmt.Sprintf("%d", user.ID))
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "生成 token 失败"})
		return
	}

	// 更新最后在线时间
	db.UpdateLastSeen(user.ID, time.Now().Unix())

	writeJSON(w, http.StatusOK, model.LoginResponse{
		Token: token,
		User:  *user,
	})
}

// Refresh 刷新 token
func (h *AuthHandler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req model.TokenRefreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "请求格式错误"})
		return
	}

	token, err := h.generateTokenFromOld(req.Token)
	if err != nil {
		writeJSON(w, http.StatusUnauthorized, model.ErrorResponse{Error: "无效的 token"})
		return
	}

	writeJSON(w, http.StatusOK, map[string]string{"token": token})
}

func (h *AuthHandler) generateToken(userID string) (string, error) {
	claims := jwt.MapClaims{
		"sub": userID,
		"iat": time.Now().Unix(),
		"exp": time.Now().Add(7 * 24 * time.Hour).Unix(), // 7 天过期
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString([]byte(h.jwtSecret))
}

func (h *AuthHandler) generateTokenFromOld(oldToken string) (string, error) {
	claims := jwt.MapClaims{}
	_, _, err := new(jwt.Parser).ParseUnverified(oldToken, claims)
	if err != nil {
		return "", err
	}

	userID, ok := claims["sub"].(string)
	if !ok {
		return "", jwt.ErrInvalidKey
	}

	return h.generateToken(userID)
}
