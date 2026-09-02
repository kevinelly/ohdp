param(
  [int]$Port = 18080,
  [string]$ApiDir = 'G:\zhy\bhfyzx\ohdp-api',
  [string]$Profile = 'mysql',
  [int]$WaitSeconds = 120,
  [string]$MavenSettings = 'G:\zhy\bhfyzx\scripts\maven-settings-ohdp.xml',
  [switch]$UseMaven,
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

function Stop-PortProcess {
  param([int]$TargetPort)
  $connections = @(Get-NetTCPConnection -LocalPort $TargetPort -State Listen -ErrorAction SilentlyContinue)
  if (-not $connections -or $connections.Count -eq 0) {
    Write-Host "[API] 端口 $TargetPort 未被占用"
    return
  }
  $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($procId in $pids) {
    if ($DryRun) {
      Write-Host "[API][DryRun] 将结束进程 PID=$procId (占用端口 $TargetPort)"
      continue
    }
    try {
      Stop-Process -Id $procId -Force -ErrorAction Stop
      Write-Host "[API] 已结束进程 PID=$procId (占用端口 $TargetPort)"
    } catch {
      Write-Warning "[API] 结束 PID=$procId 失败: $($_.Exception.Message)"
    }
  }
}

if (-not (Test-Path -LiteralPath $ApiDir)) {
  throw "API目录不存在: $ApiDir"
}

$rootDir = Split-Path $ApiDir -Parent
$apiOut = Join-Path $rootDir 'dev-api.out.log'
$apiErr = Join-Path $rootDir 'dev-api.err.log'

Stop-PortProcess -TargetPort $Port

$filePath = ''
$args = @()
$mode = ''

if ($UseMaven) {
  if (-not (Test-Path -LiteralPath $MavenSettings)) {
    throw "Maven settings不存在: $MavenSettings"
  }
  $mvnw = Join-Path $ApiDir 'mvnw.cmd'
  if (-not (Test-Path -LiteralPath $mvnw)) {
    throw "找不到 $mvnw"
  }
  $filePath = $mvnw
  $args = @('-s', $MavenSettings, 'spring-boot:run', "-Dspring-boot.run.profiles=$Profile")
  $mode = 'Maven'
} else {
  $jar = Get-ChildItem -Path (Join-Path $ApiDir 'target') -Filter 'ohdp-api-*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*.original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $jar) {
    throw "未找到可启动JAR，请先执行 mvnw.cmd package，或使用 -UseMaven"
  }
  $filePath = 'java'
  $args = @('-jar', $jar.FullName, "--spring.profiles.active=$Profile", "--server.port=$Port")
  $mode = 'JAR'
}

if ($DryRun) {
  Write-Host "[API][DryRun] 启动模式: $mode"
  Write-Host "[API][DryRun] 执行文件: $filePath"
  Write-Host "[API][DryRun] 参数: $($args -join ' ')"
  exit 0
}

try { Add-Content -Path $apiOut -Value ("`r`n==== API RESTART " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + " ====") } catch {}
try { Add-Content -Path $apiErr -Value ("`r`n==== API RESTART " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + " ====") } catch {}

$proc = Start-Process -FilePath $filePath -ArgumentList $args -WorkingDirectory $ApiDir -PassThru -RedirectStandardOutput $apiOut -RedirectStandardError $apiErr

Write-Host "[API] 启动模式: $mode"
Write-Host "[API] 重启命令已发出，日志:"
Write-Host "  OUT: $apiOut"
Write-Host "  ERR: $apiErr"

for ($i = 1; $i -le $WaitSeconds; $i++) {
  Start-Sleep -Seconds 1
  $listen = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  if ($listen) {
    Write-Host "[API] 启动成功: http://localhost:$Port"
    exit 0
  }

  if ($proc.HasExited) {
    Write-Warning "[API] 启动进程提前退出，退出码: $($proc.ExitCode)"
    Write-Host "[API] 最近错误日志:"
    if (Test-Path $apiErr) { Get-Content $apiErr -Tail 30 }
    Write-Host "[API] 最近输出日志:"
    if (Test-Path $apiOut) { Get-Content $apiOut -Tail 30 }
    exit 1
  }

  if ($i % 10 -eq 0) {
    Write-Host "[API] 等待启动中... ${i}s/${WaitSeconds}s"
  }
}

Write-Warning "[API] ${WaitSeconds}秒内未监听端口 $Port，请查看日志"
Write-Host "[API] 最近错误日志:"
if (Test-Path $apiErr) { Get-Content $apiErr -Tail 30 }
Write-Host "[API] 最近输出日志:"
if (Test-Path $apiOut) { Get-Content $apiOut -Tail 30 }
exit 1

