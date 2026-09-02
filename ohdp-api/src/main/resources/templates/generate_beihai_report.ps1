param(
    [Parameter(Mandatory = $true)][string]$templatePath,
    [Parameter(Mandatory = $true)][string]$outputPath,
    [Parameter(Mandatory = $true)][string]$jsonPath
)

$ErrorActionPreference = 'Stop'

function As-Array($value) {
    if ($null -eq $value) { return @() }
    if ($value -is [array]) { return $value }
    return @($value)
}

function Text-Value($value, [string]$fallback = '') {
    if ($null -eq $value) { return $fallback }
    $text = [string]$value
    if ([string]::IsNullOrWhiteSpace($text)) { return $fallback }
    return $text
}

function Set-Cell($table, [int]$row, [int]$column, $value) {
    try {
        $range = $table.Cell($row, $column).Range
        $range.Text = Text-Value $value
        $range.Font.Color = 0
    } catch {}
}

function Clear-DataRows($table, [int]$headerRows) {
    if ($table.Rows.Count -le $headerRows) { return }
    try {
        # 合并单元格表格不能逐行删除，但可以一次删除整个数据区。
        $dataRange = $table.Range.Duplicate
        $dataRange.Start = $table.Cell($headerRows + 1, 1).Range.Start
        $dataRange.End = $table.Range.End - 1
        $dataRange.Rows.Delete()
        return
    } catch {}
    for ($row = $table.Rows.Count; $row -gt $headerRows; $row--) {
        try { $table.Rows.Item($row).Delete() } catch {}
    }
}

function Clear-DataCells($table, [int]$headerRows) {
    # 部分正式模板数据行包含纵向合并单元格，Word 不允许逐行删除。
    # 先清空所有数据区单元格，再复用现有行，避免模板示例内容混入报告。
    foreach ($cell in $table.Range.Cells) {
        try {
            if ([int]$cell.RowIndex -gt $headerRows) {
                # 单元格 Range 末尾包含 Word 的单元格结束标记；不能覆盖该标记，
                # 否则 Word 会把单元格乃至整行从表格结构中删除。
                $cellRange = $cell.Range.Duplicate
                if ($cellRange.End -gt $cellRange.Start) { $cellRange.End-- }
                $cellRange.Text = ''
                $cellRange.Font.Color = 0
            }
        } catch {}
    }
}

function Set-DataRow($table, [int]$rowIndex, $values) {
    $maxColumns = [Math]::Min($table.Columns.Count, $values.Count)
    for ($column = 1; $column -le $maxColumns; $column++) {
        Set-Cell $table $rowIndex $column $values[$column - 1]
    }
}

function Add-DataRow($table, $values) {
    $row = $table.Rows.Add()
    $maxColumns = [Math]::Min($table.Columns.Count, $values.Count)
    for ($column = 1; $column -le $maxColumns; $column++) {
        try {
            $cell = $row.Cells.Item($column).Range
            $cell.Text = Text-Value $values[$column - 1]
            $cell.Font.Color = 0
        } catch {}
    }
}

function Set-ParagraphContaining($document, [string]$needle, [string]$replacement) {
    if ([string]::IsNullOrWhiteSpace($needle)) { return $false }
    $range = $document.Content.Duplicate
    if ($range.Find.Execute($needle, $false, $false, $false, $false, $false, $true, 1, $false)) {
        $paragraph = $range.Paragraphs.Item(1)
        $paragraph.Range.Text = $replacement + "`r"
        $paragraph.Range.Font.Color = 0
        return $true
    }
    return $false
}

function Clear-ParagraphContaining($document, [string]$needle) {
    if ([string]::IsNullOrWhiteSpace($needle)) { return $false }
    $range = $document.Content.Duplicate
    if ($range.Find.Execute($needle, $false, $false, $false, $false, $false, $true, 1, $false)) {
        $range.Paragraphs.Item(1).Range.Text = ''
        return $true
    }
    return $false
}

