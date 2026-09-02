[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('start', 'stop', 'restart', 'status', 'logs', 'backup', 'open')]
    [string]$Action
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw '未找到 docker 命令，请先安装并启动 Docker Desktop。'
}
if (-not (Test-Path -LiteralPath '.env')) {
    throw '尚未安装，请先运行“首次安装.bat”。'
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

$composePrefix = @('compose', '--env-file', '.env', '-f', 'compose.yaml')

switch ($Action) {
    'start' {
        Invoke-Docker ($composePrefix + @('up', '-d', '--no-build', '--pull', 'never'))
        Invoke-Docker ($composePrefix + @('ps'))
        Start-Process "http://localhost:$(Read-EnvValue 'WEB_PORT')"
    }
    'stop' {
        Invoke-Docker ($composePrefix + @('stop'))
        Write-Host '系统已停止，数据库和附件数据仍保留。' -ForegroundColor Green
    }
    'restart' {
        Invoke-Docker ($composePrefix + @('up', '-d', '--force-recreate', '--no-build', '--pull', 'never'))
        Invoke-Docker ($composePrefix + @('ps'))
    }
    'status' {
        Invoke-Docker ($composePrefix + @('ps'))
    }
    'logs' {
        Invoke-Docker ($composePrefix + @('logs', '--tail', '200', '-f'))
    }
    'open' {
        Start-Process "http://localhost:$(Read-EnvValue 'WEB_PORT')"
    }
    'backup' {
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        $backupRoot = Join-Path $PSScriptRoot 'backups'
        $target = Join-Path $backupRoot $stamp
        New-Item -ItemType Directory -Path $target -Force | Out-Null

        Write-Host '正在备份数据库……' -ForegroundColor Cyan
        Invoke-Docker ($composePrefix + @('exec', '-T', 'db', 'sh', '-c', 'mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" > /tmp/bhfyzx-db.sql'))
        Invoke-Docker ($composePrefix + @('cp', 'db:/tmp/bhfyzx-db.sql', (Join-Path $target 'database.sql')))
        Invoke-Docker ($composePrefix + @('exec', '-T', 'db', 'rm', '-f', '/tmp/bhfyzx-db.sql'))

        Write-Host '正在备份报告和附件……' -ForegroundColor Cyan
        Invoke-Docker ($composePrefix + @('exec', '-T', 'api', 'sh', '-c', 'tar -czf /tmp/bhfyzx-storage.tar.gz -C /data reports radiology'))
        Invoke-Docker ($composePrefix + @('cp', 'api:/tmp/bhfyzx-storage.tar.gz', (Join-Path $target 'storage.tar.gz')))
        Invoke-Docker ($composePrefix + @('exec', '-T', 'api', 'rm', '-f', '/tmp/bhfyzx-storage.tar.gz'))

        Copy-Item -LiteralPath '.env' -Destination (Join-Path $target 'deployment.env')
        Get-ChildItem -LiteralPath $target -File | ForEach-Object {
            $hash = Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256
            "$($hash.Hash)  $($_.Name)"
        } | Set-Content -LiteralPath (Join-Path $target 'SHA256SUMS.txt') -Encoding UTF8

        $zipPath = "$target.zip"
        Compress-Archive -Path (Join-Path $target '*') -DestinationPath $zipPath -CompressionLevel Optimal
        Write-Host "备份完成：$zipPath" -ForegroundColor Green
        Write-Warning '备份中含数据库密码，请复制到安全磁盘并妥善保管。'
    }
}
