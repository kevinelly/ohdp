param([Parameter(Mandatory=$true)][string]$InstallDir, [Parameter(Mandatory=$true)][string]$DataDir,
    [string]$Installer = '')
$ErrorActionPreference = 'Stop'
$env:BHFYZX_DATA_DIR = [IO.Path]::GetFullPath($DataDir)
$launcher = Join-Path $InstallDir 'BHFYZX.exe'
$checks = [Collections.Generic.List[string]]::new()
function Assert-Check([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw "FAIL: $Message" }
    $checks.Add($Message)
    Write-Output "PASS: $Message"
}
function Wait-Ready {
    for($i=0;$i -lt 90;$i++) {
        if(Test-Path (Join-Path $DataDir 'port.txt')) {
            $script:base = 'http://127.0.0.1:' + (Get-Content (Join-Path $DataDir 'port.txt') -Raw).Trim()
            try { if((Invoke-RestMethod "$base/desktop/status" -TimeoutSec 2).status -eq 'ready'){return} } catch { }
        }
        Start-Sleep -Seconds 1
    }
    throw 'Installed application did not become ready.'
}
function Start-App {
    Start-Process -FilePath $launcher -ArgumentList '--quiet' -WindowStyle Hidden | Out-Null
    Wait-Ready
}
function Stop-App {
    $p = Start-Process -FilePath $launcher -ArgumentList '--stop' -WindowStyle Hidden -PassThru -Wait
    if($p.ExitCode -ne 0){throw 'Graceful shutdown failed.'}
}
function Api([string]$Path, [string]$Method='GET', $Body=$null) {
    $options = @{ Uri="$base$Path"; Method=$Method; TimeoutSec=100 }
    if($script:auth){$options.Headers=$script:auth}
    if($null -ne $Body){$options.Body=($Body | ConvertTo-Json -Depth 20 -Compress); $options.ContentType='application/json; charset=utf-8'}
    Invoke-RestMethod @options
}
try {
    Start-App
    $homeResponse = Invoke-WebRequest "$base/" -UseBasicParsing
    Assert-Check ($homeResponse.StatusCode -eq 200 -and $homeResponse.Content.Contains('id="app"')) 'Packaged frontend loads from bundled backend'
    foreach ($manual in @('detailed-guide.docx', 'quick-guide.docx')) {
        $download = Join-Path $DataDir $manual
        Invoke-WebRequest "$base/manuals/$manual" -OutFile $download -TimeoutSec 30
        Assert-Check ((Get-FileHash $download).Hash -eq (Get-FileHash (Join-Path $InstallDir "web/manuals/$manual")).Hash) ("Offline manual download matches bundled Word document: " + $manual)
    }
    foreach($match in [regex]::Matches($homeResponse.Content, '(?:src|href)="(/assets/[^"]+)"')) {
        $asset=Invoke-WebRequest ($base + $match.Groups[1].Value) -UseBasicParsing
        Assert-Check ($asset.StatusCode -eq 200) ('Bundled asset loads: ' + $match.Groups[1].Value)
    }
    $denied=Invoke-WebRequest "$base/desktop/shutdown" -Method Post -SkipHttpErrorCheck
    Assert-Check ($denied.StatusCode -eq 403) 'Shutdown rejects requests without launcher secret'
    $keys=Get-Content (Join-Path $DataDir 'installation.keys')
    $login=Api '/api/auth/login' 'POST' @{username='admin'; password=$keys[1]}
    Assert-Check (!$login.mustChangePassword -and !!$login.token -and $keys[1] -eq 'Test@345') 'Default administrator login with Test@345'
    $script:auth=@{Authorization=('Bearer ' + $login.token)}
    $newPassword='Acceptance-' + [Guid]::NewGuid().ToString('N')
    Api '/api/auth/change-password' 'POST' @{oldPassword=$keys[1];newPassword=$newPassword} | Out-Null
    $login=Api '/api/auth/login' 'POST' @{username='admin';password=$newPassword}
    $script:auth=@{Authorization=('Bearer ' + $login.token)}
    Assert-Check (!$login.mustChangePassword) 'Password change persists'
    $projects=Api '/api/ohdp/projects'
    Assert-Check ($projects.totalElements -eq 0) 'Fresh installation contains no business projects'
    $project=Api '/api/ohdp/projects' 'POST' @{companyName='安装包验收测试（非真实企业）';companyAddress='离线安装验证';detectType='定期检测';sampleName='测试样品'}
    Assert-Check ($project.id -gt 0) 'Business record creation works'
    $preview=Join-Path $DataDir 'acceptance-preview.docx'
    Invoke-WebRequest "$base/api/ohdp/projects/$($project.id)/report/preview-file" -Headers $script:auth -OutFile $preview -TimeoutSec 100
    Assert-Check ((Get-Item $preview).Length -gt 10000) 'Bundled report template generates DOCX'
    $originalPort=[int](Get-Content (Join-Path $DataDir 'port.txt'))
    $repeat=Start-Process -FilePath $launcher -ArgumentList '--quiet' -WindowStyle Hidden -PassThru -Wait
    Assert-Check ($repeat.ExitCode -eq 0) 'Repeated launch reuses running application'
    Stop-App
    $locked=[IO.File]::Open((Join-Path $DataDir 'database/ohdp.mv.db'), 'Open', 'ReadWrite', 'None')
    $locked.Dispose()
    Assert-Check $true 'Graceful exit flushes and releases database'
    $busy=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$originalPort)
    $busy.Start()
    try { Start-App } finally { $busy.Stop() }
    Assert-Check ([int](Get-Content (Join-Path $DataDir 'port.txt')) -ne $originalPort) 'Occupied port automatically selects an available port'
    $login=Api '/api/auth/login' 'POST' @{username='admin';password=$newPassword}
    $script:auth=@{Authorization=('Bearer ' + $login.token)}
    $saved=Api "/api/ohdp/projects/$($project.id)"
    Assert-Check ($saved.companyName -eq $project.companyName) 'Record and administrator password survive restart'
    Stop-App
    $backup=Join-Path (Split-Path $DataDir -Parent) ('acceptance-backup-' + [Guid]::NewGuid().ToString('N') + '.zip')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory($DataDir,$backup)
    $zip=[IO.Compression.ZipFile]::OpenRead($backup)
    try { Assert-Check (!!($zip.Entries | Where-Object FullName -eq 'installation.keys') -and !!($zip.Entries | Where-Object FullName -eq 'database/ohdp.mv.db')) 'Stopped data can be backed up with database credentials' } finally {$zip.Dispose()}
    $originalDataDir=$DataDir
    $DataDir=Join-Path (Split-Path $DataDir -Parent) ('还原测试-' + [Guid]::NewGuid().ToString('N'))
    [IO.Compression.ZipFile]::ExtractToDirectory($backup,$DataDir)
    $env:BHFYZX_DATA_DIR=$DataDir
    Start-App
    $login=Api '/api/auth/login' 'POST' @{username='admin';password=$newPassword}
    $script:auth=@{Authorization=('Bearer ' + $login.token)}
    Assert-Check ((Api "/api/ohdp/projects/$($project.id)").companyName -eq $project.companyName) 'Backup restores into a different data directory with working login and records'
    Stop-App
    $DataDir=$originalDataDir
    $env:BHFYZX_DATA_DIR=$DataDir
    if($Installer) {
        Start-App
        $p=Start-Process -FilePath $Installer -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/TASKS=""',('/DIR="' + $InstallDir + '"')) -WindowStyle Hidden -PassThru -Wait
        Assert-Check ($p.ExitCode -eq 0) 'Installer stops a running application before replacing binaries'
        Start-App
        $login=Api '/api/auth/login' 'POST' @{username='admin';password=$newPassword}
        $script:auth=@{Authorization=('Bearer ' + $login.token)}
        Assert-Check ((Api "/api/ohdp/projects/$($project.id)").companyName -eq $project.companyName) 'Overwrite installation preserves password and business records'
        $p=Start-Process -FilePath (Join-Path $InstallDir 'unins000.exe') -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART') -WindowStyle Hidden -PassThru -Wait
        Assert-Check ($p.ExitCode -eq 0 -and !(Test-Path $launcher)) 'Uninstaller stops the running application and removes program files'
        Assert-Check ((Test-Path (Join-Path $DataDir 'database/ohdp.mv.db')) -and (Test-Path (Join-Path $DataDir 'installation.keys')) -and (Test-Path $preview)) 'Uninstall retains database, credentials and generated report'
    }
    $checks | ConvertTo-Json | Set-Content (Join-Path (Split-Path $DataDir -Parent) 'checks.json') -Encoding utf8
} finally { if(Test-Path $launcher){Stop-App} }
