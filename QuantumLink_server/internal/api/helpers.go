package api

import (
	"encoding/json"
	"net/http"
)

// writeJSON 统一的 JSON 响应写入函数
func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}
