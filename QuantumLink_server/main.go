package main

import (
	"fmt"
	"log"
	"net"
	"net/http"
	"time"

	"github.com/gorilla/websocket"
	"quantumlink-server/config"
	"quantumlink-server/internal/api"
	"quantumlink-server/internal/db"
	"quantumlink-server/internal/ws"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  4096,
	WriteBufferSize: 4096,
	CheckOrigin: func(r *http.Request) bool {
		return true // 开发阶段允许所有来源
	},
}

func main() {
	// 加载配置
	cfg := config.Load()

	// 初始化数据库
	log.Println("正在初始化数据库...")
	if err := db.Init(cfg.DatabasePath); err != nil {
		log.Fatalf("数据库初始化失败: %v", err)
	}
	defer db.Close()
	log.Println("数据库初始化完成")

	// 打印本机 IP 地址
	printLocalIPs(cfg.RESTPort)

	// 初始化 WebSocket Hub
	hub := ws.NewHub()
	go hub.Run()

	// 设置 WS 客户端的 JWT 密钥（用于 AUTH 帧验证）
	ws.JWTSecret = cfg.JWTSecret

	// 初始化密信计时器管理器（服务端计时，到期发销毁通知）
	secretTimer := ws.NewSecretTimer(hub)

	// 启动密信过期清理协程（仅清理数据库过期标记，不删记录）
	go cleanupRoutine(cfg)

	// 创建 API 处理器
	authHandler := api.NewAuthHandler(cfg.JWTSecret)
	userHandler := api.NewUserHandler()
	contactHandler := api.NewContactHandler()
	forumHandler := api.NewForumHandler()
	vpnHandler := api.NewVPNHandler("", "", "")
	secretHandler := api.NewSecretHandler()

	// 创建 REST 路由
	router := api.NewRouter(authHandler, userHandler, contactHandler, forumHandler, vpnHandler, secretHandler)

	// 确保 secretTimer 不被 GC 回收
	_ = secretTimer

	// 创建 HTTP mux
	mux := http.NewServeMux()

	// REST API（WebSocket 已移至独立端口 1050）
	mux.Handle("/", router)

	// 启动 REST API 服务
	go func() {
		addr := fmt.Sprintf(":%s", cfg.RESTPort)
		log.Printf("REST API 服务启动于 %s", addr)
		if err := http.ListenAndServe(addr, mux); err != nil {
			log.Fatalf("REST API 服务启动失败: %v", err)
		}
	}()

	// 启动独立的 WebSocket 服务
	wsAddr := fmt.Sprintf(":%s", cfg.WSPort)
	wsMux := http.NewServeMux()
	wsMux.HandleFunc("/ws", func(w http.ResponseWriter, r *http.Request) {
		handleWebSocket(hub, w, r)
	})
	go func() {
		log.Printf("WebSocket 服务启动于 %s", wsAddr)
		if err := http.ListenAndServe(wsAddr, wsMux); err != nil {
			log.Fatalf("WebSocket 服务启动失败: %v", err)
		}
	}()

	select {}
}

// handleWebSocket 处理 WebSocket 升级请求
func handleWebSocket(hub *ws.Hub, w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("WebSocket 升级失败: %v", err)
		return
	}

	connID := fmt.Sprintf("%s-%d", r.RemoteAddr, time.Now().UnixNano())
	client := ws.NewClient(hub, conn, connID)

	// 先注册未认证的连接，等收到 AUTH 帧后更新 UserID
	hub.RegisterUnregistered(client)

	go client.WritePump()
	go client.ReadPump()
}

// cleanupRoutine 定时清理过期的密信消息
// printLocalIPs 打印本机所有非回环 IP 地址
func printLocalIPs(port string) {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		log.Printf("获取本机 IP 失败: %v", err)
		return
	}
	log.Println("========================================")
	log.Println("  量子飞信 服务端已启动")
	log.Println("  手机请填入以下 IP 地址连接:")
	for _, addr := range addrs {
		ipnet, ok := addr.(*net.IPNet)
		if !ok || ipnet.IP.IsLoopback() || ipnet.IP.To4() == nil {
			continue
		}
		log.Printf("  ▶ http://%s:%s  (ws://%s:%s/ws)", ipnet.IP.String(), port, ipnet.IP.String(), port)
	}
	log.Println("========================================")
}

func cleanupRoutine(cfg *config.Config) {
	ticker := time.NewTicker(time.Duration(cfg.CleanupInterval) * time.Minute)
	defer ticker.Stop()

	for range ticker.C {
		now := time.Now().Unix()
		deleted, err := db.DeleteExpiredSecretMessages(now)
		if err != nil {
			log.Printf("清理过期密信失败: %v", err)
			continue
		}
		if deleted > 0 {
			log.Printf("已清理 %d 条过期密信", deleted)
		}
	}
}
