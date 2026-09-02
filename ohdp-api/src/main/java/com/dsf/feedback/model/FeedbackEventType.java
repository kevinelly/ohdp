package com.dsf.feedback.model;

public enum FeedbackEventType {
    SUBMITTED("提交意见"),
    AUTO_ACCEPTED("系统自动采纳"),
    HANDLING_UPDATED("更新处理安排"),
    STATUS_CHANGED("更新处理状态"),
    COMMENTED("补充说明"),
    INTERNAL_NOTE("内部处理记录"),
    ATTACHMENT_ADDED("上传附件"),
    ATTACHMENT_REMOVED("删除附件");

    private final String label;

    FeedbackEventType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
