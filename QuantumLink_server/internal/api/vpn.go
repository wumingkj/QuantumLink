package api

import (
	"encoding/json"
	"net/http"

	"quantumlink-server/internal/db"
	"quantumlink-server/internal/middleware"
	"quantumlink-server/internal/model"
)

// VPNHandler VPN 相关的 HTTP 处理器
type VPNHandler struct {
	// VPN 服务器配置
	serverPublicKey string
	serverEndpoint  string
	serverAddress   string
}

// NewVPNHandler 创建 VPNHandler
func NewVPNHandler(serverPublicKey, serverEndpoint, serverAddress string) *VPNHandler {
	return &VPNHandler{
		serverPublicKey: serverPublicKey,
		serverEndpoint:  serverEndpoint,
		serverAddress:   serverAddress,
	}
}

// VPNConfig VPN 配置响应
type VPNConfig struct {
	ClientAddress   string `json:"client_address"`
	ServerPublicKey string `json:"server_public_key"`
	ServerEndpoint  string `json:"server_endpoint"`
	ServerAddress   string `json:"server_address"`
	DNS             string `json:"dns"`
}

// GetConfig 获取用户 VPN 配置
func (h *VPNHandler) GetConfig(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())

	user, err := db.GetUserByIDStr(userID)
	if err != nil || user == nil {
		writeJSON(w, http.StatusNotFound, model.ErrorResponse{Error: "用户不存在"})
		return
	}

	// 生成客户端地址（基于用户 ID 的后几位）
	clientAddr := generateClientAddress(userID)

	config := VPNConfig{
		ClientAddress:   clientAddr,
		ServerPublicKey: h.serverPublicKey,
		ServerEndpoint:  h.serverEndpoint,
		ServerAddress:   h.serverAddress,
		DNS:             "1.1.1.1",
	}

	writeJSON(w, http.StatusOK, config)
}

// UploadPubKey 上传客户端公钥
func (h *VPNHandler) UploadPubKey(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())

	var req model.UploadPubKeyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "请求格式错误"})
		return
	}

	if req.PublicKey == "" {
		writeJSON(w, http.StatusBadRequest, model.ErrorResponse{Error: "公钥不能为空"})
		return
	}

	user, err := db.GetUserByIDStr(userID)
	if err != nil || user == nil {
		writeJSON(w, http.StatusNotFound, model.ErrorResponse{Error: "用户不存在"})
		return
	}

	user.PublicKey = req.PublicKey
	if err := db.UpdateUser(user); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "更新公钥失败"})
		return
	}

	writeJSON(w, http.StatusOK, model.SuccessResponse{Message: "公钥上传成功"})
}

// generateClientAddress 为用户生成一个 WireGuard 客户端地址
func generateClientAddress(userID string) string {
	// 简单实现：取 UUID 的后 8 位作为 IP 的后两段
	// 实际部署时需要更完善的 IP 分配管理
	if len(userID) < 8 {
		return "10.0.0.2/32"
	}
	// 使用 hash 或简单映射
	hash := 0
	for _, c := range userID[len(userID)-8:] {
		hash = (hash + int(c)) % 253
	}
	return "10.0.0." + itoa(hash+2) + "/32"
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	var buf [12]byte
	i := len(buf)
	for n > 0 {
		i--
		buf[i] = byte('0' + n%10)
		n /= 10
	}
	return string(buf[i:])
}
