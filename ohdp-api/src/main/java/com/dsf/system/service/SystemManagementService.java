package com.dsf.system.service;

import com.dsf.system.dto.SystemDto;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public interface SystemManagementService {

    // Department
    List<SystemDto.DepartmentResponse> listDepartments(Boolean enabled);

    SystemDto.DepartmentResponse createDepartment(SystemDto.DepartmentRequest request);

    SystemDto.DepartmentResponse updateDepartment(Long id, SystemDto.DepartmentRequest request);

    void deleteDepartment(Long id);

    // Post
    Page<SystemDto.PostResponse> pagePosts(SystemDto.PostQuery query);

    List<SystemDto.PostResponse> listAllPosts(Boolean enabled);

    SystemDto.PostResponse createPost(SystemDto.PostRequest request);

    SystemDto.PostResponse updatePost(Long id, SystemDto.PostRequest request);

    void deletePost(Long id);

    // Notice
    Page<SystemDto.NoticeResponse> pageNotices(SystemDto.NoticeQuery query);

    SystemDto.NoticeResponse createNotice(SystemDto.NoticeRequest request);

    SystemDto.NoticeResponse updateNotice(Long id, SystemDto.NoticeRequest request);

    void deleteNotice(Long id);

    // Log (read-only)
    Page<SystemDto.LogResponse> pageLogs(SystemDto.LogQuery query);

    // File
    Page<SystemDto.FileResponse> pageFiles(SystemDto.FileQuery query);

    SystemDto.FileResponse createFile(SystemDto.FileRequest request);

    SystemDto.FileResponse updateFile(Long id, SystemDto.FileRequest request);

    void deleteFile(Long id);

    // Config
    Page<SystemDto.ConfigResponse> pageConfigs(SystemDto.ConfigQuery query);

    SystemDto.ConfigResponse getConfigByKey(String configKey);

    SystemDto.ConfigResponse createConfig(SystemDto.ConfigRequest request);

    SystemDto.ConfigResponse updateConfig(Long id, SystemDto.ConfigRequest request);

    void deleteConfig(Long id);
}