function Insert-BodyAfterHeading($document, [string]$heading, [string]$body) {
    if ([string]::IsNullOrWhiteSpace($body)) { return }
    $match = $document.Content.Duplicate
    if ($match.Find.Execute($heading, $false, $false, $false, $false, $false, $true, 1, $false)) {
        $paragraph = $match.Paragraphs.Item(1)
        $insertAt = [Math]::Max($paragraph.Range.End - 1, $paragraph.Range.Start)
        $range = $document.Range($insertAt, $insertAt)
        $range.InsertAfter("`r" + $body)
        $range.Font.Color = 0
    }
}

function Replace-AllInRange($range, [string]$findText, [string]$replaceText) {
    if ($null -eq $range -or [string]::IsNullOrWhiteSpace($findText)) { return }
    $find = $range.Find
    $find.ClearFormatting()
    $find.Replacement.ClearFormatting()
    $find.Text = $findText
    $find.Replacement.Text = Text-Value $replaceText
    $null = $find.Execute($findText, $false, $false, $false, $false, $false, $true, 1, $false, (Text-Value $replaceText), 2)
}

function Remove-RedInstructions($document) {
    # 红色内容是模板编制说明，不属于正式报告。先完成数据填充，再统一删除。
    $range = $document.Content.Duplicate
    $find = $range.Find
    $find.ClearFormatting()
    $find.Replacement.ClearFormatting()
    $find.Text = ''
    $find.Replacement.Text = ''
    $find.Font.Color = 255
    $null = $find.Execute('', $false, $false, $false, $false, $false, $true, 1, $true, '', 2)

    # Word 的“全部替换”可能只删除文字而保留红色空格/段落标记。
    # 将这些残留格式恢复为黑色，确保交付报告中不存在红色编制痕迹。
    $cursorStart = 0
    $guard = 0
    while ($cursorStart -lt $document.Content.End -and $guard -lt 10000) {
        $guard++
        $formatted = $document.Range($cursorStart, $document.Content.End)
        $formattedFind = $formatted.Find
        $formattedFind.ClearFormatting()
        $formattedFind.Text = ''
        $formattedFind.Font.Color = 255
        $found = $formattedFind.Execute('', $false, $false, $false, $false, $false, $true, 1, $true)
        if (-not $found) { break }
        $formatted.Font.Color = 0
        $nextStart = [Math]::Max($formatted.End, $cursorStart + 1)
        $cursorStart = $nextStart
    }
}

function Result-DisplayValue($item) {
    $raw = Text-Value $item.valueRaw
    if ($raw) { return $raw }
    return Text-Value $item.valueNum
}

function Metric-Is($item, [string[]]$codes) {
    $metric = (Text-Value $item.metricType).ToUpperInvariant().Replace(' ', '')
    foreach ($code in $codes) {
        if ($metric -eq $code.ToUpperInvariant().Replace(' ', '')) { return $true }
    }
    return $false
}

function Fill-SimpleResultTable($document, [int]$tableIndex, [int]$headerRows, $items, [scriptblock]$rowBuilder) {
    if ($tableIndex -gt $document.Tables.Count) { return }
    # 使用数组子表达式，保证只有一条记录时也有稳定的 Count 属性。
    $rows = @(As-Array $items)
    # 未录入/未确认的内容保留模板原结构，由报告预检提示补录，不能静默删除。
    if ($rows.Count -eq 0) { return }
    $table = $document.Tables.Item($tableIndex)
    Clear-DataRows $table $headerRows
    foreach ($item in $rows) {
        $values = & $rowBuilder $item
        Add-DataRow $table $values
    }
}

