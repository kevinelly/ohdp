package com.dsf.ohdp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author kevin
 * @description 报告输出目录配置
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ohdp.report")
public class OhdpReportProperties {

    private String storageDir;

    /** Refresh TOC/page fields through the locally installed Word/WPS automation on Windows. */
    private boolean refreshFieldsOnWindows;
}
