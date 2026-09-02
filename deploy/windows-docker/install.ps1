[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)]
    [int]$WebPort = 8088,
    [switch]$NoOpen
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$offlineArchive = Join-Path $PSScriptRoot 'images\bhfyzx-offline-images.tar.gz'
$offlineHashFile = "$offlineArchive.sha256"
$requiredImages = @(
    'mysql:8.4'
    'bhfyzx-api:1.0.6'
    'bhfyzx-web:1.0.6'
)

function Write-Step([string]$Text) {
    Write-Host "`n==> $Text" -ForegroundColor Cyan
}

function New-RandomPassword([int]$Length = 24) {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $Length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}

function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker 命令执行失败：docker $($Arguments -join ' ')"
    }
}

function Read-EnvValue([string]$Name) {
    $line = Get-Content -LiteralPath '.env' -Encoding UTF8 |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -First 1
    if ($null -eq $line) { return '' }
    return ($line -split '=', 2)[1]
}

Write-Host 'BHFYZX Windows Docker 完全离线安装程序 V1.0.6' -ForegroundColor Green
Write-Host '安装过程不会访问 Docker Hub、npm、Maven 或任何云端服务。'
Write-Host 'Java、Node.js、Nginx 和 MySQL 均已封装在本地 Linux 镜像中。'

Write-Step '检查离线镜像包'
if (-not (Test-Path -LiteralPath $offlineArchive -PathType Leaf)) {
    throw "离线镜像文件不存在：$offlineArchive。请确认压缩包已完整解压，不能只复制 BAT 文件。"
}
if (-not (Test-Path -LiteralPath $offlineHashFile -PathType Leaf)) {
    throw "镜像校验文件不存在：$offlineHashFile。请重新复制完整离线部署包。"
}
$expectedHash = ((Get-Content -LiteralPath $offlineHashFile -Encoding ASCII | Select-Object -First 1) -split '\s+')[0].Trim().ToUpperInvariant()
if ($expectedHash -notmatch '^[0-9A-F]{64}$') {
    throw '离线镜像校验文件格式错误，请重新复制完整离线部署包。'
}
$archiveSizeGB = (Get-Item -LiteralPath $offlineArchive).Length / 1GB
Write-Host ('正在校验 {0:N2} GB 的离线镜像文件，请稍候……' -f $archiveSizeGB)
$actualHash = (Get-FileHash -LiteralPath $offlineArchive -Algorithm SHA256).Hash.ToUpperInvariant()
if ($actualHash -ne $expectedHash) {
    throw "离线镜像文件校验失败。期望：$expectedHash；实际：$actualHash。文件可能未复制完整，请重新解压。"
}
Write-Host '离线镜像包校验通过。' -ForegroundColor Green

Write-Step '检查 Docker Desktop'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw '未找到 docker 命令。请先离线安装 Docker Desktop 并重新登录 Windows，然后再运行本脚本。'
}
& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Desktop 尚未启动，或 Linux containers 未就绪。请启动 Docker Desktop 并等待 Engine running。'
}
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
    throw '未检测到 Docker Compose。请安装包含 Compose 的 Docker Desktop。'
}
$dockerOs = (& docker info --format '{{.OSType}}').Trim()
if ($LASTEXITCODE -ne 0 -or $dockerOs -ne 'linux') {
    throw '当前不是 Linux containers 模式。请把 Docker Desktop 切换为 Linux containers。'
}

Write-Step '检查端口与磁盘空间'
$listener = Get-NetTCPConnection -State Listen -LocalPort $WebPort -ErrorAction SilentlyContinue
if ($listener) {
    throw "端口 $WebPort 已被占用。请关闭占用程序，或使用：powershell -ExecutionPolicy Bypass -File .\install.ps1 -WebPort 其他端口"
}
$driveName = ([System.IO.Path]::GetPathRoot($PSScriptRoot)).TrimEnd('\').TrimEnd(':')
$drive = Get-PSDrive -Name $driveName -ErrorAction SilentlyContinue
if ($drive -and $drive.Free -lt 10GB) {
    throw ('部署包所在盘仅剩 {0:N1} GB，请至少释放到 10 GB 后再安装。' -f ($drive.Free / 1GB))
} elseif ($drive -and $drive.Free -lt 100GB) {
    Write-Warning ('当前盘剩余 {0:N1} GB；可完成安装，但建议业务数据盘长期保留至少 100 GB。' -f ($drive.Free / 1GB))
}

Write-Step '生成本机专用密码'
if (-not (Test-Path -LiteralPath '.env')) {
    $lines = @(
        "WEB_PORT=$WebPort"
        'MYSQL_DATABASE=ohdp'
        'MYSQL_USER=ohdp'
        "MYSQL_PASSWORD=$(New-RandomPassword)"
        "MYSQL_ROOT_PASSWORD=$(New-RandomPassword 32)"
        'ADMIN_USERNAME=admin'
        "ADMIN_INITIAL_PASSWORD=$(New-RandomPassword 16)"
        'TZ=Asia/Shanghai'
    )
    [System.IO.File]::WriteAllLines(
        (Join-Path $PSScriptRoot '.env'),
        $lines,
        (New-Object System.Text.UTF8Encoding($false))
    )
    Write-Host '已生成 .env。请妥善保管；该文件包含数据库和初始管理员密码。' -ForegroundColor Yellow
} else {
    Write-Host '检测到现有 .env，将保留原密码和端口。'
    $WebPort = [int](Read-EnvValue 'WEB_PORT')
}

$composePrefix = @('compose', '--env-file', '.env', '-f', 'compose.yaml')

Write-Step '校验本机部署配置'
Invoke-Docker ($composePrefix + @('config', '--quiet'))

Write-Step '从本地文件导入三个离线镜像（不连接网络）'
Invoke-Docker @('load', '--input', $offlineArchive)
foreach ($image in $requiredImages) {
    & docker image inspect $image *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "离线镜像导入后仍缺少：$image。请重新复制完整部署包后重试。"
    }
}

Write-Step '启动 MySQL、API、Web 三个本机容器'
Invoke-Docker ($composePrefix + @('up', '-d', '--no-build', '--pull', 'never'))

Write-Step '等待系统就绪'
$url = "http://localhost:$WebPort"
$ready = $false
for ($attempt = 1; $attempt -le 90; $attempt++) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$url/healthz" -TimeoutSec 3
        if ($response.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}

Invoke-Docker ($composePrefix + @('ps'))
if (-not $ready) {
    Write-Warning '容器已经启动，但网页尚未在 3 分钟内响应。请运行“查看日志.bat”检查。'
    exit 2
}

$adminUser = Read-EnvValue 'ADMIN_USERNAME'
$adminPassword = Read-EnvValue 'ADMIN_INITIAL_PASSWORD'
Write-Host "`n完全离线安装完成。" -ForegroundColor Green
Write-Host "访问地址：$url"
Write-Host "管理员账号：$adminUser"
Write-Host "初始密码：$adminPassword" -ForegroundColor Yellow
Write-Host '系统运行、数据库、报告及附件全部位于本机，不依赖外网。'
Write-Host '首次登录后系统会要求修改密码。'

if (-not $NoOpen) {
    Start-Process $url
}
