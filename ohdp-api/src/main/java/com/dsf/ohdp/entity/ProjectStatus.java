package com.dsf.ohdp.entity;

/**
 * @author kevin
 * @description 职业危害项目状态
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
public enum ProjectStatus {
    DRAFT,
    DATA_FILLED,
    JUDGED,
    REPORT_READY
}
