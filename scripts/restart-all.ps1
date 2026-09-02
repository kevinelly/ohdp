param(
  [int]$ApiPort = 18080,
  [int]$WebPort = 5173,
  [string]$ApiDir = 'G:\zhy\bhfyzx\ohdp-api',
  [string]$WebDir = 'G:\zhy\bhfyzx\ohdp-web',
  [string]$FallbackWebDir = 'C:\Users\Administrator\ohdp-web-1-element-test2',
  [string]$Profile = 'mysql',
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

$apiScript = Join-Path $root 'restart-api.ps1'
$webScript = Join-Path $root 'restart-web.ps1'

if (-not (Test-Path -LiteralPath $apiScript)) { throw "缺少脚本: $apiScript" }
if (-not (Test-Path -LiteralPath $webScript)) { throw "缺少脚本: $webScript" }

Write-Host '========== 重启 API =========='
$apiArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $apiScript, '-Port', $ApiPort, '-ApiDir', $ApiDir, '-Profile', $Profile)
if ($DryRun) { $apiArgs += '-DryRun' }
& powershell.exe @apiArgs

Write-Host '========== 重启 WEB =========='
$webArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $webScript, '-Port', $WebPort, '-WebDir', $WebDir, '-FallbackWebDir', $FallbackWebDir)
if ($DryRun) { $webArgs += '-DryRun' }
& powershell.exe @webArgs

Write-Host '========== 完成 =========='
