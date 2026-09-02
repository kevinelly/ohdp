package com.dsf.feedback.model;

public enum FeedbackPriority {
    LOW("低"),
    NORMAL("普通"),
    HIGH("高"),
    URGENT("紧急");

    private final String label;

    FeedbackPriority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
