package com.dsf.feedback.model;

public enum FeedbackCategory {
    FEATURE("功能建议"),
    UI_UX("界面体验"),
    OPERATION("操作问题"),
    DATA("数据问题"),
    REPORT("报告问题"),
    PERFORMANCE("性能问题"),
    OTHER("其他");

    private final String label;

    FeedbackCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
