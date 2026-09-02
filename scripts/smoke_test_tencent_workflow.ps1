[CmdletBinding()]
param(
    [string]$Server = '101.33.226.22',
    [string]$KeyPath = '',
    [int]$Port = 8088
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($KeyPath)) {
    $KeyPath = (Get-ChildItem -LiteralPath 'S:\keys' -Filter '*101.33.226.22.pem' -File |
        Select-Object -First 1 -ExpandProperty FullName)
}
if ([string]::IsNullOrWhiteSpace($KeyPath)) { throw 'Tencent Cloud SSH key was not found.' }
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
if ($LASTEXITCODE -ne 0) { throw 'Unable to obtain online administrator test credentials.' }
$credentials = @{}
foreach ($line in $credentialLines) {
    $name, $value = $line -split '=', 2
    $credentials[$name] = $value
}

$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" -ContentType 'application/json' -Body (@{
    username = $credentials.ADMIN_USERNAME
    password = $credentials.ADMIN_INITIAL_PASSWORD
} | ConvertTo-Json) -TimeoutSec 20
if (-not $login.token) { throw 'Online login did not return an access token.' }
$headers = @{ Authorization = "Bearer $($login.token)" }
$unknownLabelPrefix = -join @([char]0x672A, [char]0x77E5, [char]0xFF08)

$reportCenter = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/reports?page=0&size=50" -Headers $headers -TimeoutSec 20
if ($reportCenter.totalElements -lt 1) { throw 'The report center returned no existing projects.' }
foreach ($row in $reportCenter.content) {
    if ([string]::IsNullOrWhiteSpace($row.projectStatusLabel) -or [string]::IsNullOrWhiteSpace($row.reportStatusLabel) -or
        $row.projectStatusLabel.StartsWith($unknownLabelPrefix) -or $row.reportStatusLabel.StartsWith($unknownLabelPrefix)) {
        throw "The report center contains an untranslated status: $($row.projectCode)"
    }
}

$devices = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/devices?page=0&size=100" -Headers $headers -TimeoutSec 20
$hazards = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/hazard-factors?page=0&size=100" -Headers $headers -TimeoutSec 20
$tasks = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/lab/tasks?page=0&size=100" -Headers $headers -TimeoutSec 20
$laws = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/basic/laws?page=0&size=100" -Headers $headers -TimeoutSec 20
$standards = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/basic/standards?page=0&size=100" -Headers $headers -TimeoutSec 20
foreach ($law in $laws.content) {
    if ([string]::IsNullOrWhiteSpace($law.levelLabel) -or $law.levelLabel.StartsWith($unknownLabelPrefix)) { throw "A law contains an untranslated level: $($law.id)" }
}
foreach ($standard in $standards.content) {
    if ([string]::IsNullOrWhiteSpace($standard.levelLabel) -or $standard.levelLabel.StartsWith($unknownLabelPrefix)) { throw "A standard contains an untranslated level: $($standard.id)" }
}
foreach ($device in $devices.content) {
    if ([string]::IsNullOrWhiteSpace($device.deviceTypeLabel) -or [string]::IsNullOrWhiteSpace($device.statusLabel) -or
        $device.deviceTypeLabel.StartsWith($unknownLabelPrefix) -or $device.statusLabel.StartsWith($unknownLabelPrefix)) {
        throw "A device contains an untranslated code: $($device.id)"
    }
}
foreach ($hazard in $hazards.content) {
    if ([string]::IsNullOrWhiteSpace($hazard.factorCategoryLabel) -or $hazard.factorCategoryLabel.StartsWith($unknownLabelPrefix)) { throw "A hazard contains an untranslated category: $($hazard.id)" }
}
foreach ($task in $tasks.content) {
    if ([string]::IsNullOrWhiteSpace($task.taskStatusLabel) -or $task.taskStatusLabel.StartsWith($unknownLabelPrefix)) { throw "A lab task contains an untranslated status: $($task.id)" }
}

$lawId = $null
$downloadPath = Join-Path ([System.IO.Path]::GetTempPath()) ("ohdp-reference-" + [guid]::NewGuid() + '.pdf')
try {
    $stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $title = "[SMOKE TEST] Reference document version flow-$stamp"
    $law = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/ohdp/basic/laws" -Headers $headers -ContentType 'application/json' -Body (@{
        title = $title
        docNo = "AUTO-$stamp"
        level = 'NATIONAL'
        publisher = 'Automated acceptance test'
        isValid = $true
        isOpen = $false
        notes = 'Soft-deleted automatically after the test'
    } | ConvertTo-Json) -TimeoutSec 20
    $lawId = $law.id

    $fixture = Join-Path $PSScriptRoot 'fixtures\reference-smoke.pdf'
    $curlBase = @('-sS', '-X', 'POST', '-H', "Authorization: Bearer $($login.token)")
    $firstText = & curl.exe @curlBase "$baseUrl/api/ohdp/basic/LAW/$lawId/documents" '-F' 'versionLabel=Initial release' '-F' 'sourceUrl=https://example.test/official' '-F' "file=@$fixture;type=application/pdf"
    if ($LASTEXITCODE -ne 0) { throw 'First reference-document upload failed.' }
    $first = $firstText | ConvertFrom-Json
    $secondText = & curl.exe @curlBase "$baseUrl/api/ohdp/basic/LAW/$lawId/documents" '-F' 'versionLabel=Reviewed revision' '-F' "file=@$fixture;type=application/pdf"
    if ($LASTEXITCODE -ne 0) { throw 'Second reference-document upload failed.' }
    $second = $secondText | ConvertFrom-Json

    $versions = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/ohdp/basic/LAW/$lawId/documents" -Headers $headers -TimeoutSec 20
    if ($versions.Count -ne 2 -or -not $versions[0].current -or $versions[0].id -ne $second.id) {
        throw ('Unexpected reference-document version order/current flag: ' + ($versions | ConvertTo-Json -Depth 5 -Compress))
    }
    if ($first.sha256.Length -ne 64 -or $second.sha256.Length -ne 64) { throw 'Reference document is missing a SHA-256 digest.' }

    Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$baseUrl/api/ohdp/basic/documents/$($second.id)/content?download=true" -Headers $headers -OutFile $downloadPath -TimeoutSec 20
    $downloadHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $downloadPath).Hash.ToLowerInvariant()
    if ($downloadHash -ne $second.sha256) { throw 'Downloaded reference-document SHA-256 does not match.' }
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
