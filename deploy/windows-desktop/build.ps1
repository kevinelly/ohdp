param(
    [string]$Version = '1.0.12',
    [string]$JavaHome = '',
    [string]$InnoCompiler = '',
    [string]$RuntimeImage = '',
    [switch]$SkipApplicationBuild
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$JavaHome) { $JavaHome = Split-Path (Split-Path (Get-Command javac.exe -ErrorAction Stop).Source -Parent) -Parent }
if (!(Test-Path (Join-Path $JavaHome 'bin/jlink.exe'))) { throw 'Java 17 JDK with jlink is required.' }
if (!$InnoCompiler) { $InnoCompiler = Join-Path $root 'tmp/windows-installer-tools/inno/ISCC.exe' }
if (!(Test-Path $InnoCompiler)) { throw 'Install Inno Setup 6, then pass -InnoCompiler <path to ISCC.exe>.' }
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Version must be three numeric components.' }
$env:JAVA_HOME = $JavaHome

if (!$SkipApplicationBuild) {
    Push-Location (Join-Path $root 'ohdp-web')
    try { & npm.cmd run build; if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' } }
    finally { Pop-Location }
    Push-Location (Join-Path $root 'ohdp-api')
    try { & .\mvnw.cmd -s ../scripts/maven-settings-ohdp.xml package; if ($LASTEXITCODE -ne 0) { throw 'Backend build/tests failed.' } }
    finally { Pop-Location }
}

# Every run uses a new directory, avoiding stale binaries and destructive cleanup.
$manualHashes = @{}
foreach ($manual in @('detailed-guide.docx', 'quick-guide.docx')) {
    $sourceManual = Join-Path $root ('ohdp-web/public/manuals/' + $manual)
    $builtManual = Join-Path $root ('ohdp-web/dist/manuals/' + $manual)
    if (!(Test-Path -LiteralPath $builtManual)) { throw "Missing bundled manual: $manual" }
    $manualHashes[$manual] = (Get-FileHash -LiteralPath $builtManual -Algorithm SHA256).Hash
    if ($manualHashes[$manual] -ne (Get-FileHash -LiteralPath $sourceManual -Algorithm SHA256).Hash) { throw "Stale bundled manual: $manual" }
}
$build = Join-Path $root ('tmp/windows-desktop-build/' + $Version + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$payload = Join-Path $build 'payload'
$output = Join-Path $root 'output/installers'
New-Item -ItemType Directory -Force -Path $payload, $output | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'output/windows-desktop-sample/online-sample.zip') -Destination $payload
Copy-Item -LiteralPath (Join-Path $root 'output/windows-desktop-sample/online-sample.summary.json') -Destination $payload
Copy-Item -LiteralPath (Join-Path $root 'ohdp-api/target/ohdp-api-0.0.1-SNAPSHOT.jar') -Destination (Join-Path $payload 'app.jar')
Copy-Item -LiteralPath (Join-Path $root 'ohdp-web/dist') -Destination (Join-Path $payload 'web') -Recurse
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'application-desktop.properties'), (Join-Path $PSScriptRoot '使用说明.txt') -Destination $payload
if ($RuntimeImage) {
    Copy-Item -LiteralPath (Resolve-Path $RuntimeImage).Path -Destination (Join-Path $payload 'runtime') -Recurse
} else {
    & (Join-Path $JavaHome 'bin/jlink.exe') --add-modules ALL-MODULE-PATH --strip-debug --no-header-files --no-man-pages --compress=2 --output (Join-Path $payload 'runtime')
    if ($LASTEXITCODE -ne 0) { throw 'Runtime build failed.' }
}
Copy-Item -LiteralPath (Join-Path $JavaHome 'NOTICE') -Destination (Join-Path $payload 'runtime/NOTICE')
$csc = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
& $csc /nologo /target:winexe /platform:x64 /optimize+ /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.Web.Extensions.dll /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll ('/out:' + (Join-Path $payload 'BHFYZX.exe')) (Join-Path $PSScriptRoot 'Launcher.cs')
if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed.' }
@{
    version = $Version
    builtAt = (Get-Date).ToString('o')
    dataIncluded = $true
    sampleSnapshot = (Get-Content (Join-Path $payload 'online-sample.summary.json') -Raw | ConvertFrom-Json)
    architecture = 'windows-x64'
    backendSha256 = (Get-FileHash (Join-Path $payload 'app.jar') -Algorithm SHA256).Hash
    manuals = $manualHashes
    runtimeSource = (Get-Content (Join-Path $JavaHome 'release') -Raw)
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $payload 'build-manifest.json') -Encoding UTF8
& $InnoCompiler /Qp ('/DPayloadDir=' + $payload) ('/DOutputDir=' + $output) ('/DAppVersion=' + $Version) (Join-Path $PSScriptRoot 'installer.iss')
if ($LASTEXITCODE -ne 0) { throw 'Installer compilation failed.' }
$installer = Join-Path $output "BHFYZX-Setup-$Version-x64.exe"
$hash = (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash
"$hash  $([IO.Path]::GetFileName($installer))" | Set-Content -LiteralPath ($installer + '.sha256') -Encoding ASCII
Copy-Item -LiteralPath (Join-Path $payload '使用说明.txt') -Destination $output -Force
[PSCustomObject]@{ Installer = $installer; Bytes = (Get-Item $installer).Length; SHA256 = $hash; Payload = $payload } | Format-List
