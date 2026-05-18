#!/bin/bash
# 量子飞信 服务端一键部署脚本
# 适用: Debian 13 / Ubuntu 22.04+

set -e

# ===== 配置 =====
DOMAIN="${DOMAIN:-im.example.com}"
BINARY_URL="${BINARY_URL:-}"
INSTALL_DIR="/opt/quantumlink"
DATA_DIR="/var/lib/quantumlink"
DB_PATH="${DATA_DIR}/quantumlink.db"
JWT_SECRET="${JWT_SECRET:-$(openssl rand -hex 32)}"

echo "================================================"
echo "  量子飞信 (QuantumLink) 服务端部署脚本"
echo "================================================"

# ===== 1. 安装依赖 =====
echo "[1/5] 安装系统依赖..."
apt-get update
apt-get install -y curl wget tar systemd

# ===== 2. 安装 Caddy =====
echo "[2/5] 安装 Caddy..."
if ! command -v caddy &> /dev/null; then
    sudo apt-get install -y debian-keyring debian-archive-keyring apt-transport-https
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
    apt-get update
    apt-get install -y caddy
fi

# ===== 3. 部署服务端 =====
echo "[3/5] 部署量子飞信服务端..."
mkdir -p "${INSTALL_DIR}"
mkdir -p "${DATA_DIR}"

# 如果提供了下载 URL，从 URL 下载
if [ -n "$BINARY_URL" ]; then
    wget -O "${INSTALL_DIR}/quantumlink-server" "$BINARY_URL"
    chmod +x "${INSTALL_DIR}/quantumlink-server"
else
    echo "  请手动将 quantumlink-server-linux 复制到 ${INSTALL_DIR}/"
    echo "  并执行: chmod +x ${INSTALL_DIR}/quantumlink-server"
fi

# ===== 4. 创建 systemd 服务 =====
echo "[4/5] 创建 systemd 服务..."
cat > /etc/systemd/system/quantumlink.service << 'EOF'
[Unit]
Description=QuantumLink IM Server
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/quantumlink
ExecStart=/opt/quantumlink/quantumlink-server
Restart=always
RestartSec=5
Environment="DB_PATH=/var/lib/quantumlink/quantumlink.db"
Environment="JWT_SECRET=${JWT_SECRET}"
Environment="WS_PORT=8081"
Environment="REST_PORT=8082"

[Install]
WantedBy=multi-user.target
EOF

# 替换环境变量
sed -i "s/\${JWT_SECRET}/${JWT_SECRET}/" /etc/systemd/system/quantumlink.service

# ===== 5. 配置 Caddy 反向代理 =====
echo "[5/5] 配置 Caddy..."
cat > /etc/caddy/Caddyfile << EOF
${DOMAIN} {
    reverse_proxy localhost:8082
}

im.${DOMAIN} {
    reverse_proxy localhost:8081
}
EOF

# 重启服务
systemctl daemon-reload
systemctl enable quantumlink
systemctl restart quantumlink || true
systemctl reload caddy || systemctl restart caddy

echo ""
echo "================================================"
echo "  部署完成！"
echo "================================================"
echo "  REST API:  https://${DOMAIN}"
echo "  WebSocket: wss://im.${DOMAIN}"
echo "  JWT Secret: ${JWT_SECRET}"
echo ""
echo "  管理命令:"
echo "  systemctl status quantumlink    # 查看状态"
echo "  journalctl -u quantumlink -f    # 查看日志"
echo "  systemctl restart quantumlink   # 重启服务"
echo "================================================"
