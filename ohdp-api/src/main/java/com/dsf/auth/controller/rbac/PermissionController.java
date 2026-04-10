package com.dsf.auth.controller.rbac;

import com.dsf.auth.dto.rbac.PermissionRequest;
import com.dsf.auth.dto.rbac.PermissionResponse;
import com.dsf.auth.service.rbac.PermissionService;
import jakarta.validation.Valid;
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

/**
 * @author kevin
 * @description 权限管理接口
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    public PermissionResponse create(@Valid @RequestBody PermissionRequest request) {
        return permissionService.create(request);
    }

    @PutMapping("/{id}")
    public PermissionResponse update(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        return permissionService.update(id, request);
    }

    @GetMapping("/{id}")
    public PermissionResponse findById(@PathVariable Long id) {
        return permissionService.findById(id);
    }

    @GetMapping
    public Page<PermissionResponse> findPage(
            @RequestParam(required = false) String permCode,
            @RequestParam(required = false) String permName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return permissionService.findPage(permCode, permName, page, size);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        permissionService.delete(id);
    }
}
