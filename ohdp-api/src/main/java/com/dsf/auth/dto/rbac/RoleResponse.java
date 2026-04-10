package com.dsf.auth.dto.rbac;

import com.dsf.common.model.RecordStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * @author kevin
 * @description 角色响应
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Value
@Builder
public class RoleResponse {
    Long id;
    String roleCode;
    String roleName;
    String remark;
    RecordStatus status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}

