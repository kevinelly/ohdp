package com.dsf.feedback.model;

public enum FeedbackUrgency {
    NORMAL("一般"),
    URGENT("紧急");

    private final String label;

    FeedbackUrgency(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
