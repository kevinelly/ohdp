$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw '请使用管理员权限运行此脚本。'
}
if (-not (Test-Path -LiteralPath '.env')) {
    throw '尚未安装，请先运行“首次安装.bat”。'
}

$line = Get-Content -LiteralPath '.env' -Encoding UTF8 |
    Where-Object { $_ -match '^WEB_PORT=' } |
    Select-Object -First 1
$port = [int](($line -split '=', 2)[1])
$displayName = "BHFYZX Web $port"
$existing = Get-NetFirewallRule -DisplayName $displayName -ErrorAction SilentlyContinue
if ($existing) {
    Set-NetFirewallRule -DisplayName $displayName -Enabled True -Profile Domain,Private
} else {
    $ruleParameters = @{
        DisplayName   = $displayName
        Direction     = 'Inbound'
        Action        = 'Allow'
        Protocol      = 'TCP'
        LocalPort     = $port
        Profile       = @('Domain', 'Private')
        RemoteAddress = 'LocalSubnet'
    }
    New-NetFirewallRule @ruleParameters | Out-Null
}

$ipv4 = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
    Select-Object -First 1 -ExpandProperty IPAddress
Write-Host "已允许局域网访问：http://${ipv4}:$port" -ForegroundColor Green
Write-Host '仅允许 Domain/Private 网络中的本地子网访问，API 和 MySQL 端口没有对外开放。'
