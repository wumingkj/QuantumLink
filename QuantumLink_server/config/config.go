package config

import (
	"os"
	"strconv"
)

type Config struct {
	// 服务端口
	WSPort      string
	RESTPort    string
	DatabasePath string
	// JWT
	JWTSecret string
	// 密信过期时间（分钟）
	SecretExpireMinutes int
	// 定时清理间隔（分钟）
	CleanupInterval int
}

func Load() *Config {
	return &Config{
	WSPort:             getEnv("WS_PORT", "1050"),
	RESTPort:           getEnv("REST_PORT", "1051"),
		DatabasePath:       getEnv("DB_PATH", "quantumlink.db"),
		JWTSecret:          getEnv("JWT_SECRET", "quantumlink-change-me-in-production"),
		SecretExpireMinutes: getEnvInt("SECRET_EXPIRE_MIN", 30),
		CleanupInterval:    getEnvInt("CLEANUP_INTERVAL_MIN", 5),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			return i
		}
	}
	return fallback
}
