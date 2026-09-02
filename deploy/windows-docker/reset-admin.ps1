[CmdletBinding()]
param(
    [string]$Username = 'admin'
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$temporaryPassword = 'BHFYZX-Reset-2026'
$temporaryPasswordHash = '$2b$12$fh.yGq8QcbAKppoOu4nV2u8TMBeUV.cNChVzbacrbylC5RpA3toV2'

function Read-EnvValue([string]$Name) {
    $line = Get-Content -LiteralPath '.env' -Encoding UTF8 |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -First 1
    if ($null -eq $line) { return '' }
    return ($line -split '=', 2)[1]
}

if ($Username -notmatch '^[A-Za-z0-9_.-]{1,64}$') {
    throw '用户名只能包含字母、数字、下划线、点和短横线。'
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw '未找到 docker 命令，请先启动 Docker Desktop。'
}
if (-not (Test-Path -LiteralPath '.env' -PathType Leaf)) {
    throw '未找到 .env，请确认系统已经完成首次安装。'
}

$rootPassword = Read-EnvValue 'MYSQL_ROOT_PASSWORD'
$databaseName = Read-EnvValue 'MYSQL_DATABASE'
if ([string]::IsNullOrWhiteSpace($rootPassword) -or [string]::IsNullOrWhiteSpace($databaseName)) {
    throw '.env 中缺少数据库名称或 root 密码。'
}

$composePrefix = @('compose', '--env-file', '.env', '-f', 'compose.yaml')

Write-Host '正在检查本机数据库容器……' -ForegroundColor Cyan
& docker @composePrefix up -d db --no-build --pull never
if ($LASTEXITCODE -ne 0) {
    throw '无法启动本机数据库容器。'
}

$sql = "UPDATE sys_user_token t JOIN sys_user u ON t.user_id=u.id SET t.revoked_at=NOW(),t.deleted=1 WHERE u.username='$Username' AND t.deleted=0; UPDATE sys_user SET password_hash='$temporaryPasswordHash',must_change_password=1,status='ACTIVE',deleted=0,updated_at=NOW() WHERE username='$Username'; SELECT ROW_COUNT() AS affected_rows;"

Write-Host "正在无损重置管理员 $Username 的密码……" -ForegroundColor Cyan
& docker @composePrefix exec -T db mysql '--user=root' "--password=$rootPassword" $databaseName "--execute=$sql"
if ($LASTEXITCODE -ne 0) {
    throw '数据库密码重置失败，请保留窗口并将错误截图反馈。'
}

Write-Host "`n管理员密码已重置，不会影响项目、报告或附件。" -ForegroundColor Green
Write-Host "账号：$Username"
Write-Host "临时密码：$temporaryPassword" -ForegroundColor Yellow
Write-Host '登录后请立即按系统提示修改密码。'
