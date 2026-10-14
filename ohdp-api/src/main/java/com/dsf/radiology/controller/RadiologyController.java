package com.dsf.radiology.controller;

import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.model.RadiologyCaseStatus;
import com.dsf.radiology.model.RadiologyModality;
import com.dsf.radiology.service.RadiologyAttachmentService;
import com.dsf.radiology.service.RadiologyCaseService;
import com.dsf.radiology.service.RadiologyCatalogService;
import com.dsf.radiology.service.RadiologyImportService;
import com.dsf.radiology.service.RadiologyReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Radiology-DR-CT")
@RestController
@RequestMapping("/api/radiology")
@RequiredArgsConstructor
public class RadiologyController {

    private final RadiologyCaseService caseService;
    private final RadiologyCatalogService catalogService;
    private final RadiologyImportService importService;
    private final RadiologyAttachmentService attachmentService;
    private final RadiologyReportService reportService;
    private final com.dsf.radiology.service.RadiologyPdfPreviewService pdfPreviewService;
    private final com.dsf.radiology.service.RadiologyBatchImportService batchImportService;

    @GetMapping("/batch-import-template")
    public ResponseEntity<Resource> batchTemplate() {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=radiology-batch.xlsx")
            .body(new ByteArrayResource(batchImportService.template()));
    }
    @PostMapping("/batch-import")
    public com.dsf.radiology.service.RadiologyBatchImportService.Preview batchImport(@RequestPart("file") MultipartFile file,@RequestParam(defaultValue="true") boolean dryRun) {
        return batchImportService.importWorkbook(file,dryRun);
    }

    @GetMapping("/catalog/{modality}")
    public List<RadiologyCatalogService.ItemDefinition> catalog(@PathVariable RadiologyModality modality) {
        return catalogService.getCatalog(modality);
    }

    @PostMapping("/cases")
    public RadiologyDto.CaseDetail createCase(@Valid @RequestBody RadiologyDto.CaseRequest request) {
        return caseService.createCase(request);
    }

    @PutMapping("/cases/{id}")
    public RadiologyDto.CaseDetail updateCase(@PathVariable Long id, @Valid @RequestBody RadiologyDto.CaseRequest request) {
        return caseService.updateCase(id, request);
    }

    @GetMapping("/cases/{id}")
    public RadiologyDto.CaseDetail getCase(@PathVariable Long id) {
        return caseService.getCase(id);
    }

