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

$health = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/healthz" -TimeoutSec 15
$index = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/" -TimeoutSec 15
$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" -ContentType 'application/json' -Body (@{
    username = $credentials.ADMIN_USERNAME
    password = $credentials.ADMIN_INITIAL_PASSWORD
} | ConvertTo-Json) -TimeoutSec 20
if (-not $login.token) { throw 'Public login did not return an access token.' }
$headers = @{ Authorization = "Bearer $($login.token)" }

$catalog = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/radiology/catalog/CT" -Headers $headers -TimeoutSec 20
$ctdi = $catalog | Where-Object code -EQ 'CTDI' | Select-Object -First 1
$fieldCodes = @($ctdi.fields | ForEach-Object code)
$requiredFields = @(
    'CTDI-HEAD-MANUFACTURER', 'CTDI-BODY-MANUFACTURER',
    'CTDI-HEAD-BASELINE', 'CTDI-BODY-BASELINE'
)
foreach ($field in $requiredFields) {
    if ($field -notin $fieldCodes) { throw "Online CTDI field is missing: $field" }
}

$caseId = $null
$calculationVersion = $null
$annualDoseResults = $null
try {
    $caseCode = 'PUBLIC-SMOKE-' + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $created = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/radiology/cases" -Headers $headers -ContentType 'application/json' -Body (@{
        caseCode = $caseCode
        modality = 'DR'
        inspectedUnitName = 'Public API smoke test (deleted automatically)'
        equipmentName = 'DR public smoke-test device'
    } | ConvertTo-Json) -TimeoutSec 20
    $caseId = $created.id
    $calculationVersion = $created.calculationVersion

    $pointBody = @{
        points = @(
            @{
                pointCode = 'RPD-A'; pointName = 'Annual-dose qualified point'
                readings = @(30000, 30000, 30000, 30000, 30000)
                annualWorkloadMas = 1000000; occupancyFactor = 1; useFactor = 1; sortNo = 1
            },
            @{
                pointCode = 'RPD-B'; pointName = 'Annual-dose unqualified point'
                readings = @(30000, 30000, 30000, 30000, 30000)
                annualWorkloadMas = 4000000; occupancyFactor = 1; useFactor = 1; sortNo = 2
            }
        )
    } | ConvertTo-Json -Depth 6
    $points = Invoke-RestMethod -Method Put -Uri "$baseUrl/api/radiology/cases/$caseId/protection-points" -Headers $headers -ContentType 'application/json' -Body $pointBody -TimeoutSec 20
    $annualDoseResults = @(
        foreach ($point in $points) {
            [pscustomobject]@{
                rate = $point.resultValue
                annualDose = $point.annualEffectiveDose
                judgement = $point.judgement
            }
        }
    )
    if ($annualDoseResults[0].judgement -ne 'QUALIFIED' -or $annualDoseResults[1].judgement -ne 'UNQUALIFIED') {
        throw ('Unexpected public DR annual-dose judgement: ' + ($annualDoseResults | ConvertTo-Json -Compress))
    }
} finally {
    if ($null -ne $caseId) {
        Invoke-RestMethod -Method Delete -Uri "$baseUrl/api/radiology/cases/$caseId" -Headers $headers -TimeoutSec 20
    }
}

$remaining = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/radiology/cases?keyword=PUBLIC-SMOKE-&page=0&size=20" -Headers $headers -TimeoutSec 20
if ($remaining.totalElements -ne 0) { throw 'Public smoke-test case cleanup failed.' }

[pscustomobject]@{
    PublicHealthStatus = $health.StatusCode
    PublicIndexStatus = $index.StatusCode
    Login = 'OK'
    CalculationVersion = $calculationVersion
    CtdiProfileFields = 'OK'
    AnnualDoseResults = ($annualDoseResults | ConvertTo-Json -Compress)
    TestDataCleanup = 'DONE'
}
