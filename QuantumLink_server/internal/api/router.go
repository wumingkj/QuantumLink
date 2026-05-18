package api

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	chimw "github.com/go-chi/chi/v5/middleware"
	"quantumlink-server/internal/middleware"
)

// NewRouter 创建并配置 REST API 路由
func NewRouter(authHandler *AuthHandler, userHandler *UserHandler,
	contactHandler *ContactHandler, forumHandler *ForumHandler,
	vpnHandler *VPNHandler, secretHandler *SecretHandler) http.Handler {

	r := chi.NewRouter()

	// 全局中间件
	r.Use(chimw.Logger)
	r.Use(chimw.Recoverer)
	r.Use(chimw.RequestID)
	r.Use(middleware.CORS)

	// 健康检查
	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(`{"status":"ok"}`))
	})

	// ===== 认证（无需鉴权） =====
	r.Route("/api/auth", func(r chi.Router) {
		r.Post("/register", authHandler.Register)
		r.Post("/login", authHandler.Login)
		r.Post("/refresh", authHandler.Refresh)
	})

	// ===== 需要 JWT 鉴权的接口 =====
	r.Group(func(r chi.Router) {
		r.Use(middleware.AuthRequired)

		// 用户
		r.Route("/api/users", func(r chi.Router) {
			r.Get("/me", userHandler.GetMe)
			r.Put("/me", userHandler.UpdateMe)
			r.Delete("/me", userHandler.DeleteMe)
			r.Get("/search", userHandler.Search)
		})

		// 联系人
		r.Route("/api/contacts", func(r chi.Router) {
			r.Get("/", contactHandler.List)
			r.Post("/", contactHandler.Add)
			r.Delete("/{id}", contactHandler.Remove)
		})

		// 会话
		r.Route("/api/conversations", func(r chi.Router) {
			r.Get("/", userHandler.GetConversations)
			r.Post("/", userHandler.CreateConversation)
			r.Get("/{id}", userHandler.GetConversationDetail)
			r.Delete("/{id}", userHandler.DeleteConversation)
			r.Get("/{id}/messages", userHandler.GetMessages)
			r.Get("/{id}/export", secretHandler.ExportMessages) // 密信导出
		})

		// 论坛
		r.Route("/api/posts", func(r chi.Router) {
			r.Get("/", forumHandler.List)
			r.Post("/", forumHandler.Create)
			r.Get("/{id}", forumHandler.GetDetail)
			r.Post("/{id}/like", forumHandler.Like)
			r.Post("/{id}/reply", forumHandler.Reply)
		})

		// VPN
		r.Route("/api/vpn", func(r chi.Router) {
			r.Get("/config", vpnHandler.GetConfig)
			r.Post("/pubkey", vpnHandler.UploadPubKey)
		})
	})

	return r
}
