.PHONY: build build-linux clean run

# 默认: 编译当前平台
build:
	go build -o quantumlink-server .

# 交叉编译: Windows → Linux (amd64 + arm64)
build-linux:
	GOOS=linux GOARCH=amd64 go build -o quantumlink-server-linux-amd64 .
	GOOS=linux GOARCH=arm64 go build -o quantumlink-server-linux-arm64 .

# 清理
clean:
	rm -f quantumlink-server
	rm -f quantumlink-server-linux-*
	rm -f quantumlink.exe
	rm -f quantumlink.db

# 开发运行
run:
	go run .

# 下载依赖
deps:
	go mod tidy

# 编译所有平台
build-all:
	GOOS=linux GOARCH=amd64 go build -o quantumlink-server-linux .
	GOOS=linux GOARCH=arm64 go build -o quantumlink-server-linux-arm64 .
	GOOS=windows GOARCH=amd64 go build -o quantumlink-server.exe .
	GOOS=darwin GOARCH=amd64 go build -o quantumlink-server-macos .
