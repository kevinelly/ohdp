package com.dsf.ohdp.service;

import com.dsf.auth.security.AuthContext;
import com.dsf.common.exception.BusinessException;
import com.dsf.ohdp.config.OhdpReferenceDocumentProperties;
import com.dsf.ohdp.dto.BasicDataDto;
import com.dsf.ohdp.entity.OhdpLaw;
import com.dsf.ohdp.entity.OhdpReferenceDocumentVersion;
import com.dsf.ohdp.entity.OhdpStandard;
import com.dsf.ohdp.repository.OhdpLawRepository;
import com.dsf.ohdp.repository.OhdpReferenceDocumentVersionRepository;
import com.dsf.ohdp.repository.OhdpStandardRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class OhdpReferenceDocumentService {

    public static final String LAW = "LAW";
    public static final String STANDARD = "STANDARD";
    private static final Set<String> TYPES = Set.of(LAW, STANDARD);
    private static final Set<String> EXTENSIONS = Set.of(".pdf", ".doc", ".docx");
    private static final Set<String> MEDIA_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/octet-stream");

    private final OhdpReferenceDocumentVersionRepository repository;
    private final OhdpLawRepository lawRepository;
    private final OhdpStandardRepository standardRepository;
    private final OhdpReferenceDocumentProperties properties;

    @Transactional
    public BasicDataDto.ReferenceDocumentResponse upload(
            String referenceType,
            Long referenceId,
            String versionLabel,
            String sourceUrl,
            MultipartFile file) {
        String type = normalizeType(referenceType);
        validateOwner(type, referenceId);
        validateFile(file);

        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "reference-document" : file.getOriginalFilename());
        if (originalName.contains("..")) throw new BusinessException("文件名不合法");
        String extension = extension(originalName);
        String storedName = UUID.randomUUID() + extension;
        List<OhdpReferenceDocumentVersion> existing = repository
                .findByReferenceTypeAndReferenceIdAndDeletedFalseOrderByVersionNoDesc(type, referenceId);
        int versionNo = existing.isEmpty() ? 1 : existing.get(0).getVersionNo() + 1;

        Path root = root();
        Path directory = root.resolve(type.toLowerCase(Locale.ROOT)).resolve(String.valueOf(referenceId)).normalize();
        Path target = directory.resolve(storedName).normalize();
        if (!target.startsWith(root)) throw new BusinessException("文件存储路径不合法");
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException("法规原文保存失败：" + ex.getMessage());
        }

        OhdpReferenceDocumentVersion entity = new OhdpReferenceDocumentVersion();
        entity.setReferenceType(type);
        entity.setReferenceId(referenceId);
        entity.setVersionNo(versionNo);
        entity.setVersionLabel(StringUtils.hasText(versionLabel) ? versionLabel.trim() : "第" + versionNo + "版");
        entity.setOriginalName(originalName);
        entity.setStoredName(storedName);
        entity.setStoragePath(target.toString());
        entity.setContentType(resolveContentType(file.getContentType(), extension));
        entity.setFileSize(file.getSize());
        entity.setSha256(sha256(target));
        entity.setSourceUrl(StringUtils.hasText(sourceUrl) ? sourceUrl.trim() : null);
        entity.setUploadedBy(AuthContext.get() == null ? "system" : AuthContext.get().getUsername());
        entity.setUploadedAt(LocalDateTime.now());
        entity = repository.save(entity);

        if (LAW.equals(type)) {
            OhdpLaw law = lawRepository.findByIdAndDeletedFalse(referenceId).orElseThrow();
            law.setFileId(entity.getId());
            lawRepository.save(law);
        } else {
            OhdpStandard standard = standardRepository.findByIdAndDeletedFalse(referenceId).orElseThrow();
            standard.setFileId(entity.getId());
            standardRepository.save(standard);
        }
        return toResponse(entity, true);
    }

    @Transactional(readOnly = true)
    public List<BasicDataDto.ReferenceDocumentResponse> list(String referenceType, Long referenceId) {
        String type = normalizeType(referenceType);
        validateOwner(type, referenceId);
        List<OhdpReferenceDocumentVersion> items = repository
                .findByReferenceTypeAndReferenceIdAndDeletedFalseOrderByVersionNoDesc(type, referenceId);
        Long latestId = items.isEmpty() ? null : items.get(0).getId();
        return items.stream().map(item -> toResponse(item, item.getId().equals(latestId))).toList();
    }

    @Transactional(readOnly = true)
    public Optional<BasicDataDto.ReferenceDocumentResponse> latest(String referenceType, Long referenceId) {
        return repository.findFirstByReferenceTypeAndReferenceIdAndDeletedFalseOrderByVersionNoDesc(
                        normalizeType(referenceType), referenceId)
                .map(item -> toResponse(item, true));
    }

    @Transactional(readOnly = true)
    public Download download(Long documentId) {
        OhdpReferenceDocumentVersion entity = repository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new BusinessException("法规原文不存在"));
        Path path = Path.of(entity.getStoragePath()).toAbsolutePath().normalize();
        if (!path.startsWith(root())) throw new BusinessException("法规原文路径越界");
        if (!Files.isRegularFile(path)) throw new BusinessException("法规原文文件不存在");
        return new Download(new FileSystemResource(path), entity.getOriginalName(), entity.getContentType(), entity.getFileSize());
    }

    private void validateOwner(String type, Long referenceId) {
        if (referenceId == null) throw new BusinessException("法规或标准编号不能为空");
        boolean exists = LAW.equals(type)
                ? lawRepository.findByIdAndDeletedFalse(referenceId).isPresent()
                : standardRepository.findByIdAndDeletedFalse(referenceId).isPresent();
        if (!exists) throw new BusinessException(LAW.equals(type) ? "法律法规不存在" : "标准规范不存在");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择非空的法规原文");
        if (file.getSize() > properties.getMaxUploadBytes()) throw new BusinessException("法规原文超过允许大小");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extension(name);
        if (!EXTENSIONS.contains(extension)) throw new BusinessException("法规原文仅支持 PDF、DOC 或 DOCX");
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!MEDIA_TYPES.contains(contentType)) throw new BusinessException("不支持的法规原文类型：" + contentType);
    }

    private String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new BusinessException("不支持的法规资料类型：" + value);
        return type;
    }

    private Path root() {
        return Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index).toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(String contentType, String extension) {
        if (contentType != null && !contentType.isBlank() && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType.toLowerCase(Locale.ROOT);
        }
        return switch (extension) {
            case ".pdf" -> "application/pdf";
            case ".doc" -> "application/msword";
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path); DigestInputStream stream = new DigestInputStream(input, digest)) {
                stream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException("法规原文校验失败：" + ex.getMessage());
        }
    }

    public static BasicDataDto.ReferenceDocumentResponse toResponse(
            OhdpReferenceDocumentVersion value, boolean current) {
        return BasicDataDto.ReferenceDocumentResponse.builder()
                .id(value.getId())
                .referenceType(value.getReferenceType())
                .referenceId(value.getReferenceId())
                .versionNo(value.getVersionNo())
                .versionLabel(value.getVersionLabel())
                .originalName(value.getOriginalName())
                .contentType(value.getContentType())
                .fileSize(value.getFileSize())
                .sha256(value.getSha256())
                .sourceUrl(value.getSourceUrl())
                .uploadedBy(value.getUploadedBy())
                .uploadedAt(value.getUploadedAt())
                .current(current)
                .build();
    }

    public record Download(Resource resource, String fileName, String contentType, long fileSize) {
    }
}
