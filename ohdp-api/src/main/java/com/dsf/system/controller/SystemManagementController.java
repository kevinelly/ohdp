package com.dsf.system.controller;

import com.dsf.system.dto.SystemDto;
import com.dsf.system.service.SystemManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
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

@Tag(name = "System-Management")
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemManagementController {

    private final SystemManagementService systemManagementService;

    // ==================== Department ====================

    @GetMapping("/departments")
    public List<SystemDto.DepartmentResponse> listDepartments(
            @RequestParam(required = false) Boolean enabled) {
        return systemManagementService.listDepartments(enabled);
    }

    @PostMapping("/departments")
    public SystemDto.DepartmentResponse createDepartment(
            @RequestBody SystemDto.DepartmentRequest request) {
        return systemManagementService.createDepartment(request);
    }

    @PutMapping("/departments/{id}")
    public SystemDto.DepartmentResponse updateDepartment(
            @PathVariable Long id,
            @RequestBody SystemDto.DepartmentRequest request) {
        return systemManagementService.updateDepartment(id, request);
    }

    @DeleteMapping("/departments/{id}")
    public void deleteDepartment(@PathVariable Long id) {
        systemManagementService.deleteDepartment(id);
    }

    // ==================== Post ====================

    @GetMapping("/posts")
    public Page<SystemDto.PostResponse> pagePosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        SystemDto.PostQuery query = SystemDto.PostQuery.builder()
                .keyword(keyword)
                .enabled(enabled)
                .page(page)
                .size(size)
                .build();
        return systemManagementService.pagePosts(query);
    }

    @GetMapping("/posts/all")
    public List<SystemDto.PostResponse> listAllPosts(
            @RequestParam(required = false) Boolean enabled) {
        return systemManagementService.listAllPosts(enabled);
    }

    @PostMapping("/posts")
    public SystemDto.PostResponse createPost(@RequestBody SystemDto.PostRequest request) {
        return systemManagementService.createPost(request);
    }

    @PutMapping("/posts/{id}")
    public SystemDto.PostResponse updatePost(
            @PathVariable Long id,
            @RequestBody SystemDto.PostRequest request) {
        return systemManagementService.updatePost(id, request);
    }

    @DeleteMapping("/posts/{id}")
    public void deletePost(@PathVariable Long id) {
        systemManagementService.deletePost(id);
    }

    // ==================== Notice ====================

    @GetMapping("/notices")
    public Page<SystemDto.NoticeResponse> pageNotices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String noticeType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        SystemDto.NoticeQuery query = SystemDto.NoticeQuery.builder()
                .keyword(keyword)
                .noticeType(noticeType)
                .page(page)
                .size(size)
                .build();
        return systemManagementService.pageNotices(query);
    }

    @PostMapping("/notices")
    public SystemDto.NoticeResponse createNotice(@RequestBody SystemDto.NoticeRequest request) {
        return systemManagementService.createNotice(request);
    }

    @PutMapping("/notices/{id}")
    public SystemDto.NoticeResponse updateNotice(
            @PathVariable Long id,
            @RequestBody SystemDto.NoticeRequest request) {
        return systemManagementService.updateNotice(id, request);
    }

    @DeleteMapping("/notices/{id}")
    public void deleteNotice(@PathVariable Long id) {
        systemManagementService.deleteNotice(id);
    }

    // ==================== Log (read-only) ====================

    @GetMapping("/logs")
    public Page<SystemDto.LogResponse> pageLogs(
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String resultType,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        SystemDto.LogQuery query = SystemDto.LogQuery.builder()
                .userName(userName)
                .module(module)
                .resultType(resultType)
                .startTime(startTime)
                .endTime(endTime)
                .page(page)
                .size(size)
                .build();
        return systemManagementService.pageLogs(query);
    }

    // ==================== File ====================

    @GetMapping("/files")
    public Page<SystemDto.FileResponse> pageFiles(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long bizId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        SystemDto.FileQuery query = SystemDto.FileQuery.builder()
                .bizType(bizType)
                .bizId(bizId)
                .page(page)
                .size(size)
                .build();
        return systemManagementService.pageFiles(query);
    }

    @PostMapping("/files")
    public SystemDto.FileResponse createFile(@RequestBody SystemDto.FileRequest request) {
        return systemManagementService.createFile(request);
    }

    @PutMapping("/files/{id}")
    public SystemDto.FileResponse updateFile(
            @PathVariable Long id,
            @RequestBody SystemDto.FileRequest request) {
        return systemManagementService.updateFile(id, request);
    }

    @DeleteMapping("/files/{id}")
    public void deleteFile(@PathVariable Long id) {
        systemManagementService.deleteFile(id);
    }

    // ==================== Config ====================

    @GetMapping("/configs")
    public Page<SystemDto.ConfigResponse> pageConfigs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        SystemDto.ConfigQuery query = SystemDto.ConfigQuery.builder()
                .keyword(keyword)
                .page(page)
                .size(size)
                .build();
        return systemManagementService.pageConfigs(query);
    }

    @GetMapping("/configs/by-key")
    public SystemDto.ConfigResponse getConfigByKey(@RequestParam String configKey) {
        return systemManagementService.getConfigByKey(configKey);
    }

    @PostMapping("/configs")
    public SystemDto.ConfigResponse createConfig(@RequestBody SystemDto.ConfigRequest request) {
        return systemManagementService.createConfig(request);
    }

    @PutMapping("/configs/{id}")
    public SystemDto.ConfigResponse updateConfig(
            @PathVariable Long id,
            @RequestBody SystemDto.ConfigRequest request) {
        return systemManagementService.updateConfig(id, request);
    }

    @DeleteMapping("/configs/{id}")
    public void deleteConfig(@PathVariable Long id) {
        systemManagementService.deleteConfig(id);
    }
}
