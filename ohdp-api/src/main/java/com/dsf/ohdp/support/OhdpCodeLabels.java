package com.dsf.ohdp.support;

import java.util.Map;

/**
 * 统一维护持久化编码与中文业务名称的对应关系。
 * 数据库和接口中的编码保持稳定，界面、导出和异常信息使用本类提供的中文名称。
 */
public final class OhdpCodeLabels {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("DRAFT", "草稿"),
            Map.entry("DATA_FILLED", "已录入"),
            Map.entry("JUDGED", "已判定"),
            Map.entry("REPORT_READY", "已生成报告"),
            Map.entry("PENDING", "待领取"),
            Map.entry("IN_PROGRESS", "检测中"),
            Map.entry("COMPLETED", "待复核"),
            Map.entry("REVIEWED", "已复核"),
            Map.entry("REJECTED", "已退回"),
            Map.entry("CHEMICAL", "化学因素"),
            Map.entry("DUST", "粉尘"),
            Map.entry("PHYSICAL", "物理因素"),
            Map.entry("BIOLOGICAL", "生物因素"),
            Map.entry("RADIATION", "放射因素"),
            Map.entry("GENERAL", "一般因素"),
            Map.entry("OTHER", "其他"),
            Map.entry("NORMAL", "正常"),
            Map.entry("MAINTENANCE", "维修中"),
            Map.entry("SCRAPPED", "已报废"),
            Map.entry("LAB_INSTRUMENT", "实验仪器"),
            Map.entry("SAMPLER", "采样仪器"),
            Map.entry("NATIONAL", "国家级"),
            Map.entry("DEPARTMENT", "部门规章"),
            Map.entry("INDUSTRY", "行业级"),
            Map.entry("PROVINCIAL", "省级"),
            Map.entry("LOCAL", "地方级"),
            Map.entry("CREATE", "生成报告"),
            Map.entry("OVERWRITE", "重新生成报告"),
            Map.entry("REGENERATE", "重新生成报告"),
            Map.entry("DOWNLOAD", "下载报告"),
            Map.entry("SUCCESS", "成功"),
            Map.entry("FAIL", "失败"),
            Map.entry("QUALIFIED", "合格"),
            Map.entry("UNQUALIFIED", "不合格"),
            Map.entry("NOT_APPLICABLE", "不适用"),
            Map.entry("NOT_JUDGED", "未判定"),
            Map.entry("ACTIVE", "启用"),
            Map.entry("INACTIVE", "禁用"),
            Map.entry("MENU", "菜单"),
            Map.entry("BUTTON", "按钮"),
            Map.entry("API", "接口"),
            Map.entry("TASK_ORIGIN", "任务由来"),
            Map.entry("EVALUATION_BASIS", "评价依据")
    );

    private OhdpCodeLabels() {
    }

    public static String label(String code) {
        if (code == null || code.isBlank()) {
            return "未设置";
        }
        String trimmed = code.trim();
        if (trimmed.matches(".*[\\u4e00-\\u9fff].*")) {
            return trimmed;
        }
        String normalized = trimmed.toUpperCase();
        return LABELS.getOrDefault(normalized, "未知（" + trimmed + "）");
    }

    public static String regionLevel(Integer level) {
        if (level == null) return "未设置";
        return switch (level) {
            case 1 -> "省级";
            case 2 -> "市级";
            case 3 -> "区县级";
            default -> "未知（" + level + "）";
        };
    }
}
