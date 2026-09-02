package com.dsf.feedback.model;

public enum FeedbackStatus {
    ACCEPTED("已采纳"),
    IN_PROGRESS("处理中"),
    NEED_INFO("待补充"),
    PENDING_CONFIRMATION("待用户确认"),
    CLOSED("已关闭"),
    REJECTED("不予采纳"),
    WITHDRAWN("已撤回");

    private final String label;

    FeedbackStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return this == CLOSED || this == REJECTED || this == WITHDRAWN;
    }
}
