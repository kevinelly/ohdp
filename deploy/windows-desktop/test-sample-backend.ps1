param([string]$JavaHome = 'C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$testRoot=Join-Path $root ('tmp/sample-api-acceptance-'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start()
$port=$listener.LocalEndpoint.Port
$listener.Stop()
$base='http://127.0.0.1:'+ $port
$token=[Guid]::NewGuid().ToString('N')
$env:BHFYZX_DATA_HOME=$testRoot.Replace('\','/')
$env:BHFYZX_DATABASE_PASSWORD=[Guid]::NewGuid().ToString('N')
$env:BHFYZX_INSTANCE_TOKEN=$token
$env:BHFYZX_SAMPLE_ZIP=Join-Path $root 'output/windows-desktop-sample/online-sample.zip'
$env:BHFYZX_WEB_URI=([Uri]((Join-Path $root 'ohdp-web/dist')+'\')).AbsoluteUri
$env:BHFYZX_PORT=$port.ToString()
$config=([Uri](Join-Path $PSScriptRoot 'application-desktop.properties')).AbsoluteUri
$arguments=@('-Dfile.encoding=UTF-8','-jar',('"'+(Join-Path $root 'ohdp-api/target/ohdp-api-0.0.1-SNAPSHOT.jar')+'"'),'--spring.profiles.active=desktop',('--spring.config.additional-location="'+$config+'"'))
$checks=[Collections.Generic.List[string]]::new()
function Assert-Check([bool]$condition,[string]$message) {
    if(!$condition){throw "FAIL: $message"}
    $checks.Add($message)
    Write-Output "PASS: $message"
}
function Start-TestServer {
    $script:server=Start-Process -FilePath (Join-Path $JavaHome 'bin/java.exe') -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testRoot ('stdout-'+[Guid]::NewGuid().ToString('N')+'.log')) -RedirectStandardError (Join-Path $testRoot ('stderr-'+[Guid]::NewGuid().ToString('N')+'.log'))
    for($i=0;$i -lt 90;$i++) {
        try {if((Invoke-RestMethod "$base/desktop/status" -TimeoutSec 2).status -eq 'ready'){return}} catch {}
        if($server.HasExited){throw 'Backend exited before becoming ready'}
        Start-Sleep -Seconds 1
    }
    throw 'Backend startup timeout'
}
function Stop-TestServer {
    if($server -and !$server.HasExited) {
        Invoke-RestMethod "$base/desktop/shutdown" -Method Post -Headers @{'X-Desktop-Token'=$token} | Out-Null
        if(!$server.WaitForExit(45000)){throw 'Backend shutdown timeout'}
    }
}
function Sample([string]$action) {
    Invoke-RestMethod "$base/desktop/sample-data/$action" -Method Post -Headers @{'X-Desktop-Token'=$token} -TimeoutSec 120
}
function Login([string]$password) {
    Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body (@{username='admin';password=$password} | ConvertTo-Json)
}
try {
    Start-TestServer
    $login=Login 'Test@345'
    Assert-Check (!!$login.token -and !$login.mustChangePassword) 'Administrator can log in directly with Test@345'
    $auth=@{Authorization=('Bearer '+$login.token)}
    $denied=Invoke-WebRequest "$base/desktop/sample-data/import" -Method Post -SkipHttpErrorCheck
    Assert-Check ($denied.StatusCode -eq 403) 'Test data import requires launcher token'
    $first=Sample 'import'
    $summary=Get-Content (Join-Path $root 'output/windows-desktop-sample/online-sample.summary.json') -Raw | ConvertFrom-Json
    Assert-Check ($first.projects -eq $summary.activeProjects -and $first.cases -eq $summary.activeCases) 'Online snapshot active project and radiology counts match'
    $repeat=Sample 'import'
    Assert-Check ($repeat.rows -eq $first.rows) 'Repeated import is idempotent'
    $laws=Invoke-RestMethod "$base/api/ohdp/basic/standards?size=100" -Headers $auth
    $law=@($laws.content | Where-Object fileId)[0]
    if(!$law){throw 'Imported law document not found'}
    $file=Invoke-WebRequest "$base/api/ohdp/basic/documents/$($law.fileId)/content" -Headers $auth
    Assert-Check ($file.StatusCode -eq 200 -and $file.RawContentLength -gt 0) 'Imported reference document can be downloaded through application API'
    $project=Invoke-RestMethod "$base/api/ohdp/projects" -Method Post -Headers $auth -ContentType 'application/json; charset=utf-8' -Body (@{companyName='独立录入-安装验收';companyAddress='测试地址';detectType='定期检测'} | ConvertTo-Json)
    Assert-Check ($project.id -gt 0) 'Independent business record can be created alongside samples'
    $removed=Sample 'delete'
    Assert-Check (!$removed.imported -and $removed.pendingFiles -eq 0) 'All tracked sample rows and files can be deleted'
    $remaining=Invoke-RestMethod "$base/api/ohdp/projects?size=100" -Headers $auth
    Assert-Check ($remaining.totalElements -eq 1 -and $remaining.content[0].id -eq $project.id) 'Deleting samples preserves independently entered business records'
    $second=Sample 'import'
    Assert-Check ($second.projects -eq $summary.activeProjects -and $second.cases -eq $summary.activeCases) 'Sample data can be reimported after deletion'
    Sample 'delete' | Out-Null
    $newPassword='Acceptance-'+[Guid]::NewGuid().ToString('N')
    Invoke-RestMethod "$base/api/auth/change-password" -Method Post -Headers $auth -ContentType 'application/json' -Body (@{oldPassword='Test@345';newPassword=$newPassword} | ConvertTo-Json) | Out-Null
    Stop-TestServer
    Start-TestServer
    $changed=Login $newPassword
    Assert-Check (!!$changed.token) 'User-changed password is not reset on later startups'
    $status=Invoke-RestMethod "$base/desktop/sample-data" -Headers @{'X-Desktop-Token'=$token}
    Assert-Check (!$status.imported) 'Deleted samples remain deleted after restart'
    $checks | ConvertTo-Json | Set-Content (Join-Path $testRoot 'checks.json') -Encoding utf8
    Write-Output ('Evidence: '+$testRoot)
} finally { Stop-TestServer }
