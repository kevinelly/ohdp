package com.dsf.system.service;

import com.dsf.system.dto.SystemDto;
import com.dsf.system.entity.SysConfig;
import com.dsf.system.entity.SysDepartment;
import com.dsf.system.entity.SysFile;
import com.dsf.system.entity.SysLog;
import com.dsf.system.entity.SysNotice;
import com.dsf.system.entity.SysPost;
import com.dsf.system.repository.SysConfigRepository;
import com.dsf.system.repository.SysDepartmentRepository;
import com.dsf.system.repository.SysFileRepository;
import com.dsf.system.repository.SysLogRepository;
import com.dsf.system.repository.SysNoticeRepository;
import com.dsf.system.repository.SysPostRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemManagementServiceImpl implements SystemManagementService {

    private final SysDepartmentRepository departmentRepository;
    private final SysPostRepository postRepository;
    private final SysNoticeRepository noticeRepository;
    private final SysLogRepository logRepository;
    private final SysFileRepository fileRepository;
    private final SysConfigRepository configRepository;

    // ==================== Department ====================

    @Override
    public List<SystemDto.DepartmentResponse> listDepartments(Boolean enabled) {
        List<SysDepartment> list;
        if (enabled != null) {
            list = departmentRepository.findAllByEnabledAndDeletedFalseOrderBySortNoAsc(enabled);
        } else {
            list = departmentRepository.findAllByDeletedFalseOrderBySortNoAsc();
        }
        return list.stream().map(this::toDepartmentResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SystemDto.DepartmentResponse createDepartment(SystemDto.DepartmentRequest request) {
        SysDepartment entity = new SysDepartment();
        applyDepartmentRequest(entity, request);
        return toDepartmentResponse(departmentRepository.save(entity));
    }

    @Override
    @Transactional
    public SystemDto.DepartmentResponse updateDepartment(Long id, SystemDto.DepartmentRequest request) {
        SysDepartment entity = departmentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("部门不存在: " + id));
        applyDepartmentRequest(entity, request);
        return toDepartmentResponse(departmentRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteDepartment(Long id) {
        SysDepartment entity = departmentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("部门不存在: " + id));
        List<SysDepartment> children = departmentRepository.findByParentIdAndDeletedFalseOrderBySortNoAsc(id);
        if (!children.isEmpty()) {
            throw new RuntimeException("该部门下存在子部门，无法删除");
        }
        entity.setDeleted(true);
        departmentRepository.save(entity);
    }

    private void applyDepartmentRequest(SysDepartment entity, SystemDto.DepartmentRequest request) {
        entity.setParentId(request.getParentId());
        entity.setDeptName(request.getDeptName());
        entity.setDeptCode(request.getDeptCode());
        entity.setSortNo(request.getSortNo() != null ? request.getSortNo() : 0);
        entity.setLeaderName(request.getLeaderName());
        entity.setContactPhone(request.getContactPhone());
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
    }

    private SystemDto.DepartmentResponse toDepartmentResponse(SysDepartment e) {
        return SystemDto.DepartmentResponse.builder()
                .id(e.getId())
                .parentId(e.getParentId())
                .deptName(e.getDeptName())
                .deptCode(e.getDeptCode())
                .sortNo(e.getSortNo())
                .leaderName(e.getLeaderName())
                .contactPhone(e.getContactPhone())
                .enabled(e.getEnabled())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    // ==================== Post ====================

    @Override
    public Page<SystemDto.PostResponse> pagePosts(SystemDto.PostQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.ASC, "sortNo"));
        Page<SysPost> page;
        boolean hasKeyword = query.getKeyword() != null && !query.getKeyword().isBlank();
        boolean hasEnabled = query.getEnabled() != null;
        if (hasKeyword && hasEnabled) {
            page = postRepository.findByPostNameContainingAndEnabledAndDeletedFalse(
                    query.getKeyword(), query.getEnabled(), pageable);
        } else if (hasKeyword) {
            page = postRepository.findByPostNameContainingAndDeletedFalse(query.getKeyword(), pageable);
        } else if (hasEnabled) {
            page = postRepository.findByEnabledAndDeletedFalse(query.getEnabled(), pageable);
        } else {
            page = postRepository.findByDeletedFalse(pageable);
        }
        return page.map(this::toPostResponse);
    }

    @Override
    public List<SystemDto.PostResponse> listAllPosts(Boolean enabled) {
        List<SysPost> list;
        if (enabled != null) {
            list = postRepository.findAllByEnabledAndDeletedFalseOrderBySortNoAsc(enabled);
        } else {
            list = postRepository.findAllByDeletedFalseOrderBySortNoAsc();
        }
        return list.stream().map(this::toPostResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SystemDto.PostResponse createPost(SystemDto.PostRequest request) {
        SysPost entity = new SysPost();
        applyPostRequest(entity, request);
        return toPostResponse(postRepository.save(entity));
    }

    @Override
    @Transactional
    public SystemDto.PostResponse updatePost(Long id, SystemDto.PostRequest request) {
        SysPost entity = postRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("岗位不存在: " + id));
        applyPostRequest(entity, request);
        return toPostResponse(postRepository.save(entity));
    }

    @Override
    @Transactional
    public void deletePost(Long id) {
        SysPost entity = postRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("岗位不存在: " + id));
        entity.setDeleted(true);
        postRepository.save(entity);
    }

    private void applyPostRequest(SysPost entity, SystemDto.PostRequest request) {
        entity.setPostName(request.getPostName());
        entity.setPostCode(request.getPostCode());
        entity.setSortNo(request.getSortNo() != null ? request.getSortNo() : 0);
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        entity.setNotes(request.getNotes());
    }

    private SystemDto.PostResponse toPostResponse(SysPost e) {
        return SystemDto.PostResponse.builder()
                .id(e.getId())
                .postName(e.getPostName())
                .postCode(e.getPostCode())
                .sortNo(e.getSortNo())
                .enabled(e.getEnabled())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    // ==================== Notice ====================

    @Override
    public Page<SystemDto.NoticeResponse> pageNotices(SystemDto.NoticeQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SysNotice> page;
        boolean hasKeyword = query.getKeyword() != null && !query.getKeyword().isBlank();
        boolean hasType = query.getNoticeType() != null && !query.getNoticeType().isBlank();
        if (hasKeyword && hasType) {
            page = noticeRepository.findByTitleContainingAndNoticeTypeAndDeletedFalse(
                    query.getKeyword(), query.getNoticeType(), pageable);
        } else if (hasKeyword) {
            page = noticeRepository.findByTitleContainingAndDeletedFalse(query.getKeyword(), pageable);
        } else if (hasType) {
            page = noticeRepository.findByNoticeTypeAndDeletedFalse(query.getNoticeType(), pageable);
        } else {
            page = noticeRepository.findByDeletedFalse(pageable);
        }
        return page.map(this::toNoticeResponse);
    }

    @Override
    @Transactional
    public SystemDto.NoticeResponse createNotice(SystemDto.NoticeRequest request) {
        SysNotice entity = new SysNotice();
        applyNoticeRequest(entity, request);
        return toNoticeResponse(noticeRepository.save(entity));
    }

    @Override
    @Transactional
    public SystemDto.NoticeResponse updateNotice(Long id, SystemDto.NoticeRequest request) {
        SysNotice entity = noticeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("通知不存在: " + id));
        applyNoticeRequest(entity, request);
        return toNoticeResponse(noticeRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteNotice(Long id) {
        SysNotice entity = noticeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("通知不存在: " + id));
        entity.setDeleted(true);
        noticeRepository.save(entity);
    }

    private void applyNoticeRequest(SysNotice entity, SystemDto.NoticeRequest request) {
        entity.setTitle(request.getTitle());
        entity.setContent(request.getContent());
        entity.setNoticeType(request.getNoticeType());
        entity.setPublisher(request.getPublisher());
        entity.setPublishTime(request.getPublishTime());
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
    }

    private SystemDto.NoticeResponse toNoticeResponse(SysNotice e) {
        return SystemDto.NoticeResponse.builder()
                .id(e.getId())
                .title(e.getTitle())
                .content(e.getContent())
                .noticeType(e.getNoticeType())
                .publisher(e.getPublisher())
                .publishTime(e.getPublishTime())
                .enabled(e.getEnabled())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    // ==================== Log ====================

    @Override
    public Page<SystemDto.LogResponse> pageLogs(SystemDto.LogQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize());
        return logRepository.queryLogs(
                query.getUserName(),
                query.getModule(),
                query.getResultType(),
                query.getStartTime(),
                query.getEndTime(),
                pageable
        ).map(this::toLogResponse);
    }

    private SystemDto.LogResponse toLogResponse(SysLog e) {
        return SystemDto.LogResponse.builder()
                .id(e.getId())
                .userName(e.getUserName())
                .operation(e.getOperation())
                .method(e.getMethod())
                .params(e.getParams())
                .resultType(e.getResultType())
                .ip(e.getIp())
                .costTime(e.getCostTime())
                .module(e.getModule())
                .createdAt(e.getCreatedAt())
                .build();
    }

    // ==================== File ====================

    @Override
    public Page<SystemDto.FileResponse> pageFiles(SystemDto.FileQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SysFile> page;
        boolean hasBizType = query.getBizType() != null && !query.getBizType().isBlank();
        boolean hasBizId = query.getBizId() != null;
        if (hasBizType && hasBizId) {
            page = fileRepository.findByBizTypeAndBizIdAndDeletedFalse(query.getBizType(), query.getBizId(), pageable);
        } else if (hasBizType) {
            page = fileRepository.findByBizTypeAndDeletedFalse(query.getBizType(), pageable);
        } else if (hasBizId) {
            page = fileRepository.findByBizIdAndDeletedFalse(query.getBizId(), pageable);
        } else {
            page = fileRepository.findByDeletedFalse(pageable);
        }
        return page.map(this::toFileResponse);
    }

    @Override
    @Transactional
    public SystemDto.FileResponse createFile(SystemDto.FileRequest request) {
        SysFile entity = new SysFile();
        applyFileRequest(entity, request);
        return toFileResponse(fileRepository.save(entity));
    }

    @Override
    @Transactional
    public SystemDto.FileResponse updateFile(Long id, SystemDto.FileRequest request) {
        SysFile entity = fileRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("文件记录不存在: " + id));
        applyFileRequest(entity, request);
        return toFileResponse(fileRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteFile(Long id) {
        SysFile entity = fileRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("文件记录不存在: " + id));
        entity.setDeleted(true);
        fileRepository.save(entity);
    }

    private void applyFileRequest(SysFile entity, SystemDto.FileRequest request) {
        entity.setFileName(request.getFileName());
        entity.setOriginalName(request.getOriginalName());
        entity.setFileType(request.getFileType());
        entity.setFileSize(request.getFileSize());
        entity.setFilePath(request.getFilePath());
        entity.setStorageType(request.getStorageType() != null ? request.getStorageType() : "LOCAL");
        entity.setUploader(request.getUploader());
        entity.setBizType(request.getBizType());
        entity.setBizId(request.getBizId());
    }

    private SystemDto.FileResponse toFileResponse(SysFile e) {
        return SystemDto.FileResponse.builder()
                .id(e.getId())
                .fileName(e.getFileName())
                .originalName(e.getOriginalName())
                .fileType(e.getFileType())
                .fileSize(e.getFileSize())
                .filePath(e.getFilePath())
                .storageType(e.getStorageType())
                .uploader(e.getUploader())
                .bizType(e.getBizType())
                .bizId(e.getBizId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    // ==================== Config ====================

    @Override
    public Page<SystemDto.ConfigResponse> pageConfigs(SystemDto.ConfigQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Direction.ASC, "configKey"));
        String keyword = (query.getKeyword() != null && !query.getKeyword().isBlank())
                ? query.getKeyword() : null;
        return configRepository.searchByKeyword(keyword, pageable).map(this::toConfigResponse);
    }

    @Override
    public SystemDto.ConfigResponse getConfigByKey(String configKey) {
        SysConfig entity = configRepository.findByConfigKeyAndDeletedFalse(configKey)
                .orElseThrow(() -> new RuntimeException("配置项不存在: " + configKey));
        return toConfigResponse(entity);
    }

    @Override
    @Transactional
    public SystemDto.ConfigResponse createConfig(SystemDto.ConfigRequest request) {
        SysConfig entity = new SysConfig();
        applyConfigRequest(entity, request);
        return toConfigResponse(configRepository.save(entity));
    }

    @Override
    @Transactional
    public SystemDto.ConfigResponse updateConfig(Long id, SystemDto.ConfigRequest request) {
        SysConfig entity = configRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("配置项不存在: " + id));
        applyConfigRequest(entity, request);
        return toConfigResponse(configRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteConfig(Long id) {
        SysConfig entity = configRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("配置项不存在: " + id));
        entity.setDeleted(true);
        configRepository.save(entity);
    }

    private void applyConfigRequest(SysConfig entity, SystemDto.ConfigRequest request) {
        entity.setConfigKey(request.getConfigKey());
        entity.setConfigValue(request.getConfigValue());
        entity.setConfigName(request.getConfigName());
        entity.setConfigType(request.getConfigType() != null ? request.getConfigType() : "STRING");
        entity.setNotes(request.getNotes());
    }

    private SystemDto.ConfigResponse toConfigResponse(SysConfig e) {
        return SystemDto.ConfigResponse.builder()
                .id(e.getId())
                .configKey(e.getConfigKey())
                .configValue(e.getConfigValue())
                .configName(e.getConfigName())
                .configType(e.getConfigType())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
