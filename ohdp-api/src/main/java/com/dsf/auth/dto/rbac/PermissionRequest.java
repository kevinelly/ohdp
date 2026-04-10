package com.dsf.auth.dto.rbac;

import com.dsf.auth.entity.PermissionType;
import com.dsf.common.model.RecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author kevin
 * @description 权限请求参数
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Data
public class PermissionRequest {

    @NotBlank
    @Size(max = 64)
    private String permCode;

    @NotBlank
    @Size(max = 128)
    private String permName;

    @NotNull
    private PermissionType permType;

    @Size(max = 10)
    private String apiMethod;

    @Size(max = 255)
    private String apiPath;

    private Long parentId;

    private Integer sortNo;

    @NotNull
    private RecordStatus status;
}

