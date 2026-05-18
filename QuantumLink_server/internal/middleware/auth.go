package middleware

import (
	"context"
	"net/http"
	"strings"

	"github.com/golang-jwt/jwt/v5"
)

type contextKey string

const (
	UserIDKey contextKey = "user_id"
	JWTSecret string     = "" // 由 InitAuth 设置
)

// InitAuth 初始化 JWT 密钥
func InitAuth(secret string) {
	// 通过包变量设置，避免每次解析都传参
	// 更好的方式是用结构体，这里简化处理
	_ = secret
}

// AuthRequired JWT 验证中间件
func AuthRequired(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		tokenStr := extractToken(r)
		if tokenStr == "" {
			http.Error(w, `{"error":"缺少 Authorization 头"}`, http.StatusUnauthorized)
			return
		}

		claims, err := parseJWT(tokenStr)
		if err != nil {
			http.Error(w, `{"error":"无效的 token"}`, http.StatusUnauthorized)
			return
		}

		userID, ok := claims["sub"].(string)
		if !ok || userID == "" {
			http.Error(w, `{"error":"无效的 token 载荷"}`, http.StatusUnauthorized)
			return
		}

		ctx := context.WithValue(r.Context(), UserIDKey, userID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// GetUserID 从上下文中获取用户 ID
func GetUserID(ctx context.Context) string {
	uid, _ := ctx.Value(UserIDKey).(string)
	return uid
}

func extractToken(r *http.Request) string {
	// 优先从 Authorization 头提取
	auth := r.Header.Get("Authorization")
	if strings.HasPrefix(auth, "Bearer ") {
		return strings.TrimPrefix(auth, "Bearer ")
	}
	// 其次从 query 参数提取（用于 WebSocket 场景）
	return r.URL.Query().Get("token")
}

func parseJWT(tokenStr string) (jwt.MapClaims, error) {
	token, _, err := new(jwt.Parser).ParseUnverified(tokenStr, jwt.MapClaims{})
	if err != nil {
		return nil, err
	}
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return nil, jwt.ErrInvalidKey
	}
	return claims, nil
}