    @GetMapping("/cases")
    public Page<RadiologyDto.CaseSummary> listCases(
            @RequestParam(required = false) RadiologyModality modality,
            @RequestParam(required = false) RadiologyCaseStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required=false) List<RadiologyModality> modalities,
            @RequestParam(required=false) Integer year,
            @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required=false) String city,
            @RequestParam(required=false) String county,
            @RequestParam(required=false) String receipt,
            @RequestParam(required=false) String company,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caseService.searchCases(modality,modalities,status,keyword,year,from,to,city,county,receipt,company,page,size);
    }

    @DeleteMapping("/cases/{id}")
    public void deleteCase(@PathVariable Long id) {
        caseService.deleteCase(id);
    }

    @PutMapping("/cases/{id}/measurements")
    public RadiologyDto.CaseDetail saveMeasurements(
            @PathVariable Long id, @Valid @RequestBody RadiologyDto.MeasurementBatchRequest request) {
        return caseService.saveMeasurements(id, request);
    }

    @PostMapping("/cases/{id}/calculate")
    public RadiologyDto.CalculationResponse calculate(@PathVariable Long id) {
        return caseService.calculate(id);
    }

    @PutMapping("/cases/{id}/protection-points")
    public List<RadiologyDto.ProtectionPointResponse> saveProtectionPoints(
            @PathVariable Long id, @Valid @RequestBody RadiologyDto.ProtectionPointBatchRequest request) {
        return caseService.saveProtectionPoints(id, request);
    }

    @DeleteMapping("/cases/{caseId}/protection-points/{pointId}")
    public void deleteProtectionPoint(@PathVariable Long caseId, @PathVariable Long pointId) {
        caseService.deleteProtectionPoint(caseId, pointId);
    }

    @PostMapping(value = "/cases/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RadiologyDto.ImportPreview importWorkbook(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "PERFORMANCE") String type,
            @RequestParam(required = false) String sheetName,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return importService.importWorkbook(id, file, type, sheetName, dryRun);
    }

    @GetMapping("/templates/{modality}/{type}")
    public ResponseEntity<Resource> downloadImportTemplate(
            @PathVariable RadiologyModality modality, @PathVariable String type) {
        RadiologyImportService.TemplateFile template = importService.createTemplate(modality, type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(template.fileName()))
                .contentLength(template.bytes().length)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(template.bytes()));
    }

    @PostMapping(value = "/cases/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RadiologyDto.AttachmentResponse uploadAttachment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "OTHER") String category,
            @RequestPart("file") MultipartFile file) {
        return attachmentService.upload(id, category, file);
    }

    @PutMapping(value="/attachments/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public RadiologyDto.AttachmentResponse replaceAttachment(@PathVariable Long id,@RequestPart("file") MultipartFile file) {
        return attachmentService.replace(id,file);
    }

    @GetMapping("/cases/{id}/attachments")
    public List<RadiologyDto.AttachmentResponse> listAttachments(@PathVariable Long id) {
        return attachmentService.list(id);
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        RadiologyAttachmentService.Download download = attachmentService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(download.fileName()))
                .contentLength(download.fileSize())
                .contentType(MediaType.parseMediaType(download.mediaType()))
                .body(download.resource());
    }

    @DeleteMapping("/attachments/{id}")
    public void deleteAttachment(@PathVariable Long id) {
        attachmentService.delete(id);
    }

    @PostMapping("/cases/{id}/workflow/submit")
    public RadiologyDto.CaseDetail submit(@PathVariable Long id, @RequestBody RadiologyDto.WorkflowRequest request) {
        return caseService.submitForReview(id, request);
    }

    @PostMapping("/cases/{id}/workflow/review")
    public RadiologyDto.CaseDetail review(@PathVariable Long id, @RequestBody RadiologyDto.WorkflowRequest request) {
        return caseService.review(id, request);
    }

    @PostMapping("/cases/{id}/workflow/approve")
    public RadiologyDto.CaseDetail approve(@PathVariable Long id, @RequestBody RadiologyDto.WorkflowRequest request) {
        return caseService.approve(id, request);
    }

    @PostMapping("/cases/{id}/workflow/return")
    public RadiologyDto.CaseDetail returnForCorrection(@PathVariable Long id, @RequestBody RadiologyDto.WorkflowRequest request) {
        return caseService.returnForCorrection(id,request);
    }

    @PostMapping("/cases/{id}/workflow/revision")
    public RadiologyDto.CaseDetail createRevision(@PathVariable Long id) {
        return caseService.reopenAsRevision(id);
    }

    @GetMapping("/cases/{id}/suggested-conclusion")
    public java.util.Map<String,String> suggestedConclusion(@PathVariable Long id) {
        return java.util.Map.of("text",caseService.suggestedConclusion(caseService.requireCase(id)));
    }

    @GetMapping("/rules")
    public List<RadiologyDto.RuleResponse> listRules(@RequestParam RadiologyModality modality) {
        return caseService.listRules(modality);
    }

    @PostMapping("/rules")
    public RadiologyDto.RuleResponse createRule(@Valid @RequestBody RadiologyDto.RuleRequest request) {
        return caseService.saveRule(null, request);
    }

    @PutMapping("/rules/{id}")
    public RadiologyDto.RuleResponse updateRule(@PathVariable Long id, @Valid @RequestBody RadiologyDto.RuleRequest request) {
        return caseService.saveRule(id, request);
    }

    @DeleteMapping("/rules/{id}")
    public void deleteRule(@PathVariable Long id) {
        caseService.deleteRule(id);
    }

    @GetMapping("/cases/{id}/report-preview")
    public ResponseEntity<Resource> previewReport(@PathVariable Long id,@RequestParam(defaultValue="docx") String format) {
        byte[] bytes=reportService.preview(id);
        boolean pdf="pdf".equalsIgnoreCase(format);if(pdf)bytes=pdfPreviewService.convert(bytes);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(pdf?"application/pdf":"application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header(HttpHeaders.CACHE_CONTROL,"no-store").contentLength(bytes.length).body(new ByteArrayResource(bytes));
    }
    @GetMapping("/preview-capabilities")
    public java.util.Map<String,Boolean> previewCapabilities(){return java.util.Map.of("pdf",pdfPreviewService.available());}
    @GetMapping("/cases/{id}/report-preview-pages")
    public ResponseEntity<List<String>> previewPages(@PathVariable Long id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store")
            .body(pdfPreviewService.renderPages(pdfPreviewService.convert(reportService.preview(id))));
    }
    @DeleteMapping("/reports/{id}")
    public void deleteReport(@PathVariable Long id,@RequestParam String reason) {reportService.deleteReport(id,reason);}

    @PostMapping("/cases/{id}/reports")
    public RadiologyDto.ReportResponse generateReport(@PathVariable Long id) {
        return reportService.generate(id);
    }

    @GetMapping("/cases/{id}/reports")
    public List<RadiologyDto.ReportResponse> listReports(@PathVariable Long id) {
        return reportService.list(id);
    }

    @GetMapping("/reports/{id}/download")
    public ResponseEntity<Resource> downloadReport(@PathVariable Long id) {
        RadiologyReportService.Download download = reportService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(download.fileName()))
                .contentLength(download.fileSize())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(download.resource());
    }

    private String attachment(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
