package com.dsf.ohdp.service;

import com.dsf.auth.security.AuthContext;
import com.dsf.auth.security.AuthenticatedUser;
import com.dsf.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dsf.ohdp.config.OhdpReportProperties;
import com.dsf.ohdp.dto.OhdpDto;
import com.dsf.ohdp.entity.OhdpCompanyProfile;
import com.dsf.ohdp.entity.OhdpDictionaryItem;
import com.dsf.ohdp.entity.OhdpFactorTemplate;
import com.dsf.ohdp.entity.OhdpPositionTemplate;
import com.dsf.ohdp.entity.OhdpProject;
import com.dsf.ohdp.entity.OhdpProjectPosition;
import com.dsf.ohdp.entity.OhdpProjectPositionRel;
import com.dsf.ohdp.entity.OhdpReportFile;
import com.dsf.ohdp.entity.OhdpReportOperationLog;
import com.dsf.ohdp.entity.OhdpReportTextTemplate;
import com.dsf.ohdp.entity.OhdpResultItem;
import com.dsf.ohdp.support.ReportResultRules;
import com.dsf.ohdp.entity.OhdpStaffProfile;
import com.dsf.ohdp.entity.ProjectStatus;
import com.dsf.ohdp.repository.OhdpCompanyProfileRepository;
import com.dsf.ohdp.repository.OhdpDictionaryItemRepository;
import com.dsf.ohdp.repository.OhdpFactorTemplateRepository;
import com.dsf.ohdp.repository.OhdpPositionTemplateRepository;
import com.dsf.ohdp.repository.OhdpProjectPositionRepository;
import com.dsf.ohdp.repository.OhdpProjectPositionRelRepository;
import com.dsf.ohdp.repository.OhdpProjectRepository;
import com.dsf.ohdp.repository.OhdpReportFileRepository;
import com.dsf.ohdp.repository.OhdpReportOperationLogRepository;
import com.dsf.ohdp.repository.OhdpReportTextTemplateRepository;
import com.dsf.ohdp.entity.OhdpOrgConfig;
import com.dsf.ohdp.repository.OhdpOrgConfigRepository;
import com.dsf.ohdp.repository.OhdpResultItemRepository;
import com.dsf.ohdp.repository.OhdpStaffProfileRepository;
import com.dsf.ohdp.support.OhdpCodeLabels;
import com.dsf.ohdp.support.BeiHaiPeriodicReportGenerator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OhdpProjectServiceImpl implements OhdpProjectService {

    private static final DateTimeFormatter CODE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter CHINESE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy\u5e74M\u6708d\u65e5");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern MOJIBAKE_PATTERN = Pattern.compile("\uFFFD");
    private static final String REPORT_TEMPLATE_PATH = "templates/occupational_report_template_v2.docx";
    private static final String REPORT_GENERATOR_SCRIPT_PATH = "templates/generate_beihai_report.ps1";
    private static final String REPORT_FIELD_REFRESH_SCRIPT_PATH = "templates/refresh_report_fields.ps1";
    private static final Random RANDOM = new Random();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String WORD_REPLACE_SCRIPT = """
            param([string]$templatePath, [string]$outputPath, [string]$jsonPath)
            $ErrorActionPreference = 'Stop'

            # Return ArrayList of all tables whose first 3 rows contain any of the keywords
            function Get-MatchingTables($tables, $keywords) {
                $matched = New-Object System.Collections.ArrayList
                foreach ($t in $tables) {
                    $found = $false
                    for ($r = 1; $r -le [Math]::Min($t.Rows.Count,3) -and -not $found; $r++) {
                        for ($c = 1; $c -le [Math]::Min($t.Columns.Count,10) -and -not $found; $c++) {
                            try {
                                $tx = $t.Cell($r,$c).Range.Text
                                foreach ($kw in $keywords) {
                                    if ($tx -like "*$kw*") { $null = $matched.Add($t); $found = $true; break }
                                }
                            } catch {}
                        }
                    }
                }
                return ,$matched
            }

            # Return index of header row (row containing 工种 / 岗位 / 序号)
            function Get-HdrRow($t) {
                for ($r = 1; $r -le [Math]::Min($t.Rows.Count,3); $r++) {
                    for ($c = 1; $c -le [Math]::Min($t.Columns.Count,4); $c++) {
                        try {
                            $tx = $t.Cell($r,$c).Range.Text
                            if ($tx -like '*工种*' -or $tx -like '*岗位*' -or $tx -like '*序号*') { return $r }
                        } catch {}
                    }
                }
                return 1
            }

            # Delete all rows after header row
            function Clear-Rows($t, $hdr) {
                $rc = $t.Rows.Count
                for ($r = $rc; $r -gt $hdr; $r--) { try { $t.Rows.Item($r).Delete() } catch {} }
            }

            # Append a row with given cell values
            function Add-Row($t, $vals) {
                $nr = $t.Rows.Add()
                $mc = [Math]::Min($t.Columns.Count, $vals.Count)
                for ($c = 1; $c -le $mc; $c++) { try { $nr.Cells.Item($c).Range.Text = [string]$vals[$c-1] } catch {} }
            }

            $word = $null; $doc = $null
            try {
                $word = New-Object -ComObject Word.Application
                $word.Visible = $false; $word.DisplayAlerts = 0
                $doc = $word.Documents.Open($templatePath)
                $obj = Get-Content -Path $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json

                # 1. Text placeholder replacements
                foreach ($prop in $obj.PSObject.Properties) {
                    $ft = [string]$prop.Name
                    if ($ft.StartsWith('__') -or [string]::IsNullOrWhiteSpace($ft)) { continue }
                    $doc.Content.Find.Execute($ft,$false,$false,$false,$false,$false,$true,1,$false,[string]$prop.Value,2) | Out-Null
                }

                # 2. Section 3 – Position table
                # Template Table 4: 12 cols = 岗位/工种|生产班制|工时工日|总人数|每班最大作业人数|工作地点|作业类型|作业内容|作业时间|主要危害因素|个人防护用品|防护设施
                if ($obj.PSObject.Properties.Name -contains '__POSITIONS_JSON__') {
                    try {
                        $posJ = [string]$obj.__POSITIONS_JSON__
                        if ($posJ) {
                            $positions = $posJ | ConvertFrom-Json
                            $posTbls = Get-MatchingTables $doc.Tables @('岗位/工种')
                            if ($posTbls.Count -gt 0 -and $positions.Count -gt 0) {
                                $pt = $posTbls[0]; $hdr = Get-HdrRow $pt; Clear-Rows $pt $hdr
                                foreach ($p in $positions) {
                                    Add-Row $pt @(
                                        [string]$p.jobName,
                                        [string]$p.shiftSystem,
                                        [string]$p.workingHours,
                                        [string]$p.workerCount,
                                        [string]$p.workerCount,
                                        [string]$p.workLocation,
                                        [string]$p.operationType,
                                        [string]$p.operationContent,
                                        [string]$p.operationDuration,
                                        [string]$p.hazards,
                                        [string]$p.ppe,
                                        [string]$p.protectionFacilities
                                    )
                                }
                            }
                        }
                    } catch { Write-Output "WARN_POS: $_" }
                }

                # 3. Section 6 – Results tables
                # TWA tables (4 cols): keyword 'PC-TWA'   – fill jobName-factorName|value|limit|judgement
                # STEL tables (9 cols): keyword 'PC-STEL' – fill jobName-factorName|loc|val|CSTEL|limit|judge|..|..|..
                # PE/dust tables (10 cols): keyword 'CPE'  – fill jobName|loc|factor|factor|val|CPE|ratio|..|..|..
                # LEX8H table (4 cols): keyword 'LEX,8h'  – fill jobName|value|limit|judgement
                # LAeq table (5 cols): keyword 'LAeq'     – fill jobName|loc|value|..|..
                if ($obj.PSObject.Properties.Name -contains '__RESULTS_JSON__') {
                    try {
                        $resJ = [string]$obj.__RESULTS_JSON__
                        if ($resJ) {
                            $allR  = $resJ | ConvertFrom-Json
                            $twaR  = $allR | Where-Object { $_.metricType -eq 'TWA' }
                            $stelR = $allR | Where-Object { $_.metricType -eq 'STEL' }
                            $peR   = $allR | Where-Object { $_.metricType -eq 'PE' }
                            $lexR  = $allR | Where-Object { $_.metricType -eq 'LEX8H' -or $_.metricType -eq 'LEX,8H' -or $_.metricType -eq 'LEX,8h' }
                            $laeqR = $allR | Where-Object { $_.metricType -eq 'LAeq' }

                            # TWA: clear all TWA tables, fill first with all TWA rows
                            $twaTbls = Get-MatchingTables $doc.Tables @('PC-TWA')
                            if ($twaTbls.Count -gt 0) {
                                foreach ($t in $twaTbls) { Clear-Rows $t (Get-HdrRow $t) }
                                foreach ($r in $twaR) {
                                    Add-Row $twaTbls[0] @(
                                        "$([string]$r.jobName)-$([string]$r.factorName)",
                                        "$([string]$r.valueNum) $([string]$r.unit)",
                                        "$([string]$r.limitValue) $([string]$r.limitUnit)",
                                        [string]$r.judgement
                                    )
                                }
                            }

                            # STEL: clear all STEL tables, fill first
                            $stelTbls = Get-MatchingTables $doc.Tables @('PC-STEL')
                            if ($stelTbls.Count -gt 0) {
                                foreach ($t in $stelTbls) { Clear-Rows $t (Get-HdrRow $t) }
                                foreach ($r in $stelR) {
                                    $v = "$([string]$r.valueNum) $([string]$r.unit)"
                                    $l = "$([string]$r.limitValue) $([string]$r.limitUnit)"
                                    Add-Row $stelTbls[0] @("$([string]$r.jobName)-$([string]$r.factorName)",'', $v, $v, $l, [string]$r.judgement,'','','')
                                }
                            }

                            # PE/Dust: clear all CPE tables, fill first
                            $peTbls = Get-MatchingTables $doc.Tables @('CPE')
                            if ($peTbls.Count -gt 0) {
                                foreach ($t in $peTbls) { Clear-Rows $t (Get-HdrRow $t) }
                                foreach ($r in $peR) {
                                    $v = "$([string]$r.valueNum) $([string]$r.unit)"
                                    $ex = ''; try { if ([double]$r.limitValue -gt 0) { $ex = [string][Math]::Round([double]$r.valueNum/[double]$r.limitValue,2) } } catch {}
                                    Add-Row $peTbls[0] @([string]$r.jobName,'', [string]$r.factorName, [string]$r.factorName, $v, $v, $ex,'','','')
                                }
                            }

                            # LEX8H noise
                            $lexTbls = Get-MatchingTables $doc.Tables @('LEX,8h','LEX8H')
                            if ($lexTbls.Count -gt 0) {
                                foreach ($t in $lexTbls) { Clear-Rows $t (Get-HdrRow $t) }
                                foreach ($r in $lexR) {
                                    Add-Row $lexTbls[0] @([string]$r.jobName,"$([string]$r.valueNum) $([string]$r.unit)","$([string]$r.limitValue) $([string]$r.limitUnit)",[string]$r.judgement)
                                }
                            }

                            # LAeq noise
                            $laeqTbls = Get-MatchingTables $doc.Tables @('LAeq')
                            if ($laeqTbls.Count -gt 0) {
                                foreach ($t in $laeqTbls) { Clear-Rows $t (Get-HdrRow $t) }
                                foreach ($r in $laeqR) {
                                    Add-Row $laeqTbls[0] @([string]$r.jobName,'',"$([string]$r.valueNum) $([string]$r.unit)",'','')
                                }
                            }
                        }
                    } catch { Write-Output "WARN_RES: $_" }
                }

                # 3.5. Section "检测依据、方法及使用仪器" – Tables 5 (physical) & 6 (chemical)
                # Each factor occupies 3 rows (依据/方法/仪器及编号) with col 1 vertically merged
                if ($obj.PSObject.Properties.Name -contains '__METHODS_JSON__') {
                    try {
                        $methJ = [string]$obj.__METHODS_JSON__
                        if ($methJ) {
                            $meth = $methJ | ConvertFrom-Json
                            $methTbls = Get-MatchingTables $doc.Tables @('检测依据、方法及使用仪器')
                            $physicalEntries = $meth.physical
                            $chemicalEntries = $meth.chemical
                            # Normalize to array form (single PSCustomObject -> array of 1)
                            if ($physicalEntries -ne $null -and $physicalEntries -isnot [array]) { $physicalEntries = ,$physicalEntries }
                            if ($chemicalEntries -ne $null -and $chemicalEntries -isnot [array]) { $chemicalEntries = ,$chemicalEntries }
                            if ($physicalEntries -eq $null) { $physicalEntries = @() }
                            if ($chemicalEntries -eq $null) { $chemicalEntries = @() }
                            for ($mti = 0; $mti -lt $methTbls.Count; $mti++) {
                                $mt = $methTbls[$mti]
                                if ($mti -eq 0) { $entries = $physicalEntries } else { $entries = $chemicalEntries }
                                if ($entries -eq $null) { $entries = @() }
                                $entryCount = @($entries).Count
                                $existingRows = $mt.Rows.Count - 1   # exclude header
                                $existingSlots = [Math]::Floor($existingRows / 3)
                                # Overwrite existing slots
                                for ($si = 0; $si -lt $existingSlots; $si++) {
                                    $baseRow = 2 + $si * 3
                                    if ($si -lt $entryCount) {
                                        $e = if ($entryCount -eq 1) { $entries } else { $entries[$si] }
                                        $basis = [string]$e.basis; if (-not $basis) { $basis = '/' }
                                        $method = [string]$e.method; if (-not $method) { $method = '/' }
                                        $instr = [string]$e.instruments; if (-not $instr) { $instr = '/' }
                                        try { $mt.Cell($baseRow, 1).Range.Text = [string]$e.factorName } catch {}
                                        try { $mt.Cell($baseRow, 2).Range.Text = "依据：$basis" } catch {}
                                        try { $mt.Cell($baseRow + 1, 2).Range.Text = "方法：$method" } catch {}
                                        try { $mt.Cell($baseRow + 2, 2).Range.Text = "仪器及编号：$instr" } catch {}
                                    } else {
                                        # Clear unused slot
                                        try { $mt.Cell($baseRow, 1).Range.Text = '' } catch {}
                                        try { $mt.Cell($baseRow, 2).Range.Text = '' } catch {}
                                        try { $mt.Cell($baseRow + 1, 2).Range.Text = '' } catch {}
                                        try { $mt.Cell($baseRow + 2, 2).Range.Text = '' } catch {}
                                    }
                                }
                                # Append rows for any extra factors beyond existing slots
                                for ($si = $existingSlots; $si -lt $entryCount; $si++) {
                                    $e = if ($entryCount -eq 1) { $entries } else { $entries[$si] }
                                    $basis = [string]$e.basis; if (-not $basis) { $basis = '/' }
                                    $method = [string]$e.method; if (-not $method) { $method = '/' }
                                    $instr = [string]$e.instruments; if (-not $instr) { $instr = '/' }
                                    Add-Row $mt @([string]$e.factorName, "依据：$basis")
                                    Add-Row $mt @('', "方法：$method")
                                    Add-Row $mt @('', "仪器及编号：$instr")
                                }
                            }
                        }
                    } catch { Write-Output "WARN_METH: $_" }
                }

                # 4. Section "项目分工" (Table 3) – staff name/title/cert
                if ($obj.PSObject.Properties.Name -contains '__STAFF_JSON__') {
                    try {
                        $staffJ = [string]$obj.__STAFF_JSON__
                        if ($staffJ) {
                            $staff = $staffJ | ConvertFrom-Json
                            $stTbls = Get-MatchingTables $doc.Tables @('项目分工')
                            if ($stTbls.Count -gt 0) {
                                $st = $stTbls[0]
                                # Row 2 编写人, Row 3 审核人, Row 4 签发人
                                $entries = @($staff.writer, $staff.reviewer, $staff.approver)
                                for ($r = 0; $r -lt 3; $r++) {
                                    $entry = $entries[$r]
                                    $rowIdx = $r + 2
                                    if ($rowIdx -le $st.Rows.Count) {
                                        try { $st.Cell($rowIdx, 2).Range.Text = [string]$entry.name } catch {}
                                        try { $st.Cell($rowIdx, 3).Range.Text = [string]$entry.title } catch {}
                                        try { $st.Cell($rowIdx, 4).Range.Text = [string]$entry.cert } catch {}
                                    }
                                }
                            }
                            # Bottom signature table (keyword 授权签字人) – row 2 may be merged
                            $sigTbls = Get-MatchingTables $doc.Tables @('授权签字人')
                            if ($sigTbls.Count -gt 0) {
                                $sg = $sigTbls[0]
                                if ($sg.Rows.Count -ge 2) {
                                    $existing = ''
                                    try { $existing = $sg.Cell(2,1).Range.Text.Trim([char]13).Trim() } catch {}
                                    # Try filling separate cells; if merged, fall back to combined cell text
                                    $separateOk = $false
                                    try {
                                        $c2 = $sg.Cell(2,2); $c3 = $sg.Cell(2,3)
                                        $c2.Range.Text = [string]$staff.reviewer.name
                                        $c3.Range.Text = [string]$staff.approver.name
                                        $sg.Cell(2,1).Range.Text = [string]$staff.writer.name
                                        $separateOk = $true
                                    } catch {}
                                    if (-not $separateOk) {
                                        $combined = "编制：$([string]$staff.writer.name)    复核：$([string]$staff.reviewer.name)    签发：$([string]$staff.approver.name)"
                                        if ($existing) { $combined = $combined + "    " + $existing }
                                        try { $sg.Cell(2,1).Range.Text = $combined } catch {}
                                    }
                                }
                            }
                        }
                    } catch { Write-Output "WARN_STAFF: $_" }
                }

                # 5. Replace 任务由来 paragraph
                if ($obj.PSObject.Properties.Name -contains '__TASK_ORIGIN_BODY__') {
                    try {
                        $newTaskOrigin = [string]$obj.__TASK_ORIGIN_BODY__
                        if ($newTaskOrigin) {
                            foreach ($p in $doc.Paragraphs) {
                                $ptxt = $p.Range.Text
                                if ($ptxt -like "*根据*自治区*" -or $ptxt -like "*根据《自治区*") {
                                    $p.Range.Text = $newTaskOrigin + "`r"
                                    break
                                }
                            }
                        }
                    } catch { Write-Output "WARN_TASK: $_" }
                }

                # 6. Replace 评价依据 list
                if ($obj.PSObject.Properties.Name -contains '__EVALUATION_BASIS_BODY__') {
                    try {
                        $newBasis = [string]$obj.__EVALUATION_BASIS_BODY__
                        if ($newBasis) {
                            $startP = $null; $endP = $null; $startIdx = -1; $endIdx = -1; $idx = 0
                            foreach ($p in $doc.Paragraphs) {
                                $idx++
                                $ptxt = $p.Range.Text
                                if ($startP -eq $null -and $ptxt -like "*GBZ 2.1-2019*") { $startP = $p; $startIdx = $idx }
                                if ($startP -ne $null -and $ptxt -like "*GBZ 2.2-2007*") { $endP = $p; $endIdx = $idx; break }
                            }
                            if ($startP -ne $null -and $endP -ne $null) {
                                $rng = $doc.Range($startP.Range.Start, $endP.Range.End)
                                $rng.Text = $newBasis + "`r"
                            }
                        }
                    } catch { Write-Output "WARN_BASIS: $_" }
                }

                if ($obj.PSObject.Properties.Name -contains '__APPEND_SUMMARY__') {
                    $doc.Content.InsertAfter("`r`n`r`n" + [string]$obj.__APPEND_SUMMARY__) | Out-Null
                }
                $doc.SaveAs([ref]$outputPath); $doc.Close(); $word.Quit(); exit 0
            } catch {
                if ($doc -ne $null) { try { $doc.Close() } catch {} }
                if ($word -ne $null) { try { $word.Quit() } catch {} }
                Write-Output $_.Exception.Message; exit 1
            }
            """;

    private static final String JUDGE_PENDING = "\u672a\u5224\u5b9a";
    private static final String JUDGE_PASS = "\u5408\u683c";
    private static final String JUDGE_FAIL = "\u4e0d\u5408\u683c";
    private static final String DEFAULT_DETECT_TYPE = "\u5b9a\u671f\u68c0\u6d4b";
    private static final String DEFAULT_SAMPLE_NAME = "\u5de5\u4f5c\u573a\u6240\u7a7a\u6c14\u7b49";
    private static final String DEFAULT_SUBMIT_UNIT = "/";
    private static final String DEFAULT_ENTRUST_UNIT = "/";
    private static final String DEFAULT_WRITER_NAME = "/";
    private static final String DEFAULT_REVIEWER_NAME = "/";
    private static final String DEFAULT_APPROVER_NAME = "/";
    private static final String DEFAULT_WRITER_CERT_NO = "\u6842\u804c\u9632\u57f9\uff08\u804c\u536b\u8bc4\u4ef7\uff092023-XXX\u53f7";
    private static final String DEFAULT_REVIEWER_CERT_NO = "\u6842\u804c\u9632\u57f9\uff08\u804c\u536b\u8bc4\u4ef7\uff092023-XXXX\u53f7";
    private static final String DEFAULT_APPROVER_CERT_NO = "\u6842\u804c\u9632\u57f9\uff08\u804c\u536b\u8bc4\u4ef7\uff092023-XXXX\u53f7";
    private static final String DEFAULT_EVALUATION_BASIS =
            "1.\u300a\u5de5\u4f5c\u573a\u6240\u6709\u5bb3\u56e0\u7d20\u804c\u4e1a\u63a5\u89e6\u9650\u503c \u7b2c1\u90e8\u5206\uff1a\u5316\u5b66\u6709\u5bb3\u56e0\u7d20\u300b\uff08GBZ 2.1-2019\uff09\uff08\u542b\u7b2c1\u3001\u7b2c2\u53f7\u4fee\u6539\u5355\uff09\n"
                    + "2.\u300a\u5de5\u4f5c\u573a\u6240\u6709\u5bb3\u56e0\u7d20\u804c\u4e1a\u63a5\u89e6\u9650\u503c \u7b2c2\u90e8\u5206\uff1a\u7269\u7406\u56e0\u7d20\u300b\uff08GBZ 2.2-2007\uff09";

    private static final String TEMPLATE_PROJECT_CODE = "\u804c202600XX";
    private static final String TEMPLATE_COMPANY_A = "XXXXXXXXXXXXXX\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_B = "XXXXXXXXXXXX\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_C = "XXXXXXXXXXX\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_D = "XXXXXX\u6709\u9650\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_ADDRESS = "\u5317\u6d77\u5e02XXXXXXXXXXXXXXXXXX";
    private static final String TEMPLATE_DETECT_TYPE = "\u76d1\u6d4b\u68c0\u9a8c";
    private static final String TEMPLATE_SAMPLING_DATE = "2025\u5e748\u670821\u65e5";
    private static final String TEMPLATE_REPORT_DATE = "2025\u5e7412\u67084\u65e5";
    private static final String TEMPLATE_SAMPLE_NAME = "\u5de5\u4f5c\u573a\u6240\u7a7a\u6c14\u7b49";
    private static final String TEMPLATE_SAMPLE_NAME_ALT = "\u5de5\u4f5c\u573a\u6240\u7a7a\u6c14\u3001\u566a\u58f0\u7b49";
    private static final String TEMPLATE_PROJECT_CODE_ALT = "OHDP-20260410-001";
    private static final String TEMPLATE_COMPANY_ALT = "\u5e7f\u897f\u793a\u4f8b\u79d1\u6280\u6709\u9650\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_C_ALT = "XXX\u5e7f\u897f\u793a\u4f8b\u79d1\u6280\u6709\u9650\u516c\u53f8";
    private static final String TEMPLATE_COMPANY_ADDRESS_ALT = "\u5e7f\u897f\u5317\u6d77\u5e02\u6d77\u57ce\u533a\u793a\u4f8b\u5927\u905388\u53f7";
    private static final String TEMPLATE_STATEMENT_COMPANY = "XXXXXXXXXXX\u516c\u53f8";
    private static final String TEMPLATE_ORG_NAME = "\u5317\u6d77\u5e02\u75be\u75c5\u9884\u9632\u63a7\u5236\u4e2d\u5fc3\uff08\u5317\u6d77\u5e02\u536b\u751f\u76d1\u7763\u6240\uff09";
    private static final String TEMPLATE_ORG_ADDRESS = "\u5317\u6d77\u5e02\u6d77\u57ce\u533a\u4e91\u5357\u5357\u8def18\u53f7";
    private static final String TEMPLATE_ORG_PHONE = "0779-3902606\uff0c3909359";
    private static final String TEMPLATE_WRITER_CERT = "\u6842\u804c\u9632\u57f9\uff08\u804c\u536b\u8bc4\u4ef7\uff092023-XXX\u53f7";
    private static final String TEMPLATE_REVIEWER_CERT = "\u6842\u804c\u9632\u57f9\uff08\u804c\u536b\u8bc4\u4ef7\uff092023-XXXX\u53f7";

    private static final String REPORT_TEMPLATE_VERSION = "BEIHAI-PERIODIC-2026-V1";
    private static final String REPORT_FILE_SUFFIX = "_report.docx";
    private static final String PREVIEW_FILE_PREFIX = "preview_";
    private static final long PREVIEW_RETENTION_MILLIS = 6L * 60 * 60 * 1000;
    private static final String TEMPLATE_TYPE_TASK_ORIGIN = "TASK_ORIGIN";
    private static final String TEMPLATE_TYPE_EVALUATION_BASIS = "EVALUATION_BASIS";

    private final OhdpProjectRepository projectRepository;
    private final OhdpProjectPositionRepository positionRepository;
    private final OhdpProjectPositionRelRepository projectPositionRelRepository;
    private final OhdpResultItemRepository resultItemRepository;
    private final OhdpReportFileRepository reportFileRepository;
    private final OhdpReportOperationLogRepository reportOperationLogRepository;
    private final OhdpCompanyProfileRepository companyProfileRepository;
    private final OhdpPositionTemplateRepository positionTemplateRepository;
    private final OhdpFactorTemplateRepository factorTemplateRepository;
    private final OhdpDictionaryItemRepository dictionaryItemRepository;
    private final OhdpStaffProfileRepository staffProfileRepository;
    private final OhdpReportTextTemplateRepository reportTextTemplateRepository;
    private final OhdpReportProperties reportProperties;
    private final OhdpOrgConfigRepository orgConfigRepository;

    @Override
    @Transactional
    public OhdpDto.ProjectResponse createProject(OhdpDto.ProjectRequest request) {
        OhdpProject project = new OhdpProject();
        project.setProjectCode(generateProjectCode());
        applyProjectRequest(project, request, true);
        project.setStatus(ProjectStatus.DRAFT);
        project.setDeleted(false);
        return toProjectResponse(projectRepository.save(project));
    }

    @Override
    @Transactional
    public OhdpDto.ProjectResponse updateProject(Long id, OhdpDto.ProjectRequest request) {
        OhdpProject project = getActiveProject(id);
        applyProjectRequest(project, request, false);
        return toProjectResponse(projectRepository.save(project));
    }

    @Override
    @Transactional(readOnly = true)
    public OhdpDto.ProjectResponse findProjectById(Long id) {
        return toProjectResponse(getActiveProject(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.ProjectResponse> findProjectPage(OhdpDto.ProjectQuery query) {
        Pageable pageable = PageRequest.of(
                Math.max(query.getPage(), 0),
                Math.max(query.getSize(), 1),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        String projectCode = normalize(query.getProjectCode());
        String companyName = normalize(query.getCompanyName());
        if (query.getStatus() == null) {
            return projectRepository
                    .findByDeletedFalseAndProjectCodeContainingAndCompanyNameContaining(projectCode, companyName, pageable)
                    .map(this::toProjectResponse);
        }
        return projectRepository
                .findByDeletedFalseAndProjectCodeContainingAndCompanyNameContainingAndStatus(
                        projectCode, companyName, query.getStatus(), pageable)
                .map(this::toProjectResponse);
    }

    @Override
    @Transactional
    public void deleteProject(Long id) {
        OhdpProject project = getActiveProject(id);
        project.setDeleted(true);
        projectRepository.save(project);
    }

    @Override
    @Transactional
    public OhdpDto.PositionResponse addPosition(Long projectId, OhdpDto.PositionRequest request) {
        getActiveProject(projectId);
        OhdpProjectPosition position = new OhdpProjectPosition();
        position.setProjectId(null);
        position.setDeleted(false);
        applyPositionRequest(position, request, true);
        OhdpProjectPosition saved = positionRepository.save(position);
        bindPositionToProject(projectId, saved.getId());
        markProjectDataFilled(projectId);
        return toPositionResponse(saved, projectId);
    }

    @Override
    @Transactional
    public OhdpDto.PositionResponse updatePosition(Long positionId, OhdpDto.PositionRequest request) {
        OhdpProjectPosition position = positionRepository
                .findByIdAndDeletedFalse(positionId)
                .orElseThrow(() -> new BusinessException("position not found: " + positionId));
        applyPositionRequest(position, request, false);
        OhdpProjectPosition saved = positionRepository.save(position);
        List<OhdpProjectPositionRel> relList = projectPositionRelRepository.findByPositionIdAndDeletedFalseOrderByIdAsc(positionId);
        for (OhdpProjectPositionRel rel : relList) {
            markProjectDataFilled(rel.getProjectId());
        }
        Long responseProjectId = relList.isEmpty() ? null : relList.get(0).getProjectId();
        return toPositionResponse(saved, responseProjectId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.PositionResponse> listPositions(Long projectId) {
        getActiveProject(projectId);
        return listProjectPositions(projectId)
                .stream()
                .map(position -> toPositionResponse(position, projectId))
                .toList();
    }

    @Override
    @Transactional
    public void deletePosition(Long positionId) {
        OhdpProjectPosition position = positionRepository
                .findByIdAndDeletedFalse(positionId)
                .orElseThrow(() -> new BusinessException("position not found: " + positionId));
        position.setDeleted(true);
        positionRepository.save(position);
        List<OhdpProjectPositionRel> relList = projectPositionRelRepository.findByPositionIdAndDeletedFalseOrderByIdAsc(positionId);
        for (OhdpProjectPositionRel rel : relList) {
            rel.setDeleted(true);
        }
        if (!relList.isEmpty()) {
            projectPositionRelRepository.saveAll(relList);
        }
    }

    @Override
    @Transactional
    public OhdpDto.ResultItemResponse addResult(Long projectId, OhdpDto.ResultItemRequest request) {
        getActiveProject(projectId);
        OhdpResultItem item = new OhdpResultItem();
        item.setProjectId(projectId);
        item.setDeleted(false);
        applyResultRequest(item, request, true);
        item.setJudgement(JUDGE_PENDING);
        OhdpResultItem saved = resultItemRepository.save(item);
        markProjectDataFilled(projectId);
        return toResultResponse(saved);
    }

    @Override
    @Transactional
    public OhdpDto.ResultItemResponse updateResult(Long resultId, OhdpDto.ResultItemRequest request) {
        OhdpResultItem item = resultItemRepository
                .findByIdAndDeletedFalse(resultId)
                .orElseThrow(() -> new BusinessException("result item not found: " + resultId));
        applyResultRequest(item, request, false);
        item.setJudgement(JUDGE_PENDING);
        OhdpResultItem saved = resultItemRepository.save(item);
        markProjectDataFilled(item.getProjectId());
        return toResultResponse(saved);
    }

    @Override
    @Transactional
    public OhdpDto.ResultBatchResponse addResultsBatch(Long projectId, OhdpDto.ResultBatchRequest request) {
        getActiveProject(projectId);
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("batch items cannot be empty");
        }
        List<OhdpResultItem> toSave = new ArrayList<>();
        for (OhdpDto.ResultItemRequest itemRequest : request.getItems()) {
            OhdpResultItem item = new OhdpResultItem();
            item.setProjectId(projectId);
            item.setDeleted(false);
            applyResultRequest(item, itemRequest, true);
            item.setJudgement(JUDGE_PENDING);
            toSave.add(item);
        }
        resultItemRepository.saveAll(toSave);
        markProjectDataFilled(projectId);
        return OhdpDto.ResultBatchResponse.builder()
                .projectId(projectId)
                .addedCount(toSave.size())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.ResultItemResponse> listResults(Long projectId) {
        getActiveProject(projectId);
        return resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId)
                .stream()
                .map(this::toResultResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteResult(Long resultId) {
        OhdpResultItem item = resultItemRepository
                .findByIdAndDeletedFalse(resultId)
                .orElseThrow(() -> new BusinessException("result item not found: " + resultId));
        item.setDeleted(true);
        resultItemRepository.save(item);
    }

    @Override
    @Transactional
    public OhdpDto.JudgeResponse judge(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        List<OhdpResultItem> items = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
        int qualifiedCount = 0;
        int unqualifiedCount = 0;
        int judgedCount = 0;
        for (OhdpResultItem item : items) {
            String judgement = evaluate(item);
            item.setJudgement(judgement);
            if (!JUDGE_PENDING.equals(judgement)) {
                judgedCount++;
            }
            if (JUDGE_PASS.equals(judgement)) {
                qualifiedCount++;
            }
            if (JUDGE_FAIL.equals(judgement)) {
                unqualifiedCount++;
            }
        }
        resultItemRepository.saveAll(items);
        if (!items.isEmpty()) {
            project.setStatus(ProjectStatus.JUDGED);
            projectRepository.save(project);
        }
        return OhdpDto.JudgeResponse.builder()
                .projectId(projectId)
                .totalCount(items.size())
                .judgedCount(judgedCount)
                .qualifiedCount(qualifiedCount)
                .unqualifiedCount(unqualifiedCount)
                .build();
    }

    @Override
    @Transactional
    public OhdpDto.ReportFileResponse generateReport(Long projectId, boolean deleteNotApplicable) {
        OhdpProject project = getActiveProject(projectId);
        fillDefaultReportFields(project);
        project = projectRepository.save(project);
        OhdpDto.ReportPrecheckResponse precheck = buildReportPrecheck(projectId, project);
        if (!Boolean.TRUE.equals(precheck.getPassed())) {
            throw new BusinessException("报告生成前检查未通过：" + String.join("；", precheck.getMissingItems()));
        }

        Path dir = resolveReportStorageDir();

        String fileName = project.getProjectCode() + REPORT_FILE_SUFFIX;
        Path filePath = dir.resolve(fileName);

        List<OhdpProjectPosition> positions = listProjectPositions(projectId);
        List<OhdpResultItem> results = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
        results.forEach(item -> item.setJudgement(evaluate(item)));
        resultItemRepository.saveAll(results);
        writeReportDocument(filePath, project, positions, results, deleteNotApplicable);

        OhdpReportFile reportFile = reportFileRepository.findByProjectIdAndDeletedFalse(projectId).orElse(null);
        boolean overwrite = reportFile != null;
        if (reportFile == null) {
            reportFile = new OhdpReportFile();
            reportFile.setProjectId(projectId);
            reportFile.setDeleted(false);
        }
        reportFile.setFileName(fileName);
        reportFile.setFilePath(filePath.toString());
        reportFile.setTemplateVersion(REPORT_TEMPLATE_VERSION);
        reportFile.setGeneratedBy(currentUsername());
        reportFile.setGeneratedAt(LocalDateTime.now());
        OhdpReportFile saved = reportFileRepository.save(reportFile);

        project.setStatus(ProjectStatus.REPORT_READY);
        projectRepository.save(project);

        if (overwrite) {
            logReportOperation(projectId, "OVERWRITE", "报告已重新生成，原文件已覆盖（"
                    + (deleteNotApplicable ? "已删除不涉及内容" : "已保留不涉及内容") + "）");
        } else {
            logReportOperation(projectId, "CREATE", "报告已生成（"
                    + (deleteNotApplicable ? "已删除不涉及内容" : "已保留不涉及内容") + "）");
        }
        return toReportResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OhdpDto.ReportPrecheckResponse precheckReport(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        return buildReportPrecheck(projectId, project);
    }

    @Override
    @Transactional(readOnly = true)
    public OhdpDto.ReportPreviewResponse previewReport(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        List<OhdpDto.PositionResponse> positions = listProjectPositions(projectId)
                .stream()
                .map(position -> toPositionResponse(position, projectId))
                .toList();
        List<OhdpDto.ResultItemResponse> results = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId)
                .stream()
                .map(this::toResultResponse)
                .toList();
        return OhdpDto.ReportPreviewResponse.builder()
                .project(toProjectResponse(project))
                .positions(positions)
                .results(results)
                .precheck(buildReportPrecheck(projectId, project))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.ReportFileResponse> listReports(Long projectId) {
        getActiveProject(projectId);
        return reportFileRepository.findByProjectIdAndDeletedFalseOrderByGeneratedAtDesc(projectId)
                .stream()
                .map(this::toReportResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.ReportOperationLogResponse> listReportOperationLogs(Long projectId) {
        getActiveProject(projectId);
        return reportOperationLogRepository.findByProjectIdAndDeletedFalseOrderByOperatedAtDesc(projectId)
                .stream()
                .map(this::toReportOperationLogResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.ReportCenterResponse> pageReportCenter(
            String projectCode,
            String companyName,
            String reportStatus,
            LocalDate generatedFrom,
            LocalDate generatedTo,
            int page,
            int size) {
        String normalizedStatus = reportStatus == null ? "" : reportStatus.trim().toUpperCase();
        if (!normalizedStatus.isEmpty() && !List.of("GENERATED", "PENDING").contains(normalizedStatus)) {
            throw new BusinessException("报告状态仅支持 GENERATED 或 PENDING");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LocalDateTime startTime = generatedFrom == null ? null : generatedFrom.atStartOfDay();
        LocalDateTime endTime = generatedTo == null ? null : generatedTo.plusDays(1).atStartOfDay();
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return projectRepository.findReportCenterPage(
                        normalize(projectCode), normalize(companyName), normalizedStatus, startTime, endTime, pageable)
                .map(row -> toReportCenterResponse((OhdpProject) row[0], (OhdpReportFile) row[1]));
    }

    @Override
    @Transactional
    public OhdpDto.ProjectResponse copyProject(Long sourceProjectId) {
        OhdpProject source = getActiveProject(sourceProjectId);
        OhdpProject copied = new OhdpProject();
        copied.setProjectCode(generateProjectCode());
        copied.setCompanyProfileId(source.getCompanyProfileId());
        copied.setCompanyName(source.getCompanyName());
        copied.setCompanyAddress(source.getCompanyAddress());
        copied.setDetectTypeDictItemId(source.getDetectTypeDictItemId());
        copied.setDetectType(source.getDetectType());
        copied.setSamplingDate(source.getSamplingDate());
        copied.setReportDate(source.getReportDate());
        copied.setSampleName(source.getSampleName());
        copied.setSubmitUnitCompanyId(source.getSubmitUnitCompanyId());
        copied.setSubmitUnit(source.getSubmitUnit());
        copied.setEntrustUnitCompanyId(source.getEntrustUnitCompanyId());
        copied.setEntrustUnit(source.getEntrustUnit());
        copied.setTaskOriginTemplateId(source.getTaskOriginTemplateId());
        copied.setTaskOrigin(source.getTaskOrigin());
        copied.setEvaluationBasisTemplateId(source.getEvaluationBasisTemplateId());
        copied.setEvaluationBasis(source.getEvaluationBasis());
        copied.setWriterStaffId(source.getWriterStaffId());
        copied.setWriterName(source.getWriterName());
        copied.setReviewerStaffId(source.getReviewerStaffId());
        copied.setReviewerName(source.getReviewerName());
        copied.setApproverStaffId(source.getApproverStaffId());
        copied.setApproverName(source.getApproverName());
        copied.setWriterCertNo(source.getWriterCertNo());
        copied.setReviewerCertNo(source.getReviewerCertNo());
        copied.setApproverCertNo(source.getApproverCertNo());
        copied.setStatementDate(source.getStatementDate());
        copied.setNotes(source.getNotes());
        copied.setReportSupplementJson(source.getReportSupplementJson());
        copied.setStatus(ProjectStatus.DRAFT);
        copied.setDeleted(false);
        copied = projectRepository.save(copied);

        List<OhdpProjectPosition> sourcePositions = listProjectPositions(sourceProjectId);
        if (!sourcePositions.isEmpty()) {
            for (OhdpProjectPosition src : sourcePositions) {
                bindPositionToProject(copied.getId(), src.getId());
            }
        }

        List<OhdpResultItem> sourceResults = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(sourceProjectId);
        if (!sourceResults.isEmpty()) {
            List<OhdpResultItem> copiedResults = new ArrayList<>();
            for (OhdpResultItem src : sourceResults) {
                OhdpResultItem target = new OhdpResultItem();
                target.setProjectId(copied.getId());
                target.setPositionId(src.getPositionId());
                target.setFactorId(src.getFactorId());
                target.setJobName(src.getJobName());
                target.setFactorName(src.getFactorName());
                target.setMetricType(src.getMetricType());
                target.setValueRaw(src.getValueRaw());
                target.setValueNum(src.getValueNum());
                target.setUnit(src.getUnit());
                target.setLimitValue(src.getLimitValue());
                target.setLimitUnit(src.getLimitUnit());
                target.setJudgement(JUDGE_PENDING);
                target.setDeleted(false);
                copiedResults.add(target);
            }
            resultItemRepository.saveAll(copiedResults);
        }

        if (!sourcePositions.isEmpty() || !sourceResults.isEmpty()) {
            copied.setStatus(ProjectStatus.DATA_FILLED);
            copied = projectRepository.save(copied);
        }
        return toProjectResponse(copied);
    }

    @Override
    public File getPreviewReportFile(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        Path previewDir = resolveReportStorageDir().resolve("preview");
        try {
            Files.createDirectories(previewDir);
        } catch (IOException e) {
            throw new BusinessException("报告预览目录创建失败");
        }
        cleanupPreviewFiles(previewDir);

        String fileName = PREVIEW_FILE_PREFIX
                + project.getProjectCode()
                + "_"
                + LocalDateTime.now().format(CODE_TIME_FORMAT)
                + ".doc";
        Path filePath = previewDir.resolve(fileName);
        List<OhdpProjectPosition> positions = listProjectPositions(projectId);
        List<OhdpResultItem> results = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
        writeReportDocument(filePath, project, positions, results);
        return filePath.toFile();
    }

    @Override
    @Transactional(readOnly = true)
    public File getReportFile(Long reportId) {
        OhdpReportFile reportFile = reportFileRepository
                .findByIdAndDeletedFalse(reportId)
                .orElseThrow(() -> new BusinessException("报告文件记录不存在：" + reportId));
        File file = new File(reportFile.getFilePath());
        if (!file.exists() || !file.isFile()) {
            throw new BusinessException("报告文件在存储目录中不存在");
        }
        return file;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.CompanyProfileResponse> pageCompanyProfiles(String keyword, int page, int size) {
        return companyProfileRepository
                .findByDeletedFalseAndCompanyNameContaining(normalize(keyword), pageRequest(page, size))
                .map(this::toCompanyProfileResponse);
    }

    @Override
    @Transactional
    public OhdpDto.CompanyProfileResponse createCompanyProfile(OhdpDto.CompanyProfileRequest request) {
        String companyName = requireText(request.getCompanyName(), "companyName");
        if (companyProfileRepository.existsByCompanyNameAndDeletedFalse(companyName)) {
            throw new BusinessException("company already exists");
        }
        OhdpCompanyProfile entity = new OhdpCompanyProfile();
        entity.setCompanyName(companyName);
        entity.setCompanyAddress(requireText(request.getCompanyAddress(), "companyAddress"));
        entity.setContactName(trimNullable(request.getContactName()));
        entity.setContactPhone(trimNullable(request.getContactPhone()));
        entity.setIndustryType(trimNullable(request.getIndustryType()));
        entity.setNotes(trimNullable(request.getNotes()));
        entity.setDeleted(false);
        return toCompanyProfileResponse(companyProfileRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.CompanyProfileResponse updateCompanyProfile(Long id, OhdpDto.CompanyProfileRequest request) {
        OhdpCompanyProfile entity = companyProfileRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("company profile not found: " + id));
        String companyName = requireText(request.getCompanyName(), "companyName");
        if (companyProfileRepository.existsByCompanyNameAndDeletedFalseAndIdNot(companyName, id)) {
            throw new BusinessException("company already exists");
        }
        entity.setCompanyName(companyName);
        entity.setCompanyAddress(requireText(request.getCompanyAddress(), "companyAddress"));
        entity.setContactName(trimNullable(request.getContactName()));
        entity.setContactPhone(trimNullable(request.getContactPhone()));
        entity.setIndustryType(trimNullable(request.getIndustryType()));
        entity.setNotes(trimNullable(request.getNotes()));
        return toCompanyProfileResponse(companyProfileRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteCompanyProfile(Long id) {
        OhdpCompanyProfile entity = companyProfileRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("company profile not found: " + id));
        entity.setDeleted(true);
        companyProfileRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.PositionTemplateResponse> pagePositionTemplates(String keyword, int page, int size) {
        return positionTemplateRepository
                .findByDeletedFalseAndJobNameContaining(normalize(keyword), pageRequest(page, size))
                .map(this::toPositionTemplateResponse);
    }

    @Override
    @Transactional
    public OhdpDto.PositionTemplateResponse createPositionTemplate(OhdpDto.PositionTemplateRequest request) {
        OhdpPositionTemplate entity = new OhdpPositionTemplate();
        entity.setJobName(requireText(request.getJobName(), "jobName"));
        entity.setShiftSystem(trimNullable(request.getShiftSystem()));
        entity.setWorkingHours(trimNullable(request.getWorkingHours()));
        entity.setOperationType(trimNullable(request.getOperationType()));
        entity.setOperationDuration(trimNullable(request.getOperationDuration()));
        entity.setHazards(trimNullable(request.getHazards()));
        entity.setPpe(trimNullable(request.getPpe()));
        entity.setProtectionFacilities(trimNullable(request.getProtectionFacilities()));
        entity.setNotes(trimNullable(request.getNotes()));
        entity.setDeleted(false);
        return toPositionTemplateResponse(positionTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.PositionTemplateResponse updatePositionTemplate(Long id, OhdpDto.PositionTemplateRequest request) {
        OhdpPositionTemplate entity = positionTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("position template not found: " + id));
        entity.setJobName(requireText(request.getJobName(), "jobName"));
        entity.setShiftSystem(trimNullable(request.getShiftSystem()));
        entity.setWorkingHours(trimNullable(request.getWorkingHours()));
        entity.setOperationType(trimNullable(request.getOperationType()));
        entity.setOperationDuration(trimNullable(request.getOperationDuration()));
        entity.setHazards(trimNullable(request.getHazards()));
        entity.setPpe(trimNullable(request.getPpe()));
        entity.setProtectionFacilities(trimNullable(request.getProtectionFacilities()));
        entity.setNotes(trimNullable(request.getNotes()));
        return toPositionTemplateResponse(positionTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public void deletePositionTemplate(Long id) {
        OhdpPositionTemplate entity = positionTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("position template not found: " + id));
        entity.setDeleted(true);
        positionTemplateRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.FactorTemplateResponse> pageFactorTemplates(String keyword, int page, int size) {
        return factorTemplateRepository
                .findByDeletedFalseAndFactorNameContaining(normalize(keyword), pageRequest(page, size))
                .map(this::toFactorTemplateResponse);
    }

    @Override
    @Transactional
    public OhdpDto.FactorTemplateResponse createFactorTemplate(OhdpDto.FactorTemplateRequest request) {
        OhdpFactorTemplate entity = new OhdpFactorTemplate();
        entity.setFactorName(requireText(request.getFactorName(), "factorName"));
        entity.setMetricType(requireText(request.getMetricType(), "metricType").toUpperCase());
        entity.setDefaultUnit(trimNullable(request.getDefaultUnit()));
        entity.setDefaultLimitValue(trimNullable(request.getDefaultLimitValue()));
        entity.setDefaultLimitUnit(trimNullable(request.getDefaultLimitUnit()));
        entity.setDetectionBasis(trimNullable(request.getDetectionBasis()));
        entity.setDetectionMethod(trimNullable(request.getDetectionMethod()));
        entity.setDetectionInstruments(trimNullable(request.getDetectionInstruments()));
        entity.setNotes(trimNullable(request.getNotes()));
        entity.setDeleted(false);
        return toFactorTemplateResponse(factorTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.FactorTemplateResponse updateFactorTemplate(Long id, OhdpDto.FactorTemplateRequest request) {
        OhdpFactorTemplate entity = factorTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("factor template not found: " + id));
        entity.setFactorName(requireText(request.getFactorName(), "factorName"));
        entity.setMetricType(requireText(request.getMetricType(), "metricType").toUpperCase());
        entity.setDefaultUnit(trimNullable(request.getDefaultUnit()));
        entity.setDefaultLimitValue(trimNullable(request.getDefaultLimitValue()));
        entity.setDefaultLimitUnit(trimNullable(request.getDefaultLimitUnit()));
        entity.setDetectionBasis(trimNullable(request.getDetectionBasis()));
        entity.setDetectionMethod(trimNullable(request.getDetectionMethod()));
        entity.setDetectionInstruments(trimNullable(request.getDetectionInstruments()));
        entity.setNotes(trimNullable(request.getNotes()));
        return toFactorTemplateResponse(factorTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteFactorTemplate(Long id) {
        OhdpFactorTemplate entity = factorTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("factor template not found: " + id));
        entity.setDeleted(true);
        factorTemplateRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.DictionaryItemResponse> listDictionaryItems(String dictType) {
        String type = requireText(dictType, "dictType");
        return dictionaryItemRepository
                .findByDeletedFalseAndDictTypeOrderBySortNoAscIdAsc(type)
                .stream()
                .map(this::toDictionaryItemResponse)
                .toList();
    }

    @Override
    @Transactional
    public OhdpDto.DictionaryItemResponse createDictionaryItem(OhdpDto.DictionaryItemRequest request) {
        String type = requireText(request.getDictType(), "dictType");
        String code = requireText(request.getItemCode(), "itemCode");
        ensureDictionaryCodeUnique(null, type, code);

        OhdpDictionaryItem entity = new OhdpDictionaryItem();
        entity.setDictType(type);
        entity.setItemCode(code);
        entity.setItemName(requireText(request.getItemName(), "itemName"));
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        entity.setDeleted(false);
        return toDictionaryItemResponse(dictionaryItemRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.DictionaryItemResponse updateDictionaryItem(Long id, OhdpDto.DictionaryItemRequest request) {
        OhdpDictionaryItem entity = dictionaryItemRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("dictionary item not found: " + id));

        String type = requireText(request.getDictType(), "dictType");
        String code = requireText(request.getItemCode(), "itemCode");
        ensureDictionaryCodeUnique(id, type, code);

        entity.setDictType(type);
        entity.setItemCode(code);
        entity.setItemName(requireText(request.getItemName(), "itemName"));
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return toDictionaryItemResponse(dictionaryItemRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteDictionaryItem(Long id) {
        OhdpDictionaryItem entity = dictionaryItemRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("dictionary item not found: " + id));
        entity.setDeleted(true);
        dictionaryItemRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.StaffProfileResponse> pageStaffProfiles(String keyword, int page, int size) {
        return staffProfileRepository
                .findByDeletedFalseAndStaffNameContaining(normalize(keyword), pageRequest(page, size))
                .map(this::toStaffProfileResponse);
    }

    @Override
    @Transactional
    public OhdpDto.StaffProfileResponse createStaffProfile(OhdpDto.StaffProfileRequest request) {
        OhdpStaffProfile entity = new OhdpStaffProfile();
        entity.setStaffName(requireText(request.getStaffName(), "staffName"));
        entity.setStaffTitle(trimNullable(request.getStaffTitle()));
        entity.setProfession(trimNullable(request.getProfession()));
        entity.setCertNo(requireText(request.getCertNo(), "certNo"));
        entity.setContactPhone(trimNullable(request.getContactPhone()));
        entity.setNotes(trimNullable(request.getNotes()));
        entity.setDeleted(false);
        return toStaffProfileResponse(staffProfileRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.StaffProfileResponse updateStaffProfile(Long id, OhdpDto.StaffProfileRequest request) {
        OhdpStaffProfile entity = staffProfileRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("staff profile not found: " + id));
        entity.setStaffName(requireText(request.getStaffName(), "staffName"));
        entity.setStaffTitle(trimNullable(request.getStaffTitle()));
        entity.setProfession(trimNullable(request.getProfession()));
        entity.setCertNo(requireText(request.getCertNo(), "certNo"));
        entity.setContactPhone(trimNullable(request.getContactPhone()));
        entity.setNotes(trimNullable(request.getNotes()));
        return toStaffProfileResponse(staffProfileRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteStaffProfile(Long id) {
        OhdpStaffProfile entity = staffProfileRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("staff profile not found: " + id));
        entity.setDeleted(true);
        staffProfileRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OhdpDto.ReportTextTemplateResponse> pageReportTextTemplates(String templateType, String keyword, int page, int size) {
        String type = requireText(templateType, "templateType").toUpperCase();
        return reportTextTemplateRepository
                .findByDeletedFalseAndTemplateTypeAndTemplateNameContaining(type, normalize(keyword), pageRequest(page, size))
                .map(this::toReportTextTemplateResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OhdpDto.ReportTextTemplateResponse> listEnabledReportTextTemplates(String templateType) {
        String type = requireText(templateType, "templateType").toUpperCase();
        return reportTextTemplateRepository.findByDeletedFalseAndTemplateTypeAndEnabledTrueOrderByIdAsc(type).stream()
                .map(this::toReportTextTemplateResponse)
                .toList();
    }

    @Override
    @Transactional
    public OhdpDto.ReportTextTemplateResponse createReportTextTemplate(OhdpDto.ReportTextTemplateRequest request) {
        OhdpReportTextTemplate entity = new OhdpReportTextTemplate();
        entity.setTemplateType(requireText(request.getTemplateType(), "templateType").toUpperCase());
        entity.setTemplateName(requireText(request.getTemplateName(), "templateName"));
        entity.setTemplateContent(requireText(request.getTemplateContent(), "templateContent"));
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        entity.setDeleted(false);
        return toReportTextTemplateResponse(reportTextTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public OhdpDto.ReportTextTemplateResponse updateReportTextTemplate(Long id, OhdpDto.ReportTextTemplateRequest request) {
        OhdpReportTextTemplate entity = reportTextTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("report text template not found: " + id));
        entity.setTemplateType(requireText(request.getTemplateType(), "templateType").toUpperCase());
        entity.setTemplateName(requireText(request.getTemplateName(), "templateName"));
        entity.setTemplateContent(requireText(request.getTemplateContent(), "templateContent"));
        entity.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        return toReportTextTemplateResponse(reportTextTemplateRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteReportTextTemplate(Long id) {
        OhdpReportTextTemplate entity = reportTextTemplateRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("report text template not found: " + id));
        entity.setDeleted(true);
        reportTextTemplateRepository.save(entity);
    }

    private OhdpProject getActiveProject(Long id) {
        return projectRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("project not found: " + id));
    }

    private void markProjectDataFilled(Long projectId) {
        OhdpProject project = getActiveProject(projectId);
        if (project.getStatus() == ProjectStatus.DRAFT
                || project.getStatus() == ProjectStatus.JUDGED
                || project.getStatus() == ProjectStatus.REPORT_READY) {
            project.setStatus(ProjectStatus.DATA_FILLED);
            projectRepository.save(project);
        }
    }

    private void bindPositionToProject(Long projectId, Long positionId) {
        boolean exists = projectPositionRelRepository.findByProjectIdAndPositionIdAndDeletedFalse(projectId, positionId).isPresent();
        if (exists) {
            return;
        }
        OhdpProjectPositionRel rel = new OhdpProjectPositionRel();
        rel.setProjectId(projectId);
        rel.setPositionId(positionId);
        rel.setDeleted(false);
        projectPositionRelRepository.save(rel);
    }

    private List<OhdpProjectPosition> listProjectPositions(Long projectId) {
        List<OhdpProjectPositionRel> relList = projectPositionRelRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
        if (relList.isEmpty()) {
            return List.of();
        }
        List<Long> positionIds = relList.stream().map(OhdpProjectPositionRel::getPositionId).distinct().toList();
        Map<Long, OhdpProjectPosition> map = new LinkedHashMap<>();
        for (OhdpProjectPosition item : positionRepository.findByIdInAndDeletedFalseOrderByIdAsc(positionIds)) {
            map.put(item.getId(), item);
        }
        List<OhdpProjectPosition> positions = new ArrayList<>();
        for (OhdpProjectPositionRel rel : relList) {
            OhdpProjectPosition p = map.get(rel.getPositionId());
            if (p != null) {
                positions.add(p);
            }
        }
        return positions;
    }

    private void applyProjectRequest(OhdpProject project, OhdpDto.ProjectRequest request, boolean createMode) {
        if (request == null) {
            throw new BusinessException("project request is required");
        }
        Long companyProfileId = request.getCompanyProfileId() != null
                ? request.getCompanyProfileId()
                : (createMode ? null : project.getCompanyProfileId());
        Long detectTypeDictItemId = request.getDetectTypeDictItemId() != null
                ? request.getDetectTypeDictItemId()
                : (createMode ? null : project.getDetectTypeDictItemId());
        Long submitUnitCompanyId = request.getSubmitUnitCompanyId() != null
                ? request.getSubmitUnitCompanyId()
                : (createMode ? null : project.getSubmitUnitCompanyId());
        Long entrustUnitCompanyId = request.getEntrustUnitCompanyId() != null
                ? request.getEntrustUnitCompanyId()
                : (createMode ? null : project.getEntrustUnitCompanyId());
        Long taskOriginTemplateId = request.getTaskOriginTemplateId() != null
                ? request.getTaskOriginTemplateId()
                : (createMode ? null : project.getTaskOriginTemplateId());
        Long evaluationBasisTemplateId = request.getEvaluationBasisTemplateId() != null
                ? request.getEvaluationBasisTemplateId()
                : (createMode ? null : project.getEvaluationBasisTemplateId());
        Long writerStaffId = request.getWriterStaffId() != null
                ? request.getWriterStaffId()
                : (createMode ? null : project.getWriterStaffId());
        Long reviewerStaffId = request.getReviewerStaffId() != null
                ? request.getReviewerStaffId()
                : (createMode ? null : project.getReviewerStaffId());
        Long approverStaffId = request.getApproverStaffId() != null
                ? request.getApproverStaffId()
                : (createMode ? null : project.getApproverStaffId());

        OhdpCompanyProfile profile = null;
        if (companyProfileId != null) {
            profile = companyProfileRepository.findByIdAndDeletedFalse(companyProfileId)
                    .orElseThrow(() -> new BusinessException("company profile not found: " + companyProfileId));
        }
        OhdpDictionaryItem detectTypeDict = null;
        if (detectTypeDictItemId != null) {
            detectTypeDict = dictionaryItemRepository.findByIdAndDeletedFalse(detectTypeDictItemId)
                    .orElseThrow(() -> new BusinessException("detect type dictionary not found: " + detectTypeDictItemId));
            if (!Boolean.TRUE.equals(detectTypeDict.getEnabled())) {
                throw new BusinessException("detect type dictionary is disabled");
            }
        }
        OhdpCompanyProfile submitCompany = null;
        if (submitUnitCompanyId != null) {
            submitCompany = companyProfileRepository.findByIdAndDeletedFalse(submitUnitCompanyId)
                    .orElseThrow(() -> new BusinessException("submit unit company not found: " + submitUnitCompanyId));
        }
        OhdpCompanyProfile entrustCompany = null;
        if (entrustUnitCompanyId != null) {
            entrustCompany = companyProfileRepository.findByIdAndDeletedFalse(entrustUnitCompanyId)
                    .orElseThrow(() -> new BusinessException("entrust unit company not found: " + entrustUnitCompanyId));
        }
        OhdpReportTextTemplate taskOriginTemplate = null;
        if (taskOriginTemplateId != null) {
            taskOriginTemplate = reportTextTemplateRepository.findByIdAndDeletedFalse(taskOriginTemplateId)
                    .orElseThrow(() -> new BusinessException("task origin template not found: " + taskOriginTemplateId));
            if (!TEMPLATE_TYPE_TASK_ORIGIN.equalsIgnoreCase(taskOriginTemplate.getTemplateType())) {
                throw new BusinessException("task origin template type mismatch");
            }
        }
        OhdpReportTextTemplate evaluationBasisTemplate = null;
        if (evaluationBasisTemplateId != null) {
            evaluationBasisTemplate = reportTextTemplateRepository.findByIdAndDeletedFalse(evaluationBasisTemplateId)
                    .orElseThrow(() -> new BusinessException("evaluation basis template not found: " + evaluationBasisTemplateId));
            if (!TEMPLATE_TYPE_EVALUATION_BASIS.equalsIgnoreCase(evaluationBasisTemplate.getTemplateType())) {
                throw new BusinessException("evaluation basis template type mismatch");
            }
        }
        OhdpStaffProfile writerStaff = null;
        if (writerStaffId != null) {
            writerStaff = staffProfileRepository.findByIdAndDeletedFalse(writerStaffId)
                    .orElseThrow(() -> new BusinessException("writer staff not found: " + writerStaffId));
        }
        OhdpStaffProfile reviewerStaff = null;
        if (reviewerStaffId != null) {
            reviewerStaff = staffProfileRepository.findByIdAndDeletedFalse(reviewerStaffId)
                    .orElseThrow(() -> new BusinessException("reviewer staff not found: " + reviewerStaffId));
        }
        OhdpStaffProfile approverStaff = null;
        if (approverStaffId != null) {
            approverStaff = staffProfileRepository.findByIdAndDeletedFalse(approverStaffId)
                    .orElseThrow(() -> new BusinessException("approver staff not found: " + approverStaffId));
        }

        String companyName = firstNonBlank(
                trimNullable(request.getCompanyName()),
                profile == null ? null : profile.getCompanyName(),
                createMode ? null : project.getCompanyName());
        String companyAddress = firstNonBlank(
                trimNullable(request.getCompanyAddress()),
                profile == null ? null : profile.getCompanyAddress(),
                createMode ? null : project.getCompanyAddress());
        String detectType = firstNonBlank(
                trimNullable(request.getDetectType()),
                detectTypeDict == null ? null : detectTypeDict.getItemName(),
                createMode ? DEFAULT_DETECT_TYPE : project.getDetectType(),
                DEFAULT_DETECT_TYPE);
        String sampleName = firstNonBlank(
                trimNullable(request.getSampleName()),
                createMode ? DEFAULT_SAMPLE_NAME : project.getSampleName(),
                DEFAULT_SAMPLE_NAME);
        String submitUnit = firstNonBlank(
                submitCompany == null ? null : submitCompany.getCompanyName(),
                trimNullable(request.getSubmitUnit()),
                createMode ? DEFAULT_SUBMIT_UNIT : project.getSubmitUnit(),
                DEFAULT_SUBMIT_UNIT);
        String entrustUnit = firstNonBlank(
                entrustCompany == null ? null : entrustCompany.getCompanyName(),
                trimNullable(request.getEntrustUnit()),
                createMode ? DEFAULT_ENTRUST_UNIT : project.getEntrustUnit(),
                DEFAULT_ENTRUST_UNIT);
        String taskOrigin = firstNonBlank(
                taskOriginTemplate == null ? null : taskOriginTemplate.getTemplateContent(),
                trimNullable(request.getTaskOrigin()),
                createMode ? defaultTaskOrigin(companyName) : project.getTaskOrigin(),
                defaultTaskOrigin(companyName));
        String evaluationBasis = firstNonBlank(
                evaluationBasisTemplate == null ? null : evaluationBasisTemplate.getTemplateContent(),
                trimNullable(request.getEvaluationBasis()),
                createMode ? DEFAULT_EVALUATION_BASIS : project.getEvaluationBasis(),
                DEFAULT_EVALUATION_BASIS);
        String writerCertNo = firstNonBlank(
                writerStaff == null ? null : writerStaff.getCertNo(),
                trimNullable(request.getWriterCertNo()),
                createMode ? DEFAULT_WRITER_CERT_NO : project.getWriterCertNo(),
                DEFAULT_WRITER_CERT_NO);
        String reviewerCertNo = firstNonBlank(
                reviewerStaff == null ? null : reviewerStaff.getCertNo(),
                trimNullable(request.getReviewerCertNo()),
                createMode ? DEFAULT_REVIEWER_CERT_NO : project.getReviewerCertNo(),
                DEFAULT_REVIEWER_CERT_NO);
        String approverCertNo = firstNonBlank(
                approverStaff == null ? null : approverStaff.getCertNo(),
                trimNullable(request.getApproverCertNo()),
                createMode ? DEFAULT_APPROVER_CERT_NO : project.getApproverCertNo(),
                DEFAULT_APPROVER_CERT_NO);
        String writerName = firstNonBlank(
                writerStaff == null ? null : writerStaff.getStaffName(),
                trimNullable(request.getWriterName()),
                createMode ? DEFAULT_WRITER_NAME : project.getWriterName(),
                DEFAULT_WRITER_NAME);
        String reviewerName = firstNonBlank(
                reviewerStaff == null ? null : reviewerStaff.getStaffName(),
                trimNullable(request.getReviewerName()),
                createMode ? DEFAULT_REVIEWER_NAME : project.getReviewerName(),
                DEFAULT_REVIEWER_NAME);
        String approverName = firstNonBlank(
                approverStaff == null ? null : approverStaff.getStaffName(),
                trimNullable(request.getApproverName()),
                createMode ? DEFAULT_APPROVER_NAME : project.getApproverName(),
                DEFAULT_APPROVER_NAME);

        if (isBlank(companyName)) {
            throw new BusinessException("companyName is required");
        }
        if (isBlank(companyAddress)) {
            throw new BusinessException("companyAddress is required");
        }

        project.setCompanyProfileId(companyProfileId);
        project.setCompanyName(companyName);
        project.setCompanyAddress(companyAddress);
        project.setDetectTypeDictItemId(detectTypeDictItemId);
        project.setDetectType(detectType);
        project.setSamplingDate(request.getSamplingDate() == null ? LocalDate.now() : request.getSamplingDate());
        project.setReportDate(request.getReportDate() == null ? LocalDate.now() : request.getReportDate());
        project.setSampleName(sampleName);
        project.setSubmitUnitCompanyId(submitUnitCompanyId);
        project.setSubmitUnit(submitUnit);
        project.setEntrustUnitCompanyId(entrustUnitCompanyId);
        project.setEntrustUnit(entrustUnit);
        project.setTaskOriginTemplateId(taskOriginTemplateId);
        project.setTaskOrigin(taskOrigin);
        project.setEvaluationBasisTemplateId(evaluationBasisTemplateId);
        project.setEvaluationBasis(evaluationBasis);
        project.setWriterStaffId(writerStaffId);
        project.setWriterName(writerName);
        project.setReviewerStaffId(reviewerStaffId);
        project.setReviewerName(reviewerName);
        project.setApproverStaffId(approverStaffId);
        project.setApproverName(approverName);
        project.setWriterCertNo(writerCertNo);
        project.setReviewerCertNo(reviewerCertNo);
        project.setApproverCertNo(approverCertNo);
        project.setStatementDate(request.getStatementDate() == null ? project.getReportDate() : request.getStatementDate());
        project.setNotes(trimNullable(request.getNotes()));
        OhdpDto.ReportSupplement reportSupplement = request.getReportSupplement();
        if (reportSupplement == null) {
            reportSupplement = createMode
                    ? defaultReportSupplement(profile, companyAddress)
                    : readReportSupplement(project.getReportSupplementJson());
        }
        applyReportSupplementDefaults(reportSupplement, profile, companyAddress);
        project.setReportSupplementJson(writeReportSupplement(reportSupplement));
    }

    private void applyPositionRequest(OhdpProjectPosition position, OhdpDto.PositionRequest request, boolean createMode) {
        if (request == null) {
            throw new BusinessException("position request is required");
        }
        OhdpPositionTemplate template = null;
        if (request.getPositionTemplateId() != null) {
            template = positionTemplateRepository.findByIdAndDeletedFalse(request.getPositionTemplateId())
                    .orElseThrow(() -> new BusinessException("position template not found: " + request.getPositionTemplateId()));
        }

        String jobName = firstNonBlank(
                trimNullable(request.getJobName()),
                template == null ? null : template.getJobName(),
                createMode ? null : position.getJobName());
        if (isBlank(jobName)) {
            throw new BusinessException("jobName is required");
        }

        position.setJobName(jobName);
        position.setShiftSystem(firstNonBlank(
                trimNullable(request.getShiftSystem()),
                template == null ? null : template.getShiftSystem(),
                createMode ? null : position.getShiftSystem()));
        position.setWorkingHours(firstNonBlank(
                trimNullable(request.getWorkingHours()),
                template == null ? null : template.getWorkingHours(),
                createMode ? null : position.getWorkingHours()));
        position.setWorkerCount(request.getWorkerCount() != null
                ? request.getWorkerCount()
                : (createMode ? 1 : position.getWorkerCount()));
        position.setWorkLocation(trimNullable(request.getWorkLocation()));
        position.setOperationType(firstNonBlank(
                trimNullable(request.getOperationType()),
                template == null ? null : template.getOperationType(),
                createMode ? null : position.getOperationType()));
        position.setOperationContent(trimNullable(request.getOperationContent()));
        position.setOperationDuration(firstNonBlank(
                trimNullable(request.getOperationDuration()),
                template == null ? null : template.getOperationDuration(),
                createMode ? null : position.getOperationDuration()));
        position.setHazards(firstNonBlank(
                trimNullable(request.getHazards()),
                template == null ? null : template.getHazards(),
                createMode ? null : position.getHazards()));
        position.setPpe(firstNonBlank(
                trimNullable(request.getPpe()),
                template == null ? null : template.getPpe(),
                createMode ? null : position.getPpe()));
        position.setProtectionFacilities(firstNonBlank(
                trimNullable(request.getProtectionFacilities()),
                template == null ? null : template.getProtectionFacilities(),
                createMode ? null : position.getProtectionFacilities()));
    }

    private void applyResultRequest(OhdpResultItem result, OhdpDto.ResultItemRequest request, boolean createMode) {
        if (request == null) {
            throw new BusinessException("result request is required");
        }
        OhdpProjectPosition position = null;
        if (request.getPositionId() != null) {
            position = positionRepository.findByIdAndDeletedFalse(request.getPositionId())
                    .orElseThrow(() -> new BusinessException("project position not found: " + request.getPositionId()));
            boolean linked = projectPositionRelRepository
                    .findByProjectIdAndPositionIdAndDeletedFalse(result.getProjectId(), position.getId())
                    .isPresent();
            if (!linked) {
                throw new BusinessException("position does not belong to current project");
            }
        }
        OhdpFactorTemplate template = null;
        if (request.getFactorTemplateId() != null) {
            template = factorTemplateRepository.findByIdAndDeletedFalse(request.getFactorTemplateId())
                    .orElseThrow(() -> new BusinessException("factor template not found: " + request.getFactorTemplateId()));
        }

        String jobName = firstNonBlank(
                trimNullable(request.getJobName()),
                position == null ? null : position.getJobName(),
                createMode ? null : result.getJobName());
        String factorName = firstNonBlank(
                trimNullable(request.getFactorName()),
                template == null ? null : template.getFactorName(),
                createMode ? null : result.getFactorName());
        String metricType = firstNonBlank(
                trimNullable(request.getMetricType()),
                template == null ? null : template.getMetricType(),
                createMode ? null : result.getMetricType());
        if (isBlank(jobName)) {
            throw new BusinessException("jobName is required");
        }
        if (isBlank(factorName)) {
            throw new BusinessException("factorName is required");
        }
        if (isBlank(metricType)) {
            throw new BusinessException("metricType is required");
        }
        if (request.getPositionId() == null && result.getPositionId() == null) {
            throw new BusinessException("positionId is required");
        }

        BigDecimal limitValue = request.getLimitValue();
        if (limitValue == null && template != null) {
            limitValue = parseDecimal(template.getDefaultLimitValue());
        }
        if (limitValue == null && !createMode) {
            limitValue = result.getLimitValue();
        }

        result.setPositionId(request.getPositionId() == null ? result.getPositionId() : request.getPositionId());
        result.setFactorId(request.getFactorId() == null ? result.getFactorId() : request.getFactorId());
        result.setJobName(jobName);
        result.setFactorName(factorName);
        result.setMetricType(metricType.toUpperCase());
        result.setValueRaw(trimNullable(request.getValueRaw()));
        result.setValueNum(request.getValueNum() == null && !createMode ? result.getValueNum() : request.getValueNum());
        result.setUnit(firstNonBlank(
                trimNullable(request.getUnit()),
                template == null ? null : trimNullable(template.getDefaultUnit()),
                createMode ? null : result.getUnit()));
        result.setLimitValue(limitValue);
        result.setLimitUnit(firstNonBlank(
                trimNullable(request.getLimitUnit()),
                template == null ? null : trimNullable(template.getDefaultLimitUnit()),
                createMode ? null : result.getLimitUnit()));
        result.setMeasurementDate(request.getMeasurementDate() == null && !createMode
                ? result.getMeasurementDate() : request.getMeasurementDate());
        result.setMeasurementBodyPart(trimNullable(request.getMeasurementBodyPart()));
        result.setMeasurementBand(trimNullable(request.getMeasurementBand()));
        result.setDailyExposureHours(request.getDailyExposureHours() == null && !createMode
                ? result.getDailyExposureHours() : request.getDailyExposureHours());
        result.setWeeklyExposureHours(request.getWeeklyExposureHours() == null && !createMode
                ? result.getWeeklyExposureHours() : request.getWeeklyExposureHours());
        result.setFactorCategory(request.getFactorCategory());
        result.setPointCode(request.getPointCode());
        result.setSampleCode(request.getSampleCode());
        result.setDetectionMode(request.getDetectionMode());
        result.setNoiseType(request.getNoiseType());
        result.setLaboratoryDate(request.getLaboratoryDate());
        result.setDetectionBasis(request.getDetectionBasis());
        result.setDetectionMethod(request.getDetectionMethod());
        result.setInstrument(request.getInstrument());
        result.setTestingOrganization(request.getTestingOrganization());
        result.setSourceRecordNo(request.getSourceRecordNo());
        result.setDetectionLimit(request.getDetectionLimit());
        result.setQuantificationLimit(request.getQuantificationLimit());
        result.setEvaluationBasis(request.getEvaluationBasis());
        result.setOverLimitReason(request.getOverLimitReason());
        result.setWeeklyWorkDays(request.getWeeklyWorkDays());
        result.setReductionApplicable(request.getReductionApplicable());
        result.setPeakLimitMultiplier(request.getPeakLimitMultiplier());
        result.setPeakExposureMinutes(request.getPeakExposureMinutes());
        result.setPeakTimesPerShift(request.getPeakTimesPerShift());
        result.setPeakIntervalMinutes(request.getPeakIntervalMinutes());
        result.setMixedExposureGroup(request.getMixedExposureGroup());
        result.setMixedExposureBasis(request.getMixedExposureBasis());
        result.setMeasurementConditions(request.getMeasurementConditions());
        List<String> validation = ReportResultRules.errors(result, false);
        if (!validation.isEmpty()) throw new BusinessException(String.join("；", validation));
        result.setValueNum(ReportResultRules.numeric(result));
        result.setReductionFactor(ReportResultRules.reduction(result));
        result.setAdjustedLimitValue(ReportResultRules.effectiveLimit(result));

        Long uniquePositionId = result.getPositionId();
        if (uniquePositionId != null && result.getMeasurementDate() == null) {
            boolean duplicated = createMode
                    ? resultItemRepository.existsByProjectIdAndPositionIdAndFactorNameAndMetricTypeAndDeletedFalse(
                    result.getProjectId(), uniquePositionId, result.getFactorName(), result.getMetricType())
                    : resultItemRepository.existsByProjectIdAndPositionIdAndFactorNameAndMetricTypeAndDeletedFalseAndIdNot(
                    result.getProjectId(), uniquePositionId, result.getFactorName(), result.getMetricType(), result.getId());
            if (duplicated) {
                throw new BusinessException("duplicate result item: same project + position + factor + metricType");
            }
        }
    }

    private String evaluate(OhdpResultItem item) {
        item.setReductionFactor(ReportResultRules.reduction(item));
        item.setAdjustedLimitValue(ReportResultRules.effectiveLimit(item));
        return ReportResultRules.judge(item);
    }

    private BigDecimal extractNumeric(String valueRaw) {
        if (valueRaw == null || valueRaw.isBlank()) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(valueRaw);
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group());
    }

    private String generateProjectCode() {
        for (int i = 0; i < 30; i++) {
            String code = "OHDP-" + LocalDateTime.now().format(CODE_TIME_FORMAT) + "-" + (100 + RANDOM.nextInt(900));
            if (!projectRepository.existsByProjectCodeAndDeletedFalse(code)) {
                return code;
            }
        }
        throw new BusinessException("failed to generate project code");
    }

    private void writeReportDocument(
            Path filePath,
            OhdpProject project,
            List<OhdpProjectPosition> positions,
            List<OhdpResultItem> results) {
        writeReportDocument(filePath, project, positions, results, true);
    }

    private void writeReportDocument(
            Path filePath,
            OhdpProject project,
            List<OhdpProjectPosition> positions,
            List<OhdpResultItem> results,
            boolean deleteNotApplicable) {
        Path tempFile = null;
        Map<String, String> replacements = buildTemplateReplacements(project, positions, results);
        try {
            tempFile = Files.createTempFile(filePath.getParent(), "ohdp-report-", ".docx");
            try (InputStream templateStream = new ClassPathResource(REPORT_TEMPLATE_PATH).getInputStream();
                 OutputStream outputStream = Files.newOutputStream(tempFile)) {
                BeiHaiPeriodicReportGenerator.generate(
                        templateStream, outputStream, replacements, deleteNotApplicable);
            }
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            if (reportProperties.isRefreshFieldsOnWindows()) {
                refreshReportFieldsByOffice(filePath);
            }
        } catch (Exception e) {
            throw new BusinessException("failed to generate BeiHai periodic report document: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void refreshReportFieldsByOffice(Path filePath) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }
        Path scriptFile = null;
        try {
            scriptFile = Files.createTempFile(filePath.getParent(), "ohdp-refresh-fields-", ".ps1");
            try (InputStream scriptStream = new ClassPathResource(REPORT_FIELD_REFRESH_SCRIPT_PATH).getInputStream()) {
                Files.writeString(scriptFile, "\uFEFF" + new String(scriptStream.readAllBytes(), StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8);
            }
            ProcessBuilder processBuilder = new ProcessBuilder(
                    resolvePowerShellExecutable(),
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.toString(),
                    filePath.toAbsolutePath().toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.descendants().forEach(child -> child.destroyForcibly());
                process.destroyForcibly();
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                        "Report field refresh exceeded 60 seconds and was stopped: {}", filePath);
                return;
            }
            byte[] output = process.getInputStream().readAllBytes();
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                        "Report field refresh was skipped (exit {}): {}", exitCode,
                        new String(output, StandardCharsets.UTF_8).trim());
            }
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                    "Report field refresh was skipped: {}", ex.getMessage());
        } finally {
            tryDelete(scriptFile);
        }
    }

    private boolean writeReportByWordAutomation(Path outputFile, Map<String, String> replacements) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return false;
        }
        Path templateFile = null;
        Path jsonFile = null;
        Path scriptFile = null;
        try {
            templateFile = Files.createTempFile(outputFile.getParent(), "ohdp-template-", ".doc");
            try (InputStream templateStream = new ClassPathResource(REPORT_TEMPLATE_PATH).getInputStream()) {
                Files.copy(templateStream, templateFile, StandardCopyOption.REPLACE_EXISTING);
            }

            jsonFile = Files.createTempFile(outputFile.getParent(), "ohdp-replacements-", ".json");
            String replacementsJson = OBJECT_MAPPER.writeValueAsString(replacements);
            Files.writeString(jsonFile, replacementsJson, StandardCharsets.UTF_8);

            scriptFile = Files.createTempFile(outputFile.getParent(), "ohdp-word-", ".ps1");
            try (InputStream scriptStream = new ClassPathResource(REPORT_GENERATOR_SCRIPT_PATH).getInputStream()) {
                String scriptText = new String(scriptStream.readAllBytes(), StandardCharsets.UTF_8);
                Files.writeString(scriptFile, "\uFEFF" + scriptText, StandardCharsets.UTF_8);
            }

            ProcessBuilder processBuilder = new ProcessBuilder(
                    resolvePowerShellExecutable(),
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.toString(),
                    templateFile.toString(),
                    outputFile.toString(),
                    jsonFile.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            byte[] scriptOut = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            String scriptOutStr = new String(scriptOut, StandardCharsets.UTF_8).trim();
            if (!scriptOutStr.isEmpty()) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("PS script output: {}", scriptOutStr);
            }
            return exitCode == 0 && Files.exists(outputFile) && isReadableDocFile(outputFile);
        } catch (Exception ex) {
            return false;
        } finally {
            tryDelete(templateFile);
            tryDelete(jsonFile);
            tryDelete(scriptFile);
        }
    }

    private void tryDelete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private String resolvePowerShellExecutable() {
        String[] candidates = new String[] {
                "C:\\\\Windows\\\\System32\\\\WindowsPowerShell\\\\v1.0\\\\powershell.exe",
                "C:\\\\Windows\\\\SysWOW64\\\\WindowsPowerShell\\\\v1.0\\\\powershell.exe"
        };
        for (String candidate : candidates) {
            if (Files.exists(Paths.get(candidate))) {
                return candidate;
            }
        }
        return "powershell";
    }

    private boolean isReadableDocFile(Path filePath) {
        try (InputStream in = Files.newInputStream(filePath);
             HWPFDocument document = new HWPFDocument(in)) {
            String text = document.getRange().text();
            return text != null && text.length() > 100;
        } catch (Exception ex) {
            return false;
        }
    }

    private void copyTemplateAsFallback(Path filePath) throws IOException {
        try (InputStream templateStream = new ClassPathResource(REPORT_TEMPLATE_PATH).getInputStream()) {
            Files.copy(templateStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolveReportStorageDir() {
        String storageDir = reportProperties.getStorageDir();
        if (storageDir == null || storageDir.isBlank()) {
            throw new BusinessException("report storage dir is not configured");
        }
        Path dir = Paths.get(storageDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("failed to prepare report storage directory");
        }
        return dir;
    }

    private void cleanupPreviewFiles(Path previewDir) {
        long cutoff = System.currentTimeMillis() - PREVIEW_RETENTION_MILLIS;
        try (var files = Files.list(previewDir)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                try {
                    if (Files.getLastModifiedTime(path).toMillis() < cutoff) {
                        Files.deleteIfExists(path);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private Map<String, Object> buildProjectData(OhdpProject project) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectCode", valueOf(project.getProjectCode()));
        data.put("companyName", valueOf(project.getCompanyName()));
        data.put("companyAddress", valueOf(project.getCompanyAddress()));
        data.put("detectType", valueOf(project.getDetectType()));
        data.put("samplingDate", formatChineseDate(project.getSamplingDate()));
        data.put("reportDate", formatChineseDate(project.getReportDate()));
        data.put("statementDate", formatChineseDate(project.getStatementDate()));
        data.put("sampleName", valueOf(project.getSampleName()));
        data.put("submitUnit", valueOf(project.getSubmitUnit()));
        data.put("entrustUnit", valueOf(project.getEntrustUnit()));
        data.put("taskOrigin", valueOf(project.getTaskOrigin()));
        data.put("evaluationBasis", valueOf(project.getEvaluationBasis()));
        data.put("writerName", valueOf(project.getWriterName()));
        data.put("reviewerName", valueOf(project.getReviewerName()));
        data.put("approverName", valueOf(project.getApproverName()));
        OhdpOrgConfig org = orgConfigRepository.findFirstByDeletedFalse().orElse(null);
        data.put("orgName", org == null ? TEMPLATE_ORG_NAME : firstNonBlank(org.getOrgName(), TEMPLATE_ORG_NAME));
        data.put("orgAddress", org == null ? TEMPLATE_ORG_ADDRESS : firstNonBlank(org.getOrgAddress(), TEMPLATE_ORG_ADDRESS));
        data.put("orgPhone", org == null ? TEMPLATE_ORG_PHONE : firstNonBlank(org.getContactPhone(), TEMPLATE_ORG_PHONE));
        return data;
    }

    private OhdpDto.ReportSupplement defaultReportSupplement(OhdpCompanyProfile profile, String companyAddress) {
        OhdpDto.ReportSupplement supplement = new OhdpDto.ReportSupplement();
        applyReportSupplementDefaults(supplement, profile, companyAddress);
        supplement.setPlanDeviation("已按现场采样与测量计划开展检测，未对计划进行变更。");
        return supplement;
    }

    private void applyReportSupplementDefaults(
            OhdpDto.ReportSupplement supplement,
            OhdpCompanyProfile profile,
            String companyAddress) {
        if (supplement == null) {
            return;
        }
        if (isBlank(supplement.getRegisteredAddress())) {
            supplement.setRegisteredAddress(companyAddress);
        }
        if (isBlank(supplement.getTechnicalServiceAddress())) {
            supplement.setTechnicalServiceAddress(companyAddress);
        }
        if (profile != null) {
            if (isBlank(supplement.getContactName())) {
                supplement.setContactName(profile.getContactName());
            }
            if (isBlank(supplement.getContactPhone())) {
                supplement.setContactPhone(profile.getContactPhone());
            }
            if (isBlank(supplement.getIndustryCategory())) {
                supplement.setIndustryCategory(profile.getIndustryType());
            }
        }
        if (supplement.getEmployeeCount() == null) {
            supplement.setEmployeeCount(0);
        }
        if (supplement.getOutsourcedWorkerCount() == null) {
            supplement.setOutsourcedWorkerCount(0);
        }
        if (supplement.getDispatchedWorkerCount() == null) {
            supplement.setDispatchedWorkerCount(0);
        }
        if (supplement.getMaterials() == null) {
            supplement.setMaterials(new ArrayList<>());
        }
        if (supplement.getVocAnalysisPerformed() == null) {
            supplement.setVocAnalysisPerformed(Boolean.FALSE);
        }
        if (supplement.getVocAnalysisItems() == null) {
            supplement.setVocAnalysisItems(new ArrayList<>());
        }
        if (isBlank(supplement.getRadiationSourceMode())) {
            supplement.setRadiationSourceMode("NONE");
        }
        if (supplement.getXrayDevices() == null) {
            supplement.setXrayDevices(new ArrayList<>());
        }
        if (supplement.getSealedSources() == null) {
            supplement.setSealedSources(new ArrayList<>());
        }
        if (supplement.getUnsealedSources() == null) {
            supplement.setUnsealedSources(new ArrayList<>());
        }
        if (supplement.getExclusionScreeningPerformed() == null) {
            supplement.setExclusionScreeningPerformed(Boolean.FALSE);
        }
        if (supplement.getExclusionScreeningItems() == null) {
            supplement.setExclusionScreeningItems(new ArrayList<>());
        }
        if (supplement.getEquipment() == null) {
            supplement.setEquipment(new ArrayList<>());
        }
        if (supplement.getProtectionFacilities() == null) {
            supplement.setProtectionFacilities(new ArrayList<>());
        }
        if (supplement.getPersonalProtectiveEquipment() == null) {
            supplement.setPersonalProtectiveEquipment(new ArrayList<>());
        }
        if (supplement.getSamplingPlans() == null) {
            supplement.setSamplingPlans(new ArrayList<>());
        }
        if (supplement.getReportPhotos() == null) {
            supplement.setReportPhotos(new ArrayList<>());
        }
        if (isBlank(supplement.getAttachmentDeliveryPlan())) {
            supplement.setAttachmentDeliveryPlan("委托书、合同、现场调查表、检测计划及其他附件不随主报告提供。");
        }
        if (isBlank(supplement.getPhotoEmbeddingPlan())) {
            supplement.setPhotoEmbeddingPlan("现场照片由报告编制者上传并直接嵌入Word。");
        }
        if (isBlank(supplement.getMultiDayDisplayRule())) {
            supplement.setMultiDayDisplayRule("单日保留日期列；两日及以上逐条显示具体检测日期。");
        }
        if (isBlank(supplement.getOvertimeReductionRule())) {
            supplement.setOvertimeReductionRule("日接触超过8小时或周接触超过40小时时，按确认公式折减PC-TWA。");
        }
        if (isBlank(supplement.getSignatureStampMode())) {
            supplement.setSignatureStampMode("保留打印后手签盖章位置");
        }
        if (supplement.getMaterialsConfirmedNotApplicable() == null) {
            supplement.setMaterialsConfirmedNotApplicable(Boolean.FALSE);
        }
        if (supplement.getEquipmentConfirmedNotApplicable() == null) {
            supplement.setEquipmentConfirmedNotApplicable(Boolean.FALSE);
        }
        if (supplement.getProtectionFacilitiesConfirmedNotApplicable() == null) {
            supplement.setProtectionFacilitiesConfirmedNotApplicable(Boolean.FALSE);
        }
        if (supplement.getPpeConfirmedNotApplicable() == null) {
            supplement.setPpeConfirmedNotApplicable(Boolean.FALSE);
        }
    }

    private String writeReportSupplement(OhdpDto.ReportSupplement supplement) {
        if (supplement == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(supplement);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new BusinessException("报告补充信息格式无效");
        }
    }

    private OhdpDto.ReportSupplement readReportSupplement(String json) {
        if (isBlank(json)) {
            return defaultReportSupplement(null, null);
        }
        try {
            OhdpDto.ReportSupplement supplement = OBJECT_MAPPER.readValue(json, OhdpDto.ReportSupplement.class);
            applyReportSupplementDefaults(supplement, null, null);
            return supplement;
        } catch (Exception ex) {
            throw new BusinessException("报告补充信息无法读取，请重新保存项目");
        }
    }

    private void addMissing(List<String> missingItems, String key, String value) {
        if (isBlank(value)) {
            missingItems.add(key);
        }
    }

    private Map<String, String> buildTemplateReplacements(
            OhdpProject project,
            List<OhdpProjectPosition> positions,
            List<OhdpResultItem> results) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put(TEMPLATE_PROJECT_CODE, valueOf(project.getProjectCode()));
        replacements.put(TEMPLATE_PROJECT_CODE_ALT, valueOf(project.getProjectCode()));
        replacements.put(TEMPLATE_COMPANY_A, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_B, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_C, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_D, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_ALT, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_C_ALT, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_STATEMENT_COMPANY, valueOf(project.getCompanyName()));
        replacements.put(TEMPLATE_COMPANY_ADDRESS, valueOf(project.getCompanyAddress()));
        replacements.put(TEMPLATE_COMPANY_ADDRESS_ALT, valueOf(project.getCompanyAddress()));
        replacements.put(TEMPLATE_DETECT_TYPE, valueOf(project.getDetectType()));
        replacements.put(TEMPLATE_SAMPLING_DATE, formatChineseDate(project.getSamplingDate()));
        replacements.put(TEMPLATE_REPORT_DATE, formatChineseDate(project.getReportDate()));
        replacements.put(TEMPLATE_SAMPLE_NAME, valueOf(project.getSampleName()));
        replacements.put(TEMPLATE_SAMPLE_NAME_ALT, valueOf(project.getSampleName()));
        replacements.put("{{POSITION_COUNT}}", String.valueOf(positions.size()));
        replacements.put("{{RESULT_COUNT}}", String.valueOf(results.size()));
        // Org config replacements
        OhdpOrgConfig org = orgConfigRepository.findFirstByDeletedFalse().orElse(null);
        replacements.put(TEMPLATE_ORG_NAME, org != null && !isBlank(org.getOrgName()) ? org.getOrgName() : TEMPLATE_ORG_NAME);
        replacements.put(TEMPLATE_ORG_ADDRESS, org != null && !isBlank(org.getOrgAddress()) ? org.getOrgAddress() : TEMPLATE_ORG_ADDRESS);
        replacements.put(TEMPLATE_ORG_PHONE, org != null && !isBlank(org.getContactPhone()) ? org.getContactPhone() : TEMPLATE_ORG_PHONE);
        // Cert number replacements
        replacements.put(TEMPLATE_WRITER_CERT, isBlank(project.getWriterCertNo()) ? TEMPLATE_WRITER_CERT : project.getWriterCertNo());
        replacements.put(TEMPLATE_REVIEWER_CERT, isBlank(project.getReviewerCertNo()) ? TEMPLATE_REVIEWER_CERT : project.getReviewerCertNo());
        // Dynamic table data: positions (Section 3) and results (Section 6)
        try {
            replacements.put("__PROJECT_JSON__", OBJECT_MAPPER.writeValueAsString(buildProjectData(project)));
            replacements.put("__REPORT_SUPPLEMENT_JSON__", OBJECT_MAPPER.writeValueAsString(readReportSupplement(project.getReportSupplementJson())));
            List<java.util.Map<String, Object>> posData = new java.util.ArrayList<>();
            for (int i = 0; i < positions.size(); i++) {
                OhdpProjectPosition p = positions.get(i);
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("seq", i + 1);
                row.put("jobName", valueOf(p.getJobName()));
                row.put("shiftSystem", valueOf(p.getShiftSystem()));
                row.put("workingHours", valueOf(p.getWorkingHours()));
                row.put("workerCount", p.getWorkerCount() != null ? p.getWorkerCount().toString() : "");
                row.put("workLocation", valueOf(p.getWorkLocation()));
                row.put("operationType", valueOf(p.getOperationType()));
                row.put("operationContent", valueOf(p.getOperationContent()));
                row.put("operationDuration", valueOf(p.getOperationDuration()));
                row.put("hazards", valueOf(p.getHazards()));
                row.put("ppe", valueOf(p.getPpe()));
                row.put("protectionFacilities", valueOf(p.getProtectionFacilities()));
                posData.add(row);
            }
            replacements.put("__POSITIONS_JSON__", OBJECT_MAPPER.writeValueAsString(posData));

            Map<Long, OhdpProjectPosition> positionById = new LinkedHashMap<>();
            for (OhdpProjectPosition position : positions) {
                if (position.getId() != null) {
                    positionById.put(position.getId(), position);
                }
            }
            List<java.util.Map<String, Object>> resData = new java.util.ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                OhdpResultItem r = results.get(i);
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("seq", i + 1);
                row.put("jobName", valueOf(r.getJobName()));
                OhdpProjectPosition position = r.getPositionId() == null ? null : positionById.get(r.getPositionId());
                row.put("workLocation", position == null ? "" : valueOf(position.getWorkLocation()));
                row.put("factorName", valueOf(r.getFactorName()));
                row.put("metricType", valueOf(r.getMetricType()));
                row.put("valueRaw", r.getValueRaw() != null ? r.getValueRaw() : "");
                row.put("valueNum", r.getValueNum() != null ? r.getValueNum().toPlainString() : "");
                row.put("unit", valueOf(r.getUnit()));
                row.put("limitValue", r.getLimitValue() != null ? r.getLimitValue().toPlainString() : "");
                row.put("limitUnit", valueOf(r.getLimitUnit()));
                row.put("measurementDate", r.getMeasurementDate() == null ? "" : r.getMeasurementDate().toString());
                row.put("measurementBodyPart", valueOf(r.getMeasurementBodyPart()));
                row.put("measurementBand", valueOf(r.getMeasurementBand()));
                row.put("dailyExposureHours", r.getDailyExposureHours() == null ? "" : r.getDailyExposureHours().toPlainString());
                row.put("weeklyExposureHours", r.getWeeklyExposureHours() == null ? "" : r.getWeeklyExposureHours().toPlainString());
                row.put("reductionFactor", r.getReductionFactor() == null ? "" : r.getReductionFactor().toPlainString());
                row.put("adjustedLimitValue", r.getAdjustedLimitValue() == null ? "" : r.getAdjustedLimitValue().toPlainString());
                row.put("judgement", valueOf(r.getJudgement()));
                row.put("factorCategory", r.getFactorCategory() == null ? "" : r.getFactorCategory().toString());
                row.put("pointCode", r.getPointCode() == null ? "" : r.getPointCode().toString());
                row.put("sampleCode", r.getSampleCode() == null ? "" : r.getSampleCode().toString());
                row.put("detectionMode", r.getDetectionMode() == null ? "" : r.getDetectionMode().toString());
                row.put("noiseType", r.getNoiseType() == null ? "" : r.getNoiseType().toString());
                row.put("laboratoryDate", r.getLaboratoryDate() == null ? "" : r.getLaboratoryDate().toString());
                row.put("detectionBasis", r.getDetectionBasis() == null ? "" : r.getDetectionBasis().toString());
                row.put("detectionMethod", r.getDetectionMethod() == null ? "" : r.getDetectionMethod().toString());
                row.put("instrument", r.getInstrument() == null ? "" : r.getInstrument().toString());
                row.put("testingOrganization", r.getTestingOrganization() == null ? "" : r.getTestingOrganization().toString());
                row.put("sourceRecordNo", r.getSourceRecordNo() == null ? "" : r.getSourceRecordNo().toString());
                row.put("detectionLimit", r.getDetectionLimit() == null ? "" : r.getDetectionLimit().toString());
                row.put("quantificationLimit", r.getQuantificationLimit() == null ? "" : r.getQuantificationLimit().toString());
                row.put("evaluationBasis", r.getEvaluationBasis() == null ? "" : r.getEvaluationBasis().toString());
                row.put("overLimitReason", r.getOverLimitReason() == null ? "" : r.getOverLimitReason().toString());
                row.put("weeklyWorkDays", r.getWeeklyWorkDays() == null ? "" : r.getWeeklyWorkDays().toString());
                row.put("reductionApplicable", r.getReductionApplicable() == null ? "" : r.getReductionApplicable().toString());
                row.put("peakLimitMultiplier", r.getPeakLimitMultiplier() == null ? "" : r.getPeakLimitMultiplier().toString());
                row.put("peakExposureMinutes", r.getPeakExposureMinutes() == null ? "" : r.getPeakExposureMinutes().toString());
                row.put("peakTimesPerShift", r.getPeakTimesPerShift() == null ? "" : r.getPeakTimesPerShift().toString());
                row.put("peakIntervalMinutes", r.getPeakIntervalMinutes() == null ? "" : r.getPeakIntervalMinutes().toString());
                row.put("mixedExposureGroup", r.getMixedExposureGroup() == null ? "" : r.getMixedExposureGroup().toString());
                row.put("mixedExposureBasis", r.getMixedExposureBasis() == null ? "" : r.getMixedExposureBasis().toString());
                row.put("measurementConditions", r.getMeasurementConditions() == null ? "" : r.getMeasurementConditions().toString());
                resData.add(row);
            }
            replacements.put("__RESULTS_JSON__", OBJECT_MAPPER.writeValueAsString(resData));

            java.util.Map<String, Object> staffData = new java.util.LinkedHashMap<>();
            staffData.put("writer", buildStaffEntry(project.getWriterStaffId(), project.getWriterName(), project.getWriterCertNo(), TEMPLATE_WRITER_CERT));
            staffData.put("reviewer", buildStaffEntry(project.getReviewerStaffId(), project.getReviewerName(), project.getReviewerCertNo(), TEMPLATE_REVIEWER_CERT));
            staffData.put("approver", buildStaffEntry(project.getApproverStaffId(), project.getApproverName(), project.getApproverCertNo(), DEFAULT_APPROVER_CERT_NO));
            replacements.put("__STAFF_JSON__", OBJECT_MAPPER.writeValueAsString(staffData));

            replacements.put("__METHODS_JSON__", OBJECT_MAPPER.writeValueAsString(buildMethodologyEntries(results)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
        }
        String taskOrigin = isBlank(project.getTaskOrigin()) ? defaultTaskOrigin(valueOf(project.getCompanyName())) : project.getTaskOrigin();
        String evaluationBasis = isBlank(project.getEvaluationBasis()) ? DEFAULT_EVALUATION_BASIS : project.getEvaluationBasis();
        replacements.put("__TASK_ORIGIN_BODY__", taskOrigin);
        replacements.put("__EVALUATION_BASIS_BODY__", evaluationBasis);
        return replacements;
    }

    private java.util.Map<String, List<java.util.Map<String, String>>> buildMethodologyEntries(List<OhdpResultItem> results) {
        java.util.LinkedHashMap<String, java.util.Map<String, String>> byFactor = new java.util.LinkedHashMap<>();
        for (OhdpResultItem r : results) {
            String factorName = valueOf(r.getFactorName());
            String metricType = valueOf(r.getMetricType());
            if (isBlank(factorName) || byFactor.containsKey(factorName)) {
                continue;
            }
            String basis = "";
            String method = "";
            String instruments = "";
            List<OhdpFactorTemplate> hits = factorTemplateRepository.findByDeletedFalseAndFactorName(factorName);
            if (!hits.isEmpty()) {
                OhdpFactorTemplate ft = hits.get(0);
                basis = valueOf(ft.getDetectionBasis());
                method = valueOf(ft.getDetectionMethod());
                instruments = valueOf(ft.getDetectionInstruments());
            }
            java.util.Map<String, String> row = new java.util.LinkedHashMap<>();
            row.put("factorName", factorName);
            row.put("metricType", metricType);
            row.put("basis", basis);
            row.put("method", method);
            row.put("instruments", instruments);
            byFactor.put(factorName, row);
        }
        List<java.util.Map<String, String>> physical = new java.util.ArrayList<>();
        List<java.util.Map<String, String>> chemical = new java.util.ArrayList<>();
        for (java.util.Map<String, String> row : byFactor.values()) {
            String mt = valueOf(row.get("metricType"));
            if ("LEX8H".equalsIgnoreCase(mt) || "LAeq".equalsIgnoreCase(mt) || "LEX,8h".equalsIgnoreCase(mt) || "LEX,8H".equalsIgnoreCase(mt)) {
                physical.add(row);
            } else {
                chemical.add(row);
            }
        }
        java.util.Map<String, List<java.util.Map<String, String>>> grouped = new java.util.LinkedHashMap<>();
        grouped.put("physical", physical);
        grouped.put("chemical", chemical);
        return grouped;
    }

    private java.util.Map<String, String> buildStaffEntry(Long staffId, String fallbackName, String certNo, String defaultCert) {
        java.util.Map<String, String> entry = new java.util.LinkedHashMap<>();
        String name = "";
        String title = "";
        if (staffId != null) {
            OhdpStaffProfile sp = staffProfileRepository.findByIdAndDeletedFalse(staffId).orElse(null);
            if (sp != null) {
                name = valueOf(sp.getStaffName());
                title = valueOf(sp.getStaffTitle());
                entry.put("profession", valueOf(sp.getProfession()));
            }
        }
        if (isBlank(name)) {
            name = isBlank(fallbackName) ? "" : fallbackName;
        }
        entry.put("name", name);
        entry.put("title", title);
        entry.put("cert", isBlank(certNo) ? defaultCert : certNo);
        return entry;
    }

    private void applyTemplateReplacements(Range range, Map<String, String> replacements) {
        replacements.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
                .forEach(entry -> {
                    String source = entry.getKey();
                    if (source.startsWith("__")) {
                        return;
                    }
                    String target = valueOf(entry.getValue());
                    if (!source.equals(target)) {
                        range.replaceText(source, target);
                    }
                });
    }

    private String formatChineseDate(LocalDate date) {
        return date == null ? "-" : CHINESE_DATE_FORMAT.format(date);
    }

    private void fillDefaultReportFields(OhdpProject project) {
        if (isBlank(project.getSampleName())) {
            project.setSampleName(DEFAULT_SAMPLE_NAME);
        }
        if (isBlank(project.getSubmitUnit())) {
            project.setSubmitUnit(DEFAULT_SUBMIT_UNIT);
        }
        if (isBlank(project.getEntrustUnit())) {
            project.setEntrustUnit(DEFAULT_ENTRUST_UNIT);
        }
        if (isBlank(project.getTaskOrigin())) {
            project.setTaskOrigin(defaultTaskOrigin(project.getCompanyName()));
        }
        if (isBlank(project.getEvaluationBasis())) {
            project.setEvaluationBasis(DEFAULT_EVALUATION_BASIS);
        }
        if (isBlank(project.getWriterName())) {
            project.setWriterName(DEFAULT_WRITER_NAME);
        }
        if (isBlank(project.getReviewerName())) {
            project.setReviewerName(DEFAULT_REVIEWER_NAME);
        }
        if (isBlank(project.getApproverName())) {
            project.setApproverName(DEFAULT_APPROVER_NAME);
        }
        if (isBlank(project.getWriterCertNo())) {
            project.setWriterCertNo(DEFAULT_WRITER_CERT_NO);
        }
        if (isBlank(project.getReviewerCertNo())) {
            project.setReviewerCertNo(DEFAULT_REVIEWER_CERT_NO);
        }
        if (isBlank(project.getApproverCertNo())) {
            project.setApproverCertNo(DEFAULT_APPROVER_CERT_NO);
        }
        if (project.getStatementDate() == null) {
            project.setStatementDate(project.getReportDate() == null ? LocalDate.now() : project.getReportDate());
        }
    }

    private String defaultTaskOrigin(String companyName) {
        String unitName = isBlank(companyName) ? "\u53d7\u68c0\u5355\u4f4d" : companyName;
        return "\u4e3a\u9884\u9632\u3001\u63a7\u5236\u548c\u6d88\u9664\u53ef\u80fd\u4ea7\u751f\u7684\u804c\u4e1a\u75c5\u5371\u5bb3\uff0c"
                + "\u5c65\u884c\u4fdd\u62a4\u52b3\u52a8\u8005\u5065\u5eb7\u7684\u804c\u8d23\uff0c"
                + unitName
                + "\u59d4\u6258\u5317\u6d77\u5e02\u75be\u75c5\u9884\u9632\u63a7\u5236\u4e2d\u5fc3\uff08\u5317\u6d77\u5e02\u536b\u751f\u76d1\u7763\u6240\uff09"
                + "\u5bf9\u5176\u7ba1\u7406\u7684\u73b0\u6709\u5de5\u4f5c\u573a\u6240\u8fdb\u884c\u804c\u4e1a\u75c5\u5371\u5bb3\u56e0\u7d20\u68c0\u6d4b\u3002";
    }

    private OhdpDto.ReportPrecheckResponse buildReportPrecheck(Long projectId, OhdpProject project) {
        List<String> missingItems = new ArrayList<>();
        if (isBlank(project.getCompanyName())) {
            missingItems.add("project.companyName");
        } else if (MOJIBAKE_PATTERN.matcher(project.getCompanyName()).find()) {
            missingItems.add("project.companyName.garbled");
        }
        if (isBlank(project.getCompanyAddress())) {
            missingItems.add("project.companyAddress");
        } else if (MOJIBAKE_PATTERN.matcher(project.getCompanyAddress()).find()) {
            missingItems.add("project.companyAddress.garbled");
        }
        if (isBlank(project.getDetectType())) {
            missingItems.add("project.detectType");
        } else if (MOJIBAKE_PATTERN.matcher(project.getDetectType()).find()) {
            missingItems.add("project.detectType.garbled");
        }
        if (project.getSamplingDate() == null) {
            missingItems.add("project.samplingDate");
        }
        if (project.getReportDate() == null) {
            missingItems.add("project.reportDate");
        }

        List<OhdpProjectPosition> positions = listProjectPositions(projectId);
        if (positions.isEmpty()) {
            missingItems.add("positions.empty");
        }
        List<OhdpResultItem> results = resultItemRepository.findByProjectIdAndDeletedFalseOrderByIdAsc(projectId);
        if (results.isEmpty()) {
            missingItems.add("results.empty");
        }
        for (OhdpResultItem item : results) {
            String prefix = "结果[" + item.getJobName() + "/" + item.getFactorName() + "/" + item.getMetricType() + "]：";
            for (String error : ReportResultRules.errors(item, true)) missingItems.add(prefix + error);
            String expected = ReportResultRules.judge(item);
            if (JUDGE_PENDING.equals(expected) || !expected.equals(item.getJudgement())) missingItems.add(prefix + "未完成有效判定，请校正数据后重新判定");
            if (project.getReportDate() != null && ((item.getMeasurementDate() != null && item.getMeasurementDate().isAfter(project.getReportDate()))
                    || (item.getLaboratoryDate() != null && item.getLaboratoryDate().isAfter(project.getReportDate())))) missingItems.add(prefix + "检测日期晚于报告日期");
        }
        if (results.stream().anyMatch(item -> isBlank(item.getJobName()) || isBlank(item.getFactorName()) || isBlank(item.getMetricType()))) {
            missingItems.add("results.requiredFields");
        }
        if (positions.stream().anyMatch(item ->
                containsMojibake(item.getJobName())
                        || containsMojibake(item.getHazards())
                        || containsMojibake(item.getOperationType()))) {
            missingItems.add("positions.garbled");
        }
        if (results.stream().anyMatch(item ->
                containsMojibake(item.getJobName())
                        || containsMojibake(item.getFactorName())
                        || containsMojibake(item.getMetricType())
                        || containsMojibake(item.getJudgement()))) {
            missingItems.add("results.garbled");
        }
        OhdpDto.ReportSupplement supplement = readReportSupplement(project.getReportSupplementJson());
        addMissing(missingItems, "report.reportHeaderName", supplement.getReportHeaderName());
        addMissing(missingItems, "联系人", supplement.getContactName());
        addMissing(missingItems, "联系电话", supplement.getContactPhone());
        addMissing(missingItems, "投产日期", supplement.getCommissioningDate());
        addMissing(missingItems, "经济类型", supplement.getEconomicType());
        addMissing(missingItems, "企业规模", supplement.getCompanyScale());
        if (supplement.getEmployeeCount() == null || supplement.getEmployeeCount() <= 0
                || supplement.getEmployeeCount() < positions.stream().mapToInt(p -> p.getWorkerCount() == null ? 0 : p.getWorkerCount()).sum()) missingItems.add("劳动定员总数不得小于岗位人数合计");
        if (!java.util.Set.of("APPLICABLE", "NOT_APPLICABLE").contains(ReportResultRules.blank(supplement.getMixedExposureAssessment()))) missingItems.add("混合接触适用性须由专业人员确认");
        addMissing(missingItems, "混合接触适用性依据", supplement.getMixedExposureAssessmentBasis());
        if ("APPLICABLE".equals(supplement.getMixedExposureAssessment()) && results.stream().noneMatch(r -> !isBlank(r.getMixedExposureGroup()))) missingItems.add("适用混合接触但未指定结果组合");
        if ("NOT_APPLICABLE".equals(supplement.getMixedExposureAssessment()) && results.stream().anyMatch(r -> !isBlank(r.getMixedExposureGroup()))) missingItems.add("混合接触不适用声明与已填组合矛盾");
        if (results.stream().anyMatch(r -> !isBlank(r.getMixedExposureGroup()) && (isBlank(r.getMixedExposureBasis()) || !ReportResultRules.isTwa(r)))) missingItems.add("混合接触组合需同一评价时段的TWA及相同效应依据");
        Map<String, List<OhdpResultItem>> mixedGroups = results.stream().filter(r -> !isBlank(r.getMixedExposureGroup()))
                .collect(java.util.stream.Collectors.groupingBy(r -> r.getMixedExposureGroup() + "/" + r.getPositionId() + "/" + r.getMeasurementDate()));
        for (var group : mixedGroups.entrySet()) {
            if (group.getValue().size() < 2 || group.getValue().stream().map(OhdpResultItem::getFactorName).distinct().count() != group.getValue().size()) missingItems.add("混合接触组合" + group.getKey() + "必须包含至少两种因素且每因素仅一条同周期结果");
            if (group.getValue().stream().map(OhdpResultItem::getMixedExposureBasis).distinct().count() != 1) missingItems.add("混合接触组合" + group.getKey() + "共同效应依据不一致");
        }
        if (Boolean.TRUE.equals(supplement.getVocAnalysisPerformed())) {
            addMissing(missingItems, "VOC检测机构", supplement.getVocTestingOrganization());
            addMissing(missingItems, "VOC外部报告编号", supplement.getVocSourceReportNo());
        }

        addMissing(missingItems, "report.socialCreditCode", supplement.getSocialCreditCode());
        addMissing(missingItems, "report.legalRepresentative", supplement.getLegalRepresentative());
        addMissing(missingItems, "report.industryCategory", supplement.getIndustryCategory());
        addMissing(missingItems, "report.detectionScope", supplement.getDetectionScope());
        addMissing(missingItems, "report.lawsRegulations", supplement.getLawsRegulations());
        addMissing(missingItems, "report.basicMaterials", supplement.getBasicMaterials());
        addMissing(missingItems, "report.productionProcess", supplement.getProductionProcess());
        addMissing(missingItems, "report.radiationSourceSummary", supplement.getRadiationSourceSummary());
        addMissing(missingItems, "report.radiationSourceMode", supplement.getRadiationSourceMode());
        if ("INCLUDED_REPORT".equalsIgnoreCase(supplement.getRadiationSourceMode())
                && (supplement.getXrayDevices() == null || supplement.getXrayDevices().isEmpty())
                && (supplement.getSealedSources() == null || supplement.getSealedSources().isEmpty())
                && (supplement.getUnsealedSources() == null || supplement.getUnsealedSources().isEmpty())) {
            missingItems.add("report.radiationSources.empty");
        }
        if ("SEPARATE_REPORT".equalsIgnoreCase(supplement.getRadiationSourceMode())) {
            addMissing(missingItems, "report.radiationSeparateReportNo", supplement.getRadiationSeparateReportNo());
        }
        addMissing(missingItems, "report.vocAnalysisConclusion", supplement.getVocAnalysisConclusion());
        if (Boolean.TRUE.equals(supplement.getVocAnalysisPerformed())
                && (supplement.getVocAnalysisItems() == null || supplement.getVocAnalysisItems().isEmpty())) {
            missingItems.add("report.vocAnalysisItems.empty");
        }
        addMissing(missingItems, "report.exclusionScreeningConclusion", supplement.getExclusionScreeningConclusion());
        if (Boolean.TRUE.equals(supplement.getExclusionScreeningPerformed())
                && (supplement.getExclusionScreeningItems() == null || supplement.getExclusionScreeningItems().isEmpty())) {
            missingItems.add("report.exclusionScreeningItems.empty");
        }
        addMissing(missingItems, "report.rawRecordCompletenessNote", supplement.getRawRecordCompletenessNote());
        addMissing(missingItems, "report.qualificationCertificatePlan", supplement.getQualificationCertificatePlan());
        addMissing(missingItems, "report.qualificationCertificateImage", supplement.getQualificationCertificateImageDataUrl());
        if (!isValidReportImage(supplement.getQualificationCertificateImageDataUrl())) missingItems.add("资质证书图片无效，请上传可读取的证书图片");
        if (supplement.getReportPhotos() == null || supplement.getReportPhotos().isEmpty()) missingItems.add("缺少现场照片");
        else for (var photo : supplement.getReportPhotos()) {
            if (!isValidReportImage(photo.getImageDataUrl())) missingItems.add("现场照片无法读取：" + photo.getFileName());
            addMissing(missingItems,"现场照片日期",photo.getTakenDate());
            addMissing(missingItems,"现场照片地点/说明",photo.getCaption());
        }
        for (var staff : java.util.List.of(
                buildStaffEntry(project.getWriterStaffId(),project.getWriterName(),project.getWriterCertNo(),TEMPLATE_WRITER_CERT),
                buildStaffEntry(project.getReviewerStaffId(),project.getReviewerName(),project.getReviewerCertNo(),TEMPLATE_REVIEWER_CERT),
                buildStaffEntry(project.getApproverStaffId(),project.getApproverName(),project.getApproverCertNo(),DEFAULT_APPROVER_CERT_NO))) {
            addMissing(missingItems,"报告人员职称",staff.get("title"));addMissing(missingItems,"报告人员专业",staff.get("profession"));
        }

        addMissing(missingItems, "report.attachmentDeliveryPlan", supplement.getAttachmentDeliveryPlan());
        addMissing(missingItems, "report.photoEmbeddingPlan", supplement.getPhotoEmbeddingPlan());
        addMissing(missingItems, "report.multiDayDisplayRule", supplement.getMultiDayDisplayRule());
        addMissing(missingItems, "report.overtimeReductionRule", supplement.getOvertimeReductionRule());
        addMissing(missingItems, "report.physicalFactorScope", supplement.getPhysicalFactorScope());
        addMissing(missingItems, "report.specialMeasurementRule", supplement.getSpecialMeasurementRule());
        addMissing(missingItems, "report.signatureStampMode", supplement.getSignatureStampMode());
        addMissing(missingItems, "report.productionCondition", supplement.getProductionCondition());
        addMissing(missingItems, "report.samplingLayoutDescription", supplement.getSamplingLayoutDescription());
        addMissing(missingItems, "report.detectionConditions", supplement.getDetectionConditions());
        addMissing(missingItems, "report.environmentConditions", supplement.getEnvironmentConditions());
        addMissing(missingItems, "report.onsiteInspectors", supplement.getOnsiteInspectors());
        addMissing(missingItems, "report.planDeviation", supplement.getPlanDeviation());
        addMissing(missingItems, "report.riskCategory", supplement.getRiskCategory());
        addMissing(missingItems, "report.majorHazards", supplement.getMajorHazards());
        addMissing(missingItems, "report.overLimitSummary", supplement.getOverLimitSummary());
        addMissing(missingItems, "report.majorProblems", supplement.getMajorProblems());
        addMissing(missingItems, "report.recommendations", supplement.getRecommendations());
        if ((supplement.getMaterials() == null || supplement.getMaterials().isEmpty())
                && !Boolean.TRUE.equals(supplement.getMaterialsConfirmedNotApplicable())) {
            missingItems.add("report.materials.unconfirmed");
        }
        if ((supplement.getEquipment() == null || supplement.getEquipment().isEmpty())
                && !Boolean.TRUE.equals(supplement.getEquipmentConfirmedNotApplicable())) {
            missingItems.add("report.equipment.unconfirmed");
        }
        if ((supplement.getProtectionFacilities() == null || supplement.getProtectionFacilities().isEmpty())
                && !Boolean.TRUE.equals(supplement.getProtectionFacilitiesConfirmedNotApplicable())) {
            missingItems.add("report.protectionFacilities.unconfirmed");
        }
        if ((supplement.getPersonalProtectiveEquipment() == null || supplement.getPersonalProtectiveEquipment().isEmpty())
                && !Boolean.TRUE.equals(supplement.getPpeConfirmedNotApplicable())) {
            missingItems.add("report.ppe.unconfirmed");
        }
        if (supplement.getSamplingPlans() == null || supplement.getSamplingPlans().isEmpty()) {
            missingItems.add("report.samplingPlans.empty");
        }
        boolean noDeviation = isBlank(supplement.getPlanDeviation()) || supplement.getPlanDeviation().contains("无变更") || supplement.getPlanDeviation().contains("无偏离");
        if (supplement.getSamplingPlans() != null && noDeviation) for (var plan : supplement.getSamplingPlans()) {
            var actual = results.stream().filter(r -> java.util.Objects.equals(r.getFactorName(), plan.getFactorName()) && java.util.Objects.equals(r.getJobName(), plan.getJobName())).toList();
            long days = actual.stream().map(OhdpResultItem::getMeasurementDate).filter(java.util.Objects::nonNull).distinct().count();
            if (plan.getDays() != null && days > 0 && days != plan.getDays()) missingItems.add("采样计划与实际天数不符：" + plan.getJobName() + "/" + plan.getFactorName() + "，须记录计划偏离");
            if (actual.stream().anyMatch(r -> !isBlank(r.getDetectionMode()) && !isBlank(plan.getDetectionMode()) && !r.getDetectionMode().contains(plan.getDetectionMode().replace("检测", "")))) missingItems.add("计划与实际采样方式不符：" + plan.getFactorName() + "，须记录计划偏离");
        }
        if (results.stream().anyMatch(r -> "放射性因素".equals(ReportResultRules.category(r.getFactorName(),r.getMetricType(),r.getFactorCategory()))) && !"INCLUDED_REPORT".equals(supplement.getRadiationSourceMode())) missingItems.add("已有放射性检测结果，辐射源项处理方式须为合并写入本报告");
        return OhdpDto.ReportPrecheckResponse.builder()
                .projectId(projectId)
                .passed(missingItems.isEmpty())
                .missingItems(missingItems)
                .build();
    }

    private boolean isValidReportImage(String dataUrl) {
        if (isBlank(dataUrl)) return false;
        try {
            int comma=dataUrl.indexOf(',');
            byte[] bytes=java.util.Base64.getDecoder().decode(comma>=0?dataUrl.substring(comma+1):dataUrl);
            var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            return image!=null && image.getWidth()>1 && image.getHeight()>1;
        } catch (Exception ex) { return false; }
    }

    private void logReportOperation(Long projectId, String operationType, String message) {
        OhdpReportOperationLog log = new OhdpReportOperationLog();
        log.setProjectId(projectId);
        log.setOperationType(operationType);
        log.setOperatorName(currentUsername());
        log.setOperationMessage(message);
        log.setOperatedAt(LocalDateTime.now());
        log.setDeleted(false);
        reportOperationLogRepository.save(log);
    }

    private String currentUsername() {
        AuthenticatedUser user = AuthContext.get();
        return user == null ? "system" : user.getUsername();
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by(Sort.Direction.DESC, "id"));
    }

    private void ensureDictionaryCodeUnique(Long currentId, String dictType, String itemCode) {
        boolean duplicated = dictionaryItemRepository.findByDeletedFalseAndDictTypeOrderBySortNoAscIdAsc(dictType).stream()
                .anyMatch(item -> itemCode.equalsIgnoreCase(item.getItemCode())
                        && (currentId == null || !Objects.equals(currentId, item.getId())));
        if (duplicated) {
            throw new BusinessException("dictionary item code already exists");
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private String trimNullable(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        ensureNoMojibake(trimmed);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void ensureNoMojibake(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (MOJIBAKE_PATTERN.matcher(text).find()) {
            throw new BusinessException("input contains garbled text, please use UTF-8 and re-enter");
        }
    }

    private String requireText(String text, String fieldName) {
        String value = trimNullable(text);
        if (value == null) {
            throw new BusinessException(fieldName + " is required");
        }
        return value;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (!isBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean containsMojibake(String value) {
        return value != null && !value.isBlank() && MOJIBAKE_PATTERN.matcher(value).find();
    }

    private OhdpDto.ProjectResponse toProjectResponse(OhdpProject project) {
        return OhdpDto.ProjectResponse.builder()
                .id(project.getId())
                .projectCode(project.getProjectCode())
                .companyProfileId(project.getCompanyProfileId())
                .companyName(project.getCompanyName())
                .companyAddress(project.getCompanyAddress())
                .detectTypeDictItemId(project.getDetectTypeDictItemId())
                .detectType(project.getDetectType())
                .samplingDate(project.getSamplingDate())
                .reportDate(project.getReportDate())
                .sampleName(project.getSampleName())
                .submitUnitCompanyId(project.getSubmitUnitCompanyId())
                .submitUnit(project.getSubmitUnit())
                .entrustUnitCompanyId(project.getEntrustUnitCompanyId())
                .entrustUnit(project.getEntrustUnit())
                .taskOriginTemplateId(project.getTaskOriginTemplateId())
                .taskOrigin(project.getTaskOrigin())
                .evaluationBasisTemplateId(project.getEvaluationBasisTemplateId())
                .evaluationBasis(project.getEvaluationBasis())
                .writerStaffId(project.getWriterStaffId())
                .writerName(project.getWriterName())
                .reviewerStaffId(project.getReviewerStaffId())
                .reviewerName(project.getReviewerName())
                .approverStaffId(project.getApproverStaffId())
                .approverName(project.getApproverName())
                .writerCertNo(project.getWriterCertNo())
                .reviewerCertNo(project.getReviewerCertNo())
                .approverCertNo(project.getApproverCertNo())
                .statementDate(project.getStatementDate())
                .notes(project.getNotes())
                .reportSupplement(readReportSupplement(project.getReportSupplementJson()))
                .status(project.getStatus())
                .statusLabel(OhdpCodeLabels.label(project.getStatus().name()))
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private OhdpDto.PositionResponse toPositionResponse(OhdpProjectPosition position, Long projectId) {
        return OhdpDto.PositionResponse.builder()
                .id(position.getId())
                .projectId(projectId)
                .jobName(position.getJobName())
                .shiftSystem(position.getShiftSystem())
                .workingHours(position.getWorkingHours())
                .workerCount(position.getWorkerCount())
                .workLocation(position.getWorkLocation())
                .operationType(position.getOperationType())
                .operationContent(position.getOperationContent())
                .operationDuration(position.getOperationDuration())
                .hazards(position.getHazards())
                .ppe(position.getPpe())
                .protectionFacilities(position.getProtectionFacilities())
                .build();
    }

    private OhdpDto.ResultItemResponse toResultResponse(OhdpResultItem item) {
        return OhdpDto.ResultItemResponse.builder()
                .id(item.getId())
                .projectId(item.getProjectId())
                .positionId(item.getPositionId())
                .factorId(item.getFactorId())
                .jobName(item.getJobName())
                .factorName(item.getFactorName())
                .metricType(item.getMetricType())
                .valueRaw(item.getValueRaw())
                .valueNum(item.getValueNum())
                .unit(item.getUnit())
                .limitValue(item.getLimitValue())
                .limitUnit(item.getLimitUnit())
                .measurementDate(item.getMeasurementDate())
                .measurementBodyPart(item.getMeasurementBodyPart())
                .measurementBand(item.getMeasurementBand())
                .dailyExposureHours(item.getDailyExposureHours())
                .weeklyExposureHours(item.getWeeklyExposureHours())
                .reductionFactor(item.getReductionFactor())
                .adjustedLimitValue(item.getAdjustedLimitValue())
                .factorCategory(item.getFactorCategory())
                .pointCode(item.getPointCode())
                .sampleCode(item.getSampleCode())
                .detectionMode(item.getDetectionMode())
                .noiseType(item.getNoiseType())
                .laboratoryDate(item.getLaboratoryDate())
                .detectionBasis(item.getDetectionBasis())
                .detectionMethod(item.getDetectionMethod())
                .instrument(item.getInstrument())
                .testingOrganization(item.getTestingOrganization())
                .sourceRecordNo(item.getSourceRecordNo())
                .detectionLimit(item.getDetectionLimit())
                .quantificationLimit(item.getQuantificationLimit())
                .evaluationBasis(item.getEvaluationBasis())
                .overLimitReason(item.getOverLimitReason())
                .weeklyWorkDays(item.getWeeklyWorkDays())
                .reductionApplicable(item.getReductionApplicable())
                .peakLimitMultiplier(item.getPeakLimitMultiplier())
                .peakExposureMinutes(item.getPeakExposureMinutes())
                .peakTimesPerShift(item.getPeakTimesPerShift())
                .peakIntervalMinutes(item.getPeakIntervalMinutes())
                .mixedExposureGroup(item.getMixedExposureGroup())
                .mixedExposureBasis(item.getMixedExposureBasis())
                .measurementConditions(item.getMeasurementConditions())
                .judgement(item.getJudgement())
                .judgementLabel(OhdpCodeLabels.label(item.getJudgement()))
                .build();
    }

    private OhdpDto.ReportFileResponse toReportResponse(OhdpReportFile reportFile) {
        return OhdpDto.ReportFileResponse.builder()
                .id(reportFile.getId())
                .projectId(reportFile.getProjectId())
                .fileName(reportFile.getFileName())
                .filePath(reportFile.getFilePath())
                .templateVersion(reportFile.getTemplateVersion())
                .generatedBy(reportFile.getGeneratedBy())
                .generatedAt(reportFile.getGeneratedAt())
                .build();
    }

    private OhdpDto.ReportOperationLogResponse toReportOperationLogResponse(OhdpReportOperationLog log) {
        return OhdpDto.ReportOperationLogResponse.builder()
                .id(log.getId())
                .projectId(log.getProjectId())
                .operationType(log.getOperationType())
                .operationTypeLabel(OhdpCodeLabels.label(log.getOperationType()))
                .operatorName(log.getOperatorName())
                .operationMessage(log.getOperationMessage())
                .operatedAt(log.getOperatedAt())
                .build();
    }

    private OhdpDto.ReportCenterResponse toReportCenterResponse(OhdpProject project, OhdpReportFile report) {
        boolean generated = report != null;
        return OhdpDto.ReportCenterResponse.builder()
                .projectId(project.getId())
                .projectCode(project.getProjectCode())
                .companyName(project.getCompanyName())
                .projectStatus(project.getStatus())
                .projectStatusLabel(OhdpCodeLabels.label(project.getStatus().name()))
                .reportStatus(generated ? "GENERATED" : "PENDING")
                .reportStatusLabel(generated ? "已生成" : "待生成")
                .reportId(generated ? report.getId() : null)
                .fileName(generated ? report.getFileName() : null)
                .generatedBy(generated ? report.getGeneratedBy() : null)
                .generatedAt(generated ? report.getGeneratedAt() : null)
                .projectCreatedAt(project.getCreatedAt())
                .build();
    }

    private OhdpDto.CompanyProfileResponse toCompanyProfileResponse(OhdpCompanyProfile entity) {
        return OhdpDto.CompanyProfileResponse.builder()
                .id(entity.getId())
                .companyName(entity.getCompanyName())
                .companyAddress(entity.getCompanyAddress())
                .contactName(entity.getContactName())
                .contactPhone(entity.getContactPhone())
                .industryType(entity.getIndustryType())
                .notes(entity.getNotes())
                .build();
    }

    private OhdpDto.PositionTemplateResponse toPositionTemplateResponse(OhdpPositionTemplate entity) {
        return OhdpDto.PositionTemplateResponse.builder()
                .id(entity.getId())
                .jobName(entity.getJobName())
                .shiftSystem(entity.getShiftSystem())
                .workingHours(entity.getWorkingHours())
                .operationType(entity.getOperationType())
                .operationDuration(entity.getOperationDuration())
                .hazards(entity.getHazards())
                .ppe(entity.getPpe())
                .protectionFacilities(entity.getProtectionFacilities())
                .notes(entity.getNotes())
                .build();
    }

    private OhdpDto.FactorTemplateResponse toFactorTemplateResponse(OhdpFactorTemplate entity) {
        return OhdpDto.FactorTemplateResponse.builder()
                .id(entity.getId())
                .factorName(entity.getFactorName())
                .metricType(entity.getMetricType())
                .defaultUnit(entity.getDefaultUnit())
                .defaultLimitValue(entity.getDefaultLimitValue())
                .defaultLimitUnit(entity.getDefaultLimitUnit())
                .detectionBasis(entity.getDetectionBasis())
                .detectionMethod(entity.getDetectionMethod())
                .detectionInstruments(entity.getDetectionInstruments())
                .notes(entity.getNotes())
                .build();
    }

    private OhdpDto.DictionaryItemResponse toDictionaryItemResponse(OhdpDictionaryItem entity) {
        return OhdpDto.DictionaryItemResponse.builder()
                .id(entity.getId())
                .dictType(entity.getDictType())
                .itemCode(entity.getItemCode())
                .itemName(entity.getItemName())
                .sortNo(entity.getSortNo())
                .enabled(entity.getEnabled())
                .build();
    }

    private OhdpDto.StaffProfileResponse toStaffProfileResponse(OhdpStaffProfile entity) {
        return OhdpDto.StaffProfileResponse.builder()
                .id(entity.getId())
                .staffName(entity.getStaffName())
                .staffTitle(entity.getStaffTitle())
                .profession(entity.getProfession())
                .certNo(entity.getCertNo())
                .contactPhone(entity.getContactPhone())
                .notes(entity.getNotes())
                .build();
    }

    private OhdpDto.ReportTextTemplateResponse toReportTextTemplateResponse(OhdpReportTextTemplate entity) {
        return OhdpDto.ReportTextTemplateResponse.builder()
                .id(entity.getId())
                .templateType(entity.getTemplateType())
                .templateName(entity.getTemplateName())
                .templateContent(entity.getTemplateContent())
                .enabled(entity.getEnabled())
                .build();
    }
}

