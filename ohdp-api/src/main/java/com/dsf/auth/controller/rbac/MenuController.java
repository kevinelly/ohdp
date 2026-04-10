package com.dsf.auth.controller.rbac;

import com.dsf.auth.dto.rbac.MenuRequest;
import com.dsf.auth.dto.rbac.MenuResponse;
import com.dsf.auth.service.rbac.MenuService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author kevin
 * @description 菜单管理接口
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @PostMapping
    public MenuResponse create(@Valid @RequestBody MenuRequest request) {
        return menuService.create(request);
    }

    @PutMapping("/{id}")
    public MenuResponse update(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        return menuService.update(id, request);
    }

    @GetMapping("/{id}")
    public MenuResponse findById(@PathVariable Long id) {
        return menuService.findById(id);
    }

    @GetMapping("/tree")
    public List<MenuResponse> tree() {
        return menuService.tree();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        menuService.delete(id);
    }
}

