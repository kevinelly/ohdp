[CmdletBinding()]
param(
    [string]$PreviousDirectory = 'R:\BHFYZX-Windows-Docker-Offline-V1.0.5\deploy\windows-docker'
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$offlineArchive = Join-Path $PSScriptRoot 'images\bhfyzx-offline-images.tar.gz'
$offlineHashFile = "$offlineArchive.sha256"
$requiredImages = @('mysql:8.4', 'bhfyzx-api:1.0.6', 'bhfyzx-web:1.0.6')

function Write-Step([string]$Text) {
    Write-Host "`n==> $Text" -ForegroundColor Cyan
}

function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker 命令执行失败：docker $($Arguments -join ' ')" }
}

Write-Host 'BHFYZX Windows Docker 原数据升级程序 V1.0.6' -ForegroundColor Green
Write-Host '升级会保留 MySQL、报告和附件数据卷，只替换 API 与 Web 镜像。'

Write-Step '取得原安装密码配置'
if (-not (Test-Path -LiteralPath '.env' -PathType Leaf)) {
    $previousEnv = Join-Path $PreviousDirectory '.env'
    if (-not (Test-Path -LiteralPath $previousEnv -PathType Leaf)) {
        throw "找不到原安装 .env：$previousEnv。请用 -PreviousDirectory 指定原部署目录。"
    }
    Copy-Item -LiteralPath $previousEnv -Destination '.env'
    Write-Host "已从原部署目录复制 .env：$previousEnv" -ForegroundColor Green
} else {
    Write-Host '当前目录已有 .env，将继续使用现有密码和端口。'
}

Write-Step '校验离线镜像包'
if (-not (Test-Path -LiteralPath $offlineArchive -PathType Leaf)) { throw "缺少离线镜像：$offlineArchive" }
if (-not (Test-Path -LiteralPath $offlineHashFile -PathType Leaf)) { throw "缺少镜像校验文件：$offlineHashFile" }
$expectedHash = ((Get-Content -LiteralPath $offlineHashFile -Encoding ASCII | Select-Object -First 1) -split '\s+')[0].Trim().ToUpperInvariant()
$actualHash = (Get-FileHash -LiteralPath $offlineArchive -Algorithm SHA256).Hash.ToUpperInvariant()
if ($expectedHash -ne $actualHash) { throw "离线镜像校验失败。期望：$expectedHash；实际：$actualHash" }

Write-Step '检查 Docker Desktop'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw '未找到 docker 命令，请先启动 Docker Desktop。' }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop 尚未就绪。' }

Write-Step '导入 V1.0.6 本地镜像'
Invoke-Docker @('load', '--input', $offlineArchive)
foreach ($image in $requiredImages) {
    & docker image inspect $image *> $null
    if ($LASTEXITCODE -ne 0) { throw "离线镜像导入后仍缺少：$image" }
}

Write-Step '保留数据卷并重建 API、Web 容器'
$composePrefix = @('compose', '--env-file', '.env', '-f', 'compose.yaml')
Invoke-Docker ($composePrefix + @('config', '--quiet'))
Invoke-Docker ($composePrefix + @('up', '-d', '--no-build', '--pull', 'never'))

Write-Step '等待系统恢复服务'
$webPortLine = Get-Content -LiteralPath '.env' -Encoding UTF8 | Where-Object { $_ -match '^WEB_PORT=' } | Select-Object -First 1
$webPort = if ($webPortLine) { [int](($webPortLine -split '=', 2)[1]) } else { 8088 }
$url = "http://localhost:$webPort"
$ready = $false
for ($attempt = 1; $attempt -le 90; $attempt++) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$url/healthz" -TimeoutSec 3
        if ($response.StatusCode -eq 200) { $ready = $true; break }
    } catch {
        Start-Sleep -Seconds 2
    }
}

Invoke-Docker ($composePrefix + @('ps'))
if (-not $ready) { throw '容器已经重建，但网页未在 3 分钟内恢复，请运行“查看日志.bat”。' }

Write-Host "`n升级完成：$url" -ForegroundColor Green
Write-Host '原数据库、报告和附件数据卷均已保留。'
