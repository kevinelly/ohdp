[CmdletBinding()]
param(
    [string]$Server = '101.33.226.22',
    [string]$KeyPath = 'S:\keys\gz-腾讯云服务器私钥101.33.226.22.pem',
    [int]$Port = 8088
)

$ErrorActionPreference = 'Stop'
$baseUrl = "http://${Server}:$Port"
$sshOptions = @(
    '-i', $KeyPath,
    '-o', 'BatchMode=yes',
    '-o', 'ConnectTimeout=10',
    '-o', 'StrictHostKeyChecking=no',
    '-o', 'UserKnownHostsFile=NUL',
    "root@$Server"
)

$credentialLines = & ssh @sshOptions "grep -E '^ADMIN_(USERNAME|INITIAL_PASSWORD)=' /opt/bhfyzx/shared/.env"
if ($LASTEXITCODE -ne 0) { throw '无法取得线上管理员测试凭据' }
$credentials = @{}
foreach ($line in $credentialLines) {
    $name, $value = $line -split '=', 2
    $credentials[$name] = $value
}

$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" -ContentType 'application/json' -Body (@{
    username = $credentials.ADMIN_USERNAME
    password = $credentials.ADMIN_INITIAL_PASSWORD
} | ConvertTo-Json) -TimeoutSec 20
if (-not $login.token) { throw '管理员登录未返回令牌' }
$headers = @{ Authorization = "Bearer $($login.token)" }

$reportCenter = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/reports?page=0&size=50" -Headers $headers -TimeoutSec 20
if ($reportCenter.totalElements -lt 1) { throw '报告中心未返回已有项目' }
foreach ($row in $reportCenter.content) {
    if ([string]::IsNullOrWhiteSpace($row.projectStatusLabel) -or [string]::IsNullOrWhiteSpace($row.reportStatusLabel) -or
        $row.projectStatusLabel.StartsWith('未知（') -or $row.reportStatusLabel.StartsWith('未知（')) {
        throw "报告中心存在未翻译状态：$($row.projectCode)"
    }
}

$devices = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/devices?page=0&size=100" -Headers $headers -TimeoutSec 20
$hazards = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/hazard-factors?page=0&size=100" -Headers $headers -TimeoutSec 20
$tasks = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/lab/tasks?page=0&size=100" -Headers $headers -TimeoutSec 20
$laws = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/basic/laws?page=0&size=100" -Headers $headers -TimeoutSec 20
$standards = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/basic/standards?page=0&size=100" -Headers $headers -TimeoutSec 20
foreach ($law in $laws.content) {
    if ([string]::IsNullOrWhiteSpace($law.levelLabel) -or $law.levelLabel.StartsWith('未知（')) { throw "法规存在未翻译级别：$($law.id)" }
}
foreach ($standard in $standards.content) {
    if ([string]::IsNullOrWhiteSpace($standard.levelLabel) -or $standard.levelLabel.StartsWith('未知（')) { throw "标准存在未翻译级别：$($standard.id)" }
}
foreach ($device in $devices.content) {
    if ([string]::IsNullOrWhiteSpace($device.deviceTypeLabel) -or [string]::IsNullOrWhiteSpace($device.statusLabel) -or
        $device.deviceTypeLabel.StartsWith('未知（') -or $device.statusLabel.StartsWith('未知（')) {
        throw "设备存在未翻译代码：$($device.id)"
    }
}
foreach ($hazard in $hazards.content) {
    if ([string]::IsNullOrWhiteSpace($hazard.factorCategoryLabel) -or $hazard.factorCategoryLabel.StartsWith('未知（')) { throw "危害因素存在未翻译分类：$($hazard.id)" }
}
foreach ($task in $tasks.content) {
    if ([string]::IsNullOrWhiteSpace($task.taskStatusLabel) -or $task.taskStatusLabel.StartsWith('未知（')) { throw "实验任务存在未翻译状态：$($task.id)" }
}

$lawId = $null
$downloadPath = Join-Path ([System.IO.Path]::GetTempPath()) ("ohdp-reference-" + [guid]::NewGuid() + '.pdf')
try {
    $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $title = "[自动验收] 法规原文版本链路-$stamp"
    $law = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/ohdp/basic/laws" -Headers $headers -ContentType 'application/json' -Body (@{
        title = $title
        docNo = "AUTO-$stamp"
        level = 'NATIONAL'
        publisher = '自动化验收程序'
        isValid = $true
        isOpen = $false
        notes = '测试完成后已在业务层软删除'
    } | ConvertTo-Json) -TimeoutSec 20
    $lawId = $law.id

    $fixture = Join-Path $PSScriptRoot 'fixtures\reference-smoke.pdf'
    $curlBase = @('-sS', '-X', 'POST', '-H', "Authorization: Bearer $($login.token)")
    $firstText = & curl.exe @curlBase "$baseUrl/api/ohdp/basic/LAW/$lawId/documents" '-F' 'versionLabel=首次发布版' '-F' 'sourceUrl=https://example.test/official' '-F' "file=@$fixture;type=application/pdf"
    if ($LASTEXITCODE -ne 0) { throw '法规原文第一版上传失败' }
    $first = $firstText | ConvertFrom-Json
    $secondText = & curl.exe @curlBase "$baseUrl/api/ohdp/basic/LAW/$lawId/documents" '-F' 'versionLabel=复核修订版' '-F' "file=@$fixture;type=application/pdf"
    if ($LASTEXITCODE -ne 0) { throw '法规原文第二版上传失败' }
    $second = $secondText | ConvertFrom-Json

    $versions = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/basic/LAW/$lawId/documents" -Headers $headers -TimeoutSec 20
    if ($versions.Count -ne 2 -or -not $versions[0].current -or $versions[0].id -ne $second.id) {
        throw ('法规原文版本顺序或当前版本标记不正确：' + ($versions | ConvertTo-Json -Depth 5 -Compress))
    }
    if ($first.sha256.Length -ne 64 -or $second.sha256.Length -ne 64) { throw '法规原文缺少 SHA-256 校验值' }

    Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$baseUrl/api/ohdp/basic/documents/$($second.id)/content?download=true" -Headers $headers -OutFile $downloadPath -TimeoutSec 20
    $downloadHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $downloadPath).Hash.ToLowerInvariant()
    if ($downloadHash -ne $second.sha256) { throw '法规原文下载内容与服务端 SHA-256 不一致' }
} finally {
    if ($null -ne $lawId) {
        Invoke-RestMethod -Method Delete -Uri "$baseUrl/api/ohdp/basic/laws/$lawId" -Headers $headers -TimeoutSec 20
    }
    if (Test-Path -LiteralPath $downloadPath) { Remove-Item -LiteralPath $downloadPath -Force }
}

[pscustomobject]@{
    Login = 'OK'
    ReportCenterProjects = $reportCenter.totalElements
    ReportCenterChineseLabels = 'OK'
    DeviceRows = $devices.totalElements
    HazardRows = $hazards.totalElements
    LabTaskRows = $tasks.totalElements
    LawRows = $laws.totalElements
    StandardRows = $standards.totalElements
    ReferenceDocumentVersions = 2
    ReferenceDownloadHash = 'OK'
    VisibleTestDataCleanup = 'DONE'
}
