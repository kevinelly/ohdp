package com.dsf.radiology.service;

import com.dsf.auth.security.AuthContext;
import com.dsf.common.exception.BusinessException;
import com.dsf.radiology.config.RadiologyStorageProperties;
import com.dsf.radiology.dto.RadiologyDto;
import com.dsf.radiology.entity.RadiologyAttachment;
import com.dsf.radiology.entity.RadiologyCase;
import com.dsf.radiology.repository.RadiologyAttachmentRepository;
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
public class RadiologyAttachmentService {

    private static final Set<String> CATEGORIES = Set.of("POINT_MAP", "CERTIFICATE", "PHANTOM_MANUAL", "OTHER");
    private static final Set<String> ALLOWED_MEDIA = Set.of(
            "image/png", "image/jpeg", "image/webp", "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final RadiologyAttachmentRepository repository;
    private final RadiologyCaseService caseService;
    private final RadiologyStorageProperties properties;

    @Transactional
    public RadiologyDto.AttachmentResponse upload(Long caseId, String category, MultipartFile file) {
        RadiologyCase entity = caseService.requireCase(caseId);
        caseService.assertEditable(entity);
        String normalizedCategory = category == null ? "OTHER" : category.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(normalizedCategory)) throw new BusinessException("不支持的附件类别：" + category);
        if (file == null || file.isEmpty()) throw new BusinessException("请选择非空文件");
        if (file.getSize() > properties.getMaxUploadBytes()) throw new BusinessException("附件超过允许大小");
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename());
        if (original.contains("..")) throw new BusinessException("附件名称不合法");
        String mediaType = file.getContentType() == null ? "application/octet-stream" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MEDIA.contains(mediaType)) throw new BusinessException("不支持的附件类型：" + mediaType);
        if (normalizedCategory.equals("POINT_MAP") && !Set.of("image/png", "image/jpeg").contains(mediaType)) {
            throw new BusinessException("点位图必须是 PNG 或 JPEG 图片");
        }

        String extension = extension(original);
        String storedName = UUID.randomUUID() + extension;
        Path root = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
        Path directory = root.resolve(String.valueOf(caseId)).resolve("attachments").normalize();
        Path target = directory.resolve(storedName).normalize();
        if (!target.startsWith(root)) throw new BusinessException("附件存储路径不合法");
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException("附件保存失败：" + ex.getMessage());
        }

        RadiologyAttachment attachment = new RadiologyAttachment();
        attachment.setCaseId(caseId);
        attachment.setCategory(normalizedCategory);
        attachment.setOriginalName(original);
        attachment.setStoredName(storedName);
        attachment.setStoragePath(target.toString());
        attachment.setMediaType(mediaType);
        attachment.setFileSize(file.getSize());
        attachment.setSha256(sha256(target));
        attachment.setUploadedBy(AuthContext.get() == null ? "system" : AuthContext.get().getUsername());
        attachment.setUploadedAt(LocalDateTime.now());
        var saved=repository.save(attachment);
        caseService.invalidate(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RadiologyDto.AttachmentResponse> list(Long caseId) {
        caseService.requireCase(caseId);
        return repository.findByCaseIdAndDeletedFalseOrderByUploadedAtDesc(caseId).stream().map(RadiologyAttachmentService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Download download(Long attachmentId) {
        RadiologyAttachment attachment = requireAttachment(attachmentId);
        Path path = verifiedPath(attachment.getStoragePath());
        if (!Files.isRegularFile(path)) throw new BusinessException("附件文件不存在");
        return new Download(new FileSystemResource(path), attachment.getOriginalName(), attachment.getMediaType(), attachment.getFileSize());
    }

    @Transactional
    public void delete(Long attachmentId) {
        RadiologyAttachment attachment = requireAttachment(attachmentId);
        RadiologyCase entity = caseService.requireCase(attachment.getCaseId());
        caseService.assertEditable(entity);
        Path path = verifiedPath(attachment.getStoragePath());
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new BusinessException("附件文件删除失败：" + ex.getMessage());
        }
        attachment.setDeleted(true);
        repository.save(attachment);
        caseService.invalidate(entity);
    }

    private RadiologyAttachment requireAttachment(Long id) {
        return repository.findByIdAndDeletedFalse(id).orElseThrow(() -> new BusinessException("附件不存在"));
    }

    private Path verifiedPath(String value) {
        Path root = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new BusinessException("附件路径越界");
        return path;
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path); DigestInputStream stream = new DigestInputStream(input, digest)) {
                stream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException("附件校验失败：" + ex.getMessage());
        }
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        if (index < 0 || name.length() - index > 10) return "";
        return name.substring(index).toLowerCase(Locale.ROOT);
    }

    public static RadiologyDto.AttachmentResponse toResponse(RadiologyAttachment value) {
        return new RadiologyDto.AttachmentResponse(
                value.getId(), value.getCaseId(), value.getCategory(), value.getOriginalName(), value.getMediaType(),
                value.getFileSize(), value.getSha256(), value.getUploadedBy(), value.getUploadedAt());
    }

    public record Download(Resource resource, String fileName, String mediaType, long fileSize) {
    }
}