$word = $null
$doc = $null
try {
    $payload = Get-Content -LiteralPath $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $project = ([string]$payload.__PROJECT_JSON__) | ConvertFrom-Json
    $supplement = ([string]$payload.__REPORT_SUPPLEMENT_JSON__) | ConvertFrom-Json
    $positions = As-Array (([string]$payload.__POSITIONS_JSON__) | ConvertFrom-Json)
    $results = As-Array (([string]$payload.__RESULTS_JSON__) | ConvertFrom-Json)
    $staff = ([string]$payload.__STAFF_JSON__) | ConvertFrom-Json
    $methods = ([string]$payload.__METHODS_JSON__) | ConvertFrom-Json

    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $doc = $word.Documents.Open($templatePath)

    # 页眉不属于 document.Content，需要按节单独替换。
    $headerName = Text-Value $supplement.reportHeaderName $project.companyName
    foreach ($section in $doc.Sections) {
        foreach ($header in $section.Headers) {
            if (-not $header.Exists) { continue }
            Replace-AllInRange $header.Range 'XXXX' $headerName
            Replace-AllInRange $header.Range '职202600XX' $project.projectCode
            Replace-AllInRange $header.Range '职 202600XX' $project.projectCode
        }
    }

    # 保留对模板中明确样例文本的普通替换，复杂表格由下方固定结构写入。
    foreach ($property in $payload.PSObject.Properties) {
        $findText = [string]$property.Name
        if ($findText.StartsWith('__') -or [string]::IsNullOrWhiteSpace($findText)) { continue }
        $replaceText = Text-Value $property.Value
        $null = $doc.Content.Find.Execute($findText, $false, $false, $false, $false, $false, $true, 1, $false, $replaceText, 2)
    }

    # 封面。
    $coverNo = $doc.Tables.Item(1)
    Set-Cell $coverNo 1 2 $project.projectCode
    $cover = $doc.Tables.Item(2)
    Set-Cell $cover 1 2 $project.sampleName
    Set-Cell $cover 2 2 $project.companyName
    Set-Cell $cover 3 2 $project.companyAddress
    Set-Cell $cover 4 2 (Text-Value $project.submitUnit '/')
    $entrust = Text-Value $project.entrustUnit
    $separateEntrust = -not [string]::IsNullOrWhiteSpace($entrust) -and $entrust -ne '/' -and $entrust -ne (Text-Value $project.companyName)
    if ($separateEntrust) {
        Set-Cell $cover 5 2 $entrust
    } else {
        try { $cover.Rows.Item(5).Delete() } catch {}
    }
    Set-Cell $cover ($cover.Rows.Count - 1) 2 (Text-Value $project.detectType '定期检测')

    # 声明和签字人员。
    $statement = if ($separateEntrust) {
        "$(Text-Value $project.orgName)遵守国家有关法律、法规、规章和标准规范，受$entrust 委托，在对$(Text-Value $project.companyName)提供职业病危害因素检测服务过程中，坚持客观、真实、公正的原则，并对所出具的《检测报告》承担法律责任。"
    } else {
        "$(Text-Value $project.orgName)遵守国家有关法律、法规、规章和标准规范，在对$(Text-Value $project.companyName)提供职业病危害因素检测服务过程中，坚持客观、真实、公正的原则，并对所出具的《检测报告》承担法律责任。"
    }
    $null = Set-ParagraphContaining $doc '遵守国家有关法律、法规、规章和标准规范' $statement
    $null = Set-ParagraphContaining $doc '20XX年XX月XX日' (Text-Value $project.statementDate)
    $staffTable = $doc.Tables.Item(3)
    foreach ($entry in @(
        @{ row = 2; value = $staff.writer },
        @{ row = 4; value = $staff.reviewer },
        @{ row = 7; value = $staff.approver }
    )) {
        Set-Cell $staffTable $entry.row 2 $entry.value.name
        Set-Cell $staffTable $entry.row 3 $entry.value.title
        Set-Cell $staffTable $entry.row 4 $entry.value.profession
    }

    # 检测依据、任务来源和检测范围。
    Insert-BodyAfterHeading $doc '1.2 技术标准与规范' (Text-Value $project.evaluationBasis)
    $null = Set-ParagraphContaining $doc '为预防、控制和消除项目可能产生的职业病危害' (Text-Value $project.taskOrigin)
    Insert-BodyAfterHeading $doc '2.3 检测范围' (Text-Value $supplement.detectionScope)

    # 用人单位基本情况（表3.1-1）。
    $companyTable = $doc.Tables.Item(4)
    Set-Cell $companyTable 1 2 $project.companyName
    Set-Cell $companyTable 2 2 $supplement.socialCreditCode
    Set-Cell $companyTable 2 4 $supplement.legalRepresentative
    Set-Cell $companyTable 3 2 (Text-Value $supplement.registeredAddress $project.companyAddress)
    Set-Cell $companyTable 4 2 (Text-Value $supplement.technicalServiceAddress $project.companyAddress)
    Set-Cell $companyTable 5 2 $supplement.contactName
    Set-Cell $companyTable 5 4 $supplement.contactPhone
    Set-Cell $companyTable 6 2 $supplement.industryCategory
    Set-Cell $companyTable 7 2 $supplement.commissioningDate
    Set-Cell $companyTable 7 4 $supplement.economicType
    Set-Cell $companyTable 8 2 $supplement.companyScale
    Set-Cell $companyTable 8 4 $supplement.employeeCount
    Set-Cell $companyTable 9 2 $supplement.outsourcedWorkerCount
    Set-Cell $companyTable 9 4 $supplement.dispatchedWorkerCount

    # 原辅材料、生产设备、防护设施、个人防护用品。
    $materials = As-Array $supplement.materials
    if ($materials.Count -eq 0 -and [bool]$supplement.materialsConfirmedNotApplicable) {
        $materials = @([pscustomobject]@{ workplace = '不涉及'; materialName = '/'; processStage = '/'; mainComponents = '/'; annualAmount = '/'; physicalState = '/'; positions = '/' })
    }
    Fill-SimpleResultTable $doc 5 1 $materials {
        param($item)
        @($item.workplace, $item.materialName, $item.processStage, $item.mainComponents, $item.annualAmount, $item.physicalState, $item.positions)
    }
    $equipment = As-Array $supplement.equipment
    if ($equipment.Count -eq 0 -and [bool]$supplement.equipmentConfirmedNotApplicable) {
        $equipment = @([pscustomobject]@{ workplace = '不涉及'; location = '/'; equipmentName = '/'; totalCount = '/'; runningCount = '/'; operatingPositions = '/' })
    }
    Fill-SimpleResultTable $doc 10 2 $equipment {
        param($item)
        @($item.workplace, $item.location, $item.equipmentName, $item.totalCount, $item.runningCount, $item.operatingPositions)
    }
    $facilities = As-Array $supplement.protectionFacilities
    if ($facilities.Count -eq 0 -and [bool]$supplement.protectionFacilitiesConfirmedNotApplicable) {
        $facilities = @([pscustomobject]@{ workplace = '不涉及'; locationOrPosition = '/'; facilityName = '/'; facilityType = '/'; totalCount = '/'; runningCount = '/' })
    }
    Fill-SimpleResultTable $doc 11 2 $facilities {
        param($item)
        @($item.workplace, $item.locationOrPosition, $item.facilityName, $item.facilityType, $item.totalCount, $item.runningCount)
    }
    $ppes = As-Array $supplement.personalProtectiveEquipment
    if ($ppes.Count -eq 0 -and [bool]$supplement.ppeConfirmedNotApplicable) {
        $ppes = @([pscustomobject]@{ ppeName = '不涉及'; manufacturer = '/'; model = '/'; protectionParameters = '/'; positions = '/'; usageStatus = '/'; category = '/' })
    }
    Fill-SimpleResultTable $doc 12 2 $ppes {
        param($item)
        @($item.ppeName, $item.manufacturer, $item.model, $item.protectionParameters, $item.positions, $item.usageStatus, $item.category)
    }
    $null = Set-ParagraphContaining $doc '介绍从原材料投入到成品产出全过程的工艺描述' (Text-Value $supplement.productionProcess)
    $null = Set-ParagraphContaining $doc '此表仅在进行了物料挥发性有机组分定性时需列出' (Text-Value $supplement.vocAnalysisConclusion)
    if (-not [string]::IsNullOrWhiteSpace((Text-Value $supplement.radiationSourceSummary))) {
        $null = Set-ParagraphContaining $doc '无辐射源项时：用人单位不存在辐射源项' (Text-Value $supplement.radiationSourceSummary)
        if ((Text-Value $supplement.radiationSourceSummary) -match '不存在|无辐射|不涉及') {
            $null = Clear-ParagraphContaining $doc '放射性因素具体情况及检测结果详见本机构另行出具的定期检测报告'
        }
    }

    # 岗位与危害因素接触情况。
    Fill-SimpleResultTable $doc 13 2 $positions {
        param($item)
        @(
            $item.workLocation, $item.jobName, $item.shiftSystem, $item.workingHours,
            $item.workerCount, $item.workerCount, $item.operationType, $item.workLocation,
            $item.operationContent, $item.hazards, $item.operationDuration, '',
            '相对稳定', '正常作业时/连续'
        )
    }
    Insert-BodyAfterHeading $doc '4.2 排除性检测及接触判定' (Text-Value $supplement.exclusionScreeningConclusion)

    # 现场检测与采样计划。
    Fill-SimpleResultTable $doc 15 2 $supplement.samplingPlans {
        param($item)
        @(
            $item.workplace, $item.jobName, $item.operationType, $item.workersPerShift,
            $item.targetLocationTiming, $item.factorName, $item.limitType, $item.contactType,
            $item.detectionMode, $item.detectionTimeType, $item.pointsPerDay, $item.timesPerDay, $item.days
        )
    }
    $null = Set-ParagraphContaining $doc '列出现场检测具体日期' (Text-Value $supplement.productionCondition)
    $null = Set-ParagraphContaining $doc '描述现场检测的布点情况' (Text-Value $supplement.samplingLayoutDescription)
    $null = Set-ParagraphContaining $doc '现场检测条件已满足以下要求' (Text-Value $supplement.detectionConditions)
    $null = Set-ParagraphContaining $doc '描述现场检测时当地的温度、湿度和气压' (Text-Value $supplement.environmentConditions)
    $null = Set-ParagraphContaining $doc '列出所有现场检测人员' (Text-Value $supplement.onsiteInspectors)
    $null = Set-ParagraphContaining $doc '如存在检测计划中有未开展采样与测量' (Text-Value $supplement.planDeviation)
    $null = Clear-ParagraphContaining $doc '如计划无变更则写为'

    # 化学有害因素和噪声结果表。
    $twa = $results | Where-Object { Metric-Is $_ @('TWA', 'CTWA') }
    $cste = $results | Where-Object { Metric-Is $_ @('STEL', 'CSTE') }
    $cme = $results | Where-Object { Metric-Is $_ @('CME', 'MAC') }
    $cpe = $results | Where-Object { Metric-Is $_ @('PE', 'CPE') }
    $lex = $results | Where-Object { Metric-Is $_ @('LEX8H', 'LEX,8H', 'LEX,W') }
    $laeq = $results | Where-Object { Metric-Is $_ @('LAEQ') }
    Fill-SimpleResultTable $doc 17 1 $twa { param($r) @($r.workLocation, $r.jobName, (Text-Value $r.detectionMode '定点长时间检测'), $r.factorName, (Result-DisplayValue $r)) }
    Fill-SimpleResultTable $doc 18 1 $cste { param($r) @($r.workLocation, $r.jobName, $r.workLocation, $r.factorName, (Result-DisplayValue $r)) }
    Fill-SimpleResultTable $doc 19 1 $cme { param($r) @($r.workLocation, $r.jobName, $r.workLocation, $r.factorName, (Result-DisplayValue $r)) }
    Fill-SimpleResultTable $doc 20 2 $cpe { param($r) @($r.workLocation, $r.jobName, $r.workLocation, $r.factorName, (Result-DisplayValue $r), '', '', '', '', '') }
    Fill-SimpleResultTable $doc 21 1 $lex { param($r) @($r.workLocation, $r.jobName, '定点检测', (Text-Value $r.metricType 'LEX,8h'), (Result-DisplayValue $r)) }
    Fill-SimpleResultTable $doc 22 1 $laeq { param($r) @($r.workLocation, $r.jobName, $r.workLocation, '稳态', (Result-DisplayValue $r)) }

    # 岗位汇总、接触人数和超标原因分析。
    Fill-SimpleResultTable $doc 45 2 $results {
        param($r)
        @($r.workLocation, $r.jobName, $r.factorName, $r.metricType, (Result-DisplayValue $r), $r.metricType, $r.limitValue, (Text-Value $r.limitUnit $r.unit), $r.judgement)
    }
    Fill-SimpleResultTable $doc 47 1 $positions {
        param($p)
        @($p.workLocation, $p.jobName, $p.hazards, $p.workerCount)
    }
    $failed = $results | Where-Object { (Text-Value $_.judgement) -eq '不合格' }
    Fill-SimpleResultTable $doc 48 1 $failed {
        param($r)
        $ratio = ''
        try {
            if ([double]$r.limitValue -gt 0) { $ratio = [Math]::Round(([double](Result-DisplayValue $r)) / [double]$r.limitValue, 2) }
        } catch {}
        @($r.workLocation, $r.jobName, $r.workLocation, $r.factorName, $ratio, $r.metricType, $supplement.overLimitSummary)
    }

    # 结论与建议。
    $riskText = "根据《国家卫生健康委办公厅关于公布建设项目职业病危害风险分类管理目录的通知》（国卫办职健发〔2021〕5号）的规定，并结合存在的危害因素及浓度、强度进行分析，$(Text-Value $project.companyName)工作场所职业病危害风险分类为$(Text-Value $supplement.riskCategory)。"
    $null = Set-ParagraphContaining $doc '根据《国家卫生健康委办公厅关于公布建设项目职业病危害风险分类管理目录的通知》' $riskText
    $null = Set-ParagraphContaining $doc '明确是否存在超标岗位' (Text-Value $supplement.overLimitSummary)
    $null = Set-ParagraphContaining $doc '从职业病防护设施、个人使用的职业病防护用品等方面指出' (Text-Value $supplement.majorProblems)
    $null = Set-ParagraphContaining $doc '针对用人单位在职业病防治方面存在的主要问题' (Text-Value $supplement.recommendations)
    $null = Clear-ParagraphContaining $doc '对GBZ 2.1中标注致癌性标识'
    $null = Clear-ParagraphContaining $doc '对用人单位提出进一步做好职业病防治工作的管理和持续改进建议'

    # 报告单封面与签字栏。
    if ($doc.Tables.Count -ge 68) {
        $slip = $doc.Tables.Item(66)
        Set-Cell $slip 1 2 ((Text-Value $project.projectCode) + '号')
        Set-Cell $slip 2 2 $project.companyName
        Set-Cell $slip 3 2 $project.detectType
        Set-Cell $slip 4 2 $project.reportDate
        $summary = $doc.Tables.Item(67)
        Set-Cell $summary 1 2 $project.companyName
        Set-Cell $summary 2 2 $project.companyAddress
        Set-Cell $summary 3 2 (($results | ForEach-Object { $_.factorName } | Select-Object -Unique) -join '、')
        $signature = $doc.Tables.Item(68)
        Set-Cell $signature 1 1 ("编制人：" + (Text-Value $project.writerName))
        Set-Cell $signature 1 2 ("审核人：" + (Text-Value $project.reviewerName))
        Set-Cell $signature 1 3 ("签发人：" + (Text-Value $project.approverName))
    }

    # 报告单签发日期位于正文故事中，声明日期替换完成后再统一处理其余日期占位符。
    Replace-AllInRange $doc.Content '20XX年XX月XX日' (Text-Value $project.reportDate)

    Remove-RedInstructions $doc
    $doc.SaveAs([ref]$outputPath, [ref]0)
    try { $doc.Close($false) } catch {}
    $doc = $null
    try { $word.Quit() } catch {}
    $word = $null
    exit 0
} catch {
    Write-Output $_.Exception.ToString()
    if ($doc -ne $null) { try { $doc.Close($false) } catch {} }
    if ($word -ne $null) { try { $word.Quit() } catch {} }
    exit 1
}
