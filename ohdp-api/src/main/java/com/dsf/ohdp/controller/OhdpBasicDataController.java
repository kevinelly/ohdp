package com.dsf.ohdp.controller;

import com.dsf.ohdp.dto.BasicDataDto;
import com.dsf.ohdp.service.OhdpBasicDataService;
import com.dsf.ohdp.service.OhdpReferenceDocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "OHDP-BasicData")
@RestController
@RequestMapping("/api/ohdp")
@RequiredArgsConstructor
public class OhdpBasicDataController {

    private final OhdpBasicDataService basicDataService;
    private final OhdpReferenceDocumentService referenceDocumentService;

    @PostMapping(value = "/basic/{referenceType}/{referenceId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BasicDataDto.ReferenceDocumentResponse uploadReferenceDocument(
            @PathVariable String referenceType,
            @PathVariable Long referenceId,
            @RequestParam(required = false) String versionLabel,
            @RequestParam(required = false) String sourceUrl,
            @RequestPart("file") MultipartFile file) {
        return referenceDocumentService.upload(referenceType, referenceId, versionLabel, sourceUrl, file);
    }

    @GetMapping("/basic/{referenceType}/{referenceId}/documents")
    public List<BasicDataDto.ReferenceDocumentResponse> listReferenceDocuments(
            @PathVariable String referenceType,
            @PathVariable Long referenceId) {
        return referenceDocumentService.list(referenceType, referenceId);
    }

    @GetMapping("/basic/documents/{documentId}/content")
    public ResponseEntity<Resource> getReferenceDocument(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "false") boolean download) {
        OhdpReferenceDocumentService.Download value = referenceDocumentService.download(documentId);
        String encoded = URLEncoder.encode(value.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = download ? "attachment" : "inline";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename*=UTF-8''" + encoded)
                .contentLength(value.fileSize())
                .contentType(MediaType.parseMediaType(value.contentType()))
                .body(value.resource());
    }

    // ==================== 法律法规 /basic/laws ====================

    @GetMapping("/basic/laws")
    public Page<BasicDataDto.LawResponse> pageLaws(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return basicDataService.pageLaws(keyword, level, page, size);
    }

    @PostMapping("/basic/laws")
    public BasicDataDto.LawResponse createLaw(@Valid @RequestBody BasicDataDto.LawRequest request) {
        return basicDataService.createLaw(request);
    }

    @PutMapping("/basic/laws/{id}")
    public BasicDataDto.LawResponse updateLaw(
            @PathVariable Long id,
            @Valid @RequestBody BasicDataDto.LawRequest request) {
        return basicDataService.updateLaw(id, request);
    }

    @DeleteMapping("/basic/laws/{id}")
    public void deleteLaw(@PathVariable Long id) {
        basicDataService.deleteLaw(id);
    }

    // ==================== 标准规范 /basic/standards ====================

    @GetMapping("/basic/standards")
    public Page<BasicDataDto.StandardResponse> pageStandards(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return basicDataService.pageStandards(keyword, level, page, size);
    }

    @PostMapping("/basic/standards")
    public BasicDataDto.StandardResponse createStandard(@Valid @RequestBody BasicDataDto.StandardRequest request) {
        return basicDataService.createStandard(request);
    }

    @PutMapping("/basic/standards/{id}")
    public BasicDataDto.StandardResponse updateStandard(
            @PathVariable Long id,
            @Valid @RequestBody BasicDataDto.StandardRequest request) {
        return basicDataService.updateStandard(id, request);
    }

    @DeleteMapping("/basic/standards/{id}")
    public void deleteStandard(@PathVariable Long id) {
        basicDataService.deleteStandard(id);
    }

    // ==================== 地区字典 /basic/regions ====================

    @GetMapping("/basic/regions")
    public List<BasicDataDto.RegionResponse> listRegions(
            @RequestParam(required = false) Long parentId) {
        return basicDataService.listRegions(parentId);
    }

    @GetMapping("/basic/regions/{id}")
    public BasicDataDto.RegionResponse getRegion(@PathVariable Long id) {
        return basicDataService.findRegionById(id);
    }

    @PostMapping("/basic/regions")
    public BasicDataDto.RegionResponse createRegion(@Valid @RequestBody BasicDataDto.RegionRequest request) {
        return basicDataService.createRegion(request);
    }

    @PutMapping("/basic/regions/{id}")
    public BasicDataDto.RegionResponse updateRegion(
            @PathVariable Long id,
            @Valid @RequestBody BasicDataDto.RegionRequest request) {
        return basicDataService.updateRegion(id, request);
    }

    @DeleteMapping("/basic/regions/{id}")
    public void deleteRegion(@PathVariable Long id) {
        basicDataService.deleteRegion(id);
    }

    // ==================== 设备管理 /devices ====================

    @GetMapping("/devices")
    public Page<BasicDataDto.DeviceResponse> pageDevices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return basicDataService.pageDevices(keyword, deviceType, status, page, size);
    }

    @PostMapping("/devices")
    public BasicDataDto.DeviceResponse createDevice(@Valid @RequestBody BasicDataDto.DeviceRequest request) {
        return basicDataService.createDevice(request);
    }

    @PutMapping("/devices/{id}")
    public BasicDataDto.DeviceResponse updateDevice(
            @PathVariable Long id,
            @Valid @RequestBody BasicDataDto.DeviceRequest request) {
        return basicDataService.updateDevice(id, request);
    }

    @DeleteMapping("/devices/{id}")
    public void deleteDevice(@PathVariable Long id) {
        basicDataService.deleteDevice(id);
    }
}
