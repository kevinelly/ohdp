param([Parameter(Mandatory=$true)][string]$PayloadDir)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$testRoot=Join-Path $root ('tmp/launcher-options-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
$app=Join-Path $testRoot '自选 安装路径'
New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
Copy-Item -LiteralPath (Resolve-Path $PayloadDir).Path -Destination $app -Recurse
$launcher=Join-Path $app 'BHFYZX.exe'
$checks=[Collections.Generic.List[string]]::new()
function Stop-TestLauncher {
    $stop=Start-Process -FilePath $launcher -ArgumentList '--stop' -WindowStyle Hidden -PassThru -Wait
    if($stop.ExitCode -ne 0){throw 'Launcher did not stop gracefully'}
}
function Run-Choice([string]$dataPath,[bool]$choice,[int]$expected) {
    $env:BHFYZX_DATA_DIR=$dataPath
    $request=[Guid]::NewGuid().ToString('N')
    @('[Setup]',('ImportSample='+[int]$choice),('RequestId='+$request)) | Set-Content -LiteralPath (Join-Path $app 'installation.ini') -Encoding ascii
    $run=Start-Process -FilePath $launcher -ArgumentList '--quiet' -WindowStyle Hidden -PassThru
    try {
        $complete=$false
        for($i=0;$i -lt 120;$i++) {
            $marker=Join-Path $dataPath 'last-install-request.txt'
            if((Test-Path -LiteralPath $marker) -and (Get-Content -LiteralPath $marker -Raw).Trim() -eq $request){$complete=$true;break}
            if($run.HasExited){throw 'Launcher exited before installation choices were applied'}
            Start-Sleep -Seconds 1
        }
        if(!$complete){throw 'Installation choice was not applied'}
        $port=(Get-Content (Join-Path $dataPath 'port.txt') -Raw).Trim()
        $login=Invoke-RestMethod "http://127.0.0.1:$port/api/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"Test@345"}'
        $projects=Invoke-RestMethod "http://127.0.0.1:$port/api/ohdp/projects" -Headers @{Authorization=('Bearer '+$login.token)}
        if($projects.totalElements -ne $expected){throw "Expected $expected projects, got $($projects.totalElements)"}
        $message="ImportSample=$choice, project count=$expected, default admin login and custom Unicode install path passed"
        $checks.Add($message)
        Write-Output ('PASS: '+$message)
    } finally {Stop-TestLauncher}
}
$summary=Get-Content (Join-Path $app 'online-sample.summary.json') -Raw | ConvertFrom-Json
Run-Choice (Join-Path $testRoot '默认导入 数据') $true $summary.activeProjects
Run-Choice (Join-Path $testRoot '取消导入 数据') $false 0
Run-Choice (Join-Path $testRoot '默认导入 数据') $false $summary.activeProjects
$checks | ConvertTo-Json | Set-Content (Join-Path $testRoot 'checks.json') -Encoding utf8
Write-Output ('Evidence: '+$testRoot)
