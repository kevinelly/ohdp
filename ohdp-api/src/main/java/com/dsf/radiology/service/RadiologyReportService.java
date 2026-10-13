package com.dsf.radiology.service;

import com.dsf.auth.security.AuthContext;
import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.config.RadiologyStorageProperties;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.entity.RadiologyAttachment;
import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.entity.RadiologyMeasurement;
import com.dsf.radiology.entity.RadiologyProtectionPoint;
import com.dsf.radiology.entity.RadiologyReport;
import com.dsf.radiology.model.RadiologyCaseStatus;
import com.dsf.radiology.repository.RadiologyAttachmentRepository;
import com.dsf.radiology.repository.RadiologyCaseRepository;
import com.dsf.radiology.repository.RadiologyMeasurementRepository;
import com.dsf.radiology.repository.RadiologyProtectionPointRepository;
import com.dsf.radiology.repository.RadiologyReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RadiologyReportService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RadiologyReportRepository reportRepository;
    private final RadiologyCaseRepository caseRepository;
    private final RadiologyMeasurementRepository measurementRepository;
    private final RadiologyProtectionPointRepository protectionPointRepository;
    private final RadiologyAttachmentRepository attachmentRepository;
    private final RadiologyCaseService caseService;
    private final RadiologyStorageProperties properties;
    private final ObjectMapper objectMapper;
    private final RadiologyReportComposer composer;

    @Transactional
    public RadiologyDto.ReportResponse generate(Long caseId) {
        caseService.requirePermission("RADIOLOGY:REPORT:GENERATE");
        RadiologyCase live = caseService.requireCase(caseId);
        RadiologyCase entity=live;
        if (entity.getStatus() != RadiologyCaseStatus.APPROVED && entity.getStatus() != RadiologyCaseStatus.REPORTED) {
            throw new BusinessException("报告仅可在批准后生成");
        }
        if (!StringUtils.hasText(live.getApprovedSnapshotJson()))
            throw new BusinessException("历史案例未保存批准快照，请创建修订版重新计算和审批；原报告仍可下载");
        RadiologyCaseService.ApprovedSnapshot approved;
        try { approved=objectMapper.readValue(live.getApprovedSnapshotJson(),RadiologyCaseService.ApprovedSnapshot.class); }
        catch (JsonProcessingException ex) { throw new BusinessException("批准快照读取失败"); }
        entity=approved.inspectionCase();
        List<RadiologyMeasurement> measurements=approved.measurements();
        List<RadiologyProtectionPoint> points=approved.protectionPoints();
        List<RadiologyAttachment> attachments=approved.attachments();
        if (!templateHash(entity.getModality()).equals(approved.templateSha256()))
            throw new BusinessException("模板与批准版本不一致，请恢复批准时模板后生成报告");
        String reportNo=entity.getSampleAcceptanceNo();
        if (!StringUtils.hasText(reportNo)) throw new BusinessException("批准快照缺少受理编号");
        String fileName = "检测报告--"+safeName(reportNo)+"["+entity.getModality()+"-"+safeName(entity.getInspectedUnitName())+"]."+LocalDateTime.now().format(FILE_TIME)+"-R"+entity.getRevisionNo()+"-"+java.util.UUID.randomUUID().toString().substring(0,8)+".docx";
        Path root = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
        Path directory = root.resolve(String.valueOf(caseId)).resolve("reports").normalize();
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(root)) throw new BusinessException("报告存储路径不合法");
        try {
            Files.createDirectories(directory);
            Files.write(target,composer.compose(entity,measurements,points,attachments,false));
        } catch (IOException ex) {
            throw new BusinessException("报告生成失败：" + ex.getMessage());
        }

        String snapshot = live.getApprovedSnapshotJson();
        RadiologyReport report = new RadiologyReport();
        report.setCaseId(caseId);
        report.setReportNo(reportNo);
        report.setSnapshotJson(snapshot);
        report.setFileName(fileName);
        report.setStoragePath(target.toString());
        report.setSha256(sha256(target));
        report.setGeneratedBy(AuthContext.get() == null ? "system" : AuthContext.get().getUsername());
        report.setGeneratedAt(LocalDateTime.now());
        report = reportRepository.save(report);
        live.setStatus(RadiologyCaseStatus.REPORTED);
        caseRepository.save(live);
        return toResponse(report);
    }

    @Transactional(readOnly = true)
    public List<RadiologyDto.ReportResponse> list(Long caseId) {
        caseService.requireCase(caseId);
        return reportRepository.findByCaseIdAndDeletedFalseOrderByGeneratedAtDesc(caseId).stream()
                .map(RadiologyReportService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Download download(Long id) {
        RadiologyReport report = reportRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("报告不存在"));
        Path root = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
        Path path = Path.of(report.getStoragePath()).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new BusinessException("报告路径越界");
        if (!Files.isRegularFile(path)) throw new BusinessException("报告文件不存在");
        return new Download(new FileSystemResource(path), report.getFileName(), Files.exists(path) ? path.toFile().length() : 0);
    }

    @Transactional(readOnly=true)
    public byte[] preview(Long caseId) {
        var live=caseService.requireCase(caseId);
        if(live.getStatus()==RadiologyCaseStatus.APPROVED || live.getStatus()==RadiologyCaseStatus.REPORTED) {
            var existing=reportRepository.findByCaseIdAndDeletedFalseOrderByGeneratedAtDesc(caseId);
            if(!existing.isEmpty()) {
                try(var input=download(existing.get(0).getId()).resource().getInputStream()) {return input.readAllBytes();}
                catch(IOException ex){throw new BusinessException("报告预览读取失败");}
            }
            if(!StringUtils.hasText(live.getApprovedSnapshotJson()))throw new BusinessException("该历史案例未保存批准快照，请创建修订版");
            try {
                var approved=objectMapper.readValue(live.getApprovedSnapshotJson(),RadiologyCaseService.ApprovedSnapshot.class);
                if(!templateHash(live.getModality()).equals(approved.templateSha256()))throw new BusinessException("批准模板版本已变更，请创建修订版；原报告仍可下载");
                return composer.compose(approved.inspectionCase(),approved.measurements(),approved.protectionPoints(),approved.attachments(),false);
            }catch(JsonProcessingException ex){throw new BusinessException("批准快照读取失败");}
        }
        return composer.compose(live,measurementRepository.findByCaseIdAndDeletedFalseOrderByItemCodeAscSortNoAsc(caseId),
                protectionPointRepository.findByCaseIdAndDeletedFalseOrderBySortNoAsc(caseId),
                attachmentRepository.findByCaseIdAndDeletedFalseOrderByUploadedAtDesc(caseId),true);
    }

    @Transactional
    public void deleteReport(Long id,String reason) {
        caseService.requirePermission("RADIOLOGY:REPORT:GENERATE");
        if(!StringUtils.hasText(reason))throw new BusinessException("删除报告须填写原因");
        var report=reportRepository.findByIdAndDeletedFalse(id).orElseThrow(()->new BusinessException("报告不存在"));
        report.setDeleted(true);report.setDeletionReason(reason.trim());
        report.setDeletedBy(AuthContext.get()==null?"system":AuthContext.get().getUsername());
        report.setDeletedAt(LocalDateTime.now());reportRepository.save(report);
        // Keep the file and approved snapshot for audit; deletion only removes the active listing.
    }

    public static String templatePath(com.dsf.radiology.model.RadiologyModality modality) {
        return "templates/radiology/"+modality.name()+"-v20261013-r2.docx";
    }
    public static String templateHash(com.dsf.radiology.model.RadiologyModality modality) {
        try (var input=new org.springframework.core.io.ClassPathResource(templatePath(modality)).getInputStream()) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        } catch (Exception ex) { throw new BusinessException("批准模板不可用"); }
    }
    private String safeName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path); DigestInputStream stream = new DigestInputStream(input, digest)) {
                stream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException("报告校验失败：" + ex.getMessage());
        }
    }

    public static RadiologyDto.ReportResponse toResponse(RadiologyReport value) {
        return new RadiologyDto.ReportResponse(
                value.getId(), value.getCaseId(), value.getReportNo(), value.getFileName(), value.getSha256(),
                value.getGeneratedBy(), value.getGeneratedAt());
    }

    public record Download(Resource resource, String fileName, long fileSize) {
    }

}
