param(
  [int]$Port = 5173,
  [string]$WebDir = 'G:\zhy\bhfyzx\ohdp-web',
  [string]$FallbackWebDir = 'C:\Users\Administrator\ohdp-web-1-element-test2',
  [int]$WaitSeconds = 30,
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

function Stop-PortProcess {
  param([int]$TargetPort)

  $connections = @(Get-NetTCPConnection -LocalPort $TargetPort -State Listen -ErrorAction SilentlyContinue)
  if (-not $connections -or $connections.Count -eq 0) {
    Write-Host "[WEB] 端口 $TargetPort 未被占用"
    return
  }

  $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($procId in $pids) {
    if ($DryRun) {
      Write-Host "[WEB][DryRun] 将结束进程 PID=$procId (占用端口 $TargetPort)"
      continue
    }
    try {
      Stop-Process -Id $procId -Force -ErrorAction Stop
      Write-Host "[WEB] 已结束进程 PID=$procId (占用端口 $TargetPort)"
    } catch {
      Write-Warning "[WEB] 结束 PID=$procId 失败: $($_.Exception.Message)"
    }
  }
}

function Test-WebDirRunnable {
  param([string]$Dir)
  if (-not (Test-Path -LiteralPath $Dir)) { return $false }
  $pkg = Join-Path $Dir 'package.json'
  $viteBin = Join-Path $Dir 'node_modules\vite\bin\vite.js'
  return (Test-Path -LiteralPath $pkg) -and (Test-Path -LiteralPath $viteBin)
}

$selectedDir = $null
if (Test-WebDirRunnable -Dir $WebDir) {
  $selectedDir = $WebDir
} elseif (Test-WebDirRunnable -Dir $FallbackWebDir) {
  $selectedDir = $FallbackWebDir
  Write-Warning "[WEB] 主目录不可运行，已自动切换到: $FallbackWebDir"
} else {
  throw "WEB目录不可运行，未找到 vite。请检查: $WebDir 或 $FallbackWebDir"
}

$logRoot = Split-Path $selectedDir -Parent
$webOut = Join-Path $logRoot 'dev-web.out.log'
$webErr = Join-Path $logRoot 'dev-web.err.log'

Stop-PortProcess -TargetPort $Port

if ($DryRun) {
  Write-Host "[WEB][DryRun] 启动目录: $selectedDir"
  Write-Host "[WEB][DryRun] 将执行: npm run dev"
  exit 0
}

try { Add-Content -Path $webOut -Value ("`r`n==== WEB RESTART " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + " ====") } catch {}
try { Add-Content -Path $webErr -Value ("`r`n==== WEB RESTART " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + " ====") } catch {}

$proc = Start-Process -FilePath 'npm.cmd' -ArgumentList @('run', 'dev') -WorkingDirectory $selectedDir -PassThru -RedirectStandardOutput $webOut -RedirectStandardError $webErr

Write-Host "[WEB] 启动目录: $selectedDir"
Write-Host "[WEB] 重启命令已发出，日志:"
Write-Host "  OUT: $webOut"
Write-Host "  ERR: $webErr"

for ($i = 1; $i -le $WaitSeconds; $i++) {
  Start-Sleep -Seconds 1
  $listen = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  if ($listen) {
    Write-Host "[WEB] 启动成功: http://localhost:$Port"
    exit 0
  }

  if ($proc.HasExited) {
    Write-Warning "[WEB] 启动进程提前退出，退出码: $($proc.ExitCode)"
    Write-Host "[WEB] 最近错误日志:"
    if (Test-Path $webErr) { Get-Content $webErr -Tail 30 }
    Write-Host "[WEB] 最近输出日志:"
    if (Test-Path $webOut) { Get-Content $webOut -Tail 30 }
    exit 1
  }
}

Write-Warning "[WEB] ${WaitSeconds}秒内未监听端口 $Port，请查看日志"
Write-Host "[WEB] 最近错误日志:"
if (Test-Path $webErr) { Get-Content $webErr -Tail 30 }
Write-Host "[WEB] 最近输出日志:"
if (Test-Path $webOut) { Get-Content $webOut -Tail 30 }
exit 1

