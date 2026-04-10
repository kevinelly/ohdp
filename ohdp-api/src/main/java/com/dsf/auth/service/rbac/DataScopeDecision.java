package com.dsf.auth.service.rbac;

import java.util.Collections;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/**
 * @author kevin
 * @description 数据权限判定结果
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Value
@Builder
public class DataScopeDecision {
    boolean allAccess;
    Set<Long> storeIds;

    public static DataScopeDecision all() {
        return DataScopeDecision.builder().allAccess(true).storeIds(Collections.emptySet()).build();
    }

    public static DataScopeDecision stores(Set<Long> storeIds) {
        return DataScopeDecision.builder().allAccess(false).storeIds(storeIds).build();
    }
}

