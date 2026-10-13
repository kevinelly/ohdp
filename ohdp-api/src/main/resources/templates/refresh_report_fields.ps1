param([Parameter(Mandatory = $true)][string]$DocumentPath)

$ErrorActionPreference = 'Stop'
$application = $null
$document = $null
$refreshedPath = $DocumentPath + '.refreshed.docx'
$saved = $false
$existingWpsIds = @(Get-Process -Name 'wps' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)

try {
    try {
        $application = New-Object -ComObject 'kwps.Application'
    } catch {
        $application = New-Object -ComObject 'Word.Application'
    }
    $application.Visible = $false
    $application.DisplayAlerts = 0
    $document = $application.Documents.Open($DocumentPath, $false, $false)

    $document.Fields.Update() | Out-Null
    foreach ($section in $document.Sections) {
        foreach ($header in $section.Headers) {
            $header.Range.Fields.Update() | Out-Null
        }
        foreach ($footer in $section.Footers) {
            $footer.Range.Fields.Update() | Out-Null
        }
    }
    $document.SaveAs2($refreshedPath, 12)
    $saved = $true
} finally {
    if ($null -ne $document) {
        $document.Close($false)
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($document)
    }
    if ($null -ne $application) {
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($application)
    }
}

if ($saved) {
    Move-Item -LiteralPath $refreshedPath -Destination $DocumentPath -Force
} elseif (Test-Path -LiteralPath $refreshedPath) {
    Remove-Item -LiteralPath $refreshedPath -Force
}

[GC]::Collect()
[GC]::WaitForPendingFinalizers()
Get-Process -Name 'wps' -ErrorAction SilentlyContinue |
    Where-Object { $existingWpsIds -notcontains $_.Id } |
    Stop-Process -Force -ErrorAction SilentlyContinue
