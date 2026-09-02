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

$health = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/healthz" -TimeoutSec 15
$index = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/" -TimeoutSec 15
$login = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/auth/login" -ContentType 'application/json' -Body (@{
    username = $credentials.ADMIN_USERNAME
    password = $credentials.ADMIN_INITIAL_PASSWORD
} | ConvertTo-Json) -TimeoutSec 20
if (-not $login.token) { throw '公网管理员登录未返回令牌' }
$headers = @{ Authorization = "Bearer $($login.token)" }

$catalog = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/radiology/catalog/CT" -Headers $headers -TimeoutSec 20
$ctdi = $catalog | Where-Object code -EQ 'CTDI' | Select-Object -First 1
$fieldCodes = @($ctdi.fields | ForEach-Object code)
$requiredFields = @(
    'CTDI-HEAD-MANUFACTURER', 'CTDI-BODY-MANUFACTURER',
    'CTDI-HEAD-BASELINE', 'CTDI-BODY-BASELINE'
)
foreach ($field in $requiredFields) {
    if ($field -notin $fieldCodes) { throw "线上缺少 CTDI 字段：$field" }
}

$caseId = $null
$calculationVersion = $null
$annualDoseResults = $null
try {
    $caseCode = 'PUBLIC-SMOKE-' + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $created = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/radiology/cases" -Headers $headers -ContentType 'application/json' -Body (@{
        caseCode = $caseCode
        modality = 'DR'
        inspectedUnitName = '公网自动化验证（完成后删除）'
        equipmentName = 'DR 公网验证设备'
    } | ConvertTo-Json) -TimeoutSec 20
    $caseId = $created.id
    $calculationVersion = $created.calculationVersion

    $pointBody = @{
        points = @(
            @{
                pointCode = 'RPD-A'; pointName = '年剂量合格点'
                readings = @(30000, 30000, 30000, 30000, 30000)
                annualWorkloadMas = 1000000; occupancyFactor = 1; useFactor = 1; sortNo = 1
            },
            @{
                pointCode = 'RPD-B'; pointName = '年剂量不合格点'
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
        throw ('公网 DR 年剂量判定不符合预期：' + ($annualDoseResults | ConvertTo-Json -Compress))
    }
} finally {
    if ($null -ne $caseId) {
        Invoke-RestMethod -Method Delete -Uri "$baseUrl/api/radiology/cases/$caseId" -Headers $headers -TimeoutSec 20
    }
}

$remaining = Invoke-RestMethod -Method Get -Uri "$baseUrl/api/radiology/cases?keyword=PUBLIC-SMOKE-&page=0&size=20" -Headers $headers -TimeoutSec 20
if ($remaining.totalElements -ne 0) { throw '公网测试案例清理失败' }

[pscustomobject]@{
    PublicHealthStatus = $health.StatusCode
    PublicIndexStatus = $index.StatusCode
    Login = 'OK'
    CalculationVersion = $calculationVersion
    CtdiProfileFields = 'OK'
    AnnualDoseResults = ($annualDoseResults | ConvertTo-Json -Compress)
    TestDataCleanup = 'DONE'
}
