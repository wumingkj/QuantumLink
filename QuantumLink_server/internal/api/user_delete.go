package api

import (
	"net/http"

	"quantumlink-server/internal/db"
	"quantumlink-server/internal/middleware"
	"quantumlink-server/internal/model"
)

// DeleteMe 注销当前账号
// 法律合规：服务端标记删除而非物理删除，保留记录
func (h *UserHandler) DeleteMe(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r.Context())
	if userID == "" {
		writeJSON(w, http.StatusUnauthorized, model.ErrorResponse{Error: "未认证"})
		return
	}

	if err := db.DeleteUser(userID); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.ErrorResponse{Error: "注销失败"})
		return
	}

	writeJSON(w, http.StatusOK, model.SuccessResponse{Message: "账号已注销"})
}
