param(
    [ValidateSet("all", "server", "android", "debug")]
    [string]$Target = "all"
)

$scriptRoot = $PSScriptRoot
if (-not $scriptRoot) {
    # 兼容 ISE 等老环境
    $scriptRoot = Split-Path -Parent $global:PSCommandPath
}
$serverDir = Join-Path $scriptRoot "QuantumLink_server"
$androidDir = Join-Path $scriptRoot "QuantumLink_app"

$GOPROXY = "https://goproxy.cn,direct"

function Build-Server {
    Write-Host "=== 编译服务端 (Windows) ===" -ForegroundColor Cyan
    Push-Location $serverDir
    $env:GOPROXY = $GOPROXY
    $env:GOOS = "windows"
    $env:GOARCH = "amd64"
    go build -ldflags="-s -w" -trimpath -o quantumlink-server.exe .
    Write-Host "  ✅ quantumlink-server.exe" -ForegroundColor Green
    Pop-Location
}

function Build-Server-All {
    Build-Server
    Write-Host "=== 交叉编译 Linux (amd64 + arm64) ===" -ForegroundColor Cyan
    Push-Location $serverDir
    $env:GOOS = "linux"
    $env:GOARCH = "amd64"
    go build -ldflags="-s -w" -trimpath -o quantumlink-server-linux-amd64 .
    Write-Host "  ✅ quantumlink-server-linux-amd64" -ForegroundColor Green
    $env:GOARCH = "arm64"
    go build -ldflags="-s -w" -trimpath -o quantumlink-server-linux-arm64 .
    Write-Host "  ✅ quantumlink-server-linux-arm64" -ForegroundColor Green
    $env:GOOS = ""
    $env:GOARCH = ""
    Pop-Location
}

function Build-Android {
    Write-Host "=== 编译 Android Debug APK ===" -ForegroundColor Cyan
    Push-Location $androidDir
    & ".\gradlew.bat" assembleDebug --offline
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✅ app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Green
    }
    Pop-Location
}

function Build-Android-Release {
    Write-Host "=== 编译 Android Release APK ===" -ForegroundColor Cyan
    Push-Location $androidDir
    & ".\gradlew.bat" assembleRelease --offline
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✅ app/build/outputs/apk/release/app-release-unsigned.apk" -ForegroundColor Green
    }
    Pop-Location
}

switch ($Target) {
    "server"   { Build-Server-All }
    "android"  { Build-Android-Release }
    "debug"    { Build-Server; Write-Host ""; Build-Android }
    default {
        Write-Host "========== 量子飞信 全量编译 ==========" -ForegroundColor Magenta
        Build-Server-All
        Write-Host ""
        Build-Android-Release
        Write-Host ""
        Write-Host "========== 全部编译完成 ==========" -ForegroundColor Magenta
    }
}
