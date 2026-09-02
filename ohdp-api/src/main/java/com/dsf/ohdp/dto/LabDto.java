package com.dsf.ohdp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author kevin
 * @description 实验室任务单模块 DTO 定义
 */
public final class LabDto {

    private LabDto() {
    }

    // -------------------------------------------------------------------------
    // Task DTOs
    // -------------------------------------------------------------------------

    @Data
    public static class TaskRequest {

        @NotNull
        private Long projectId;

        @Size(max = 64)
        private String issuerName;

        private LocalDate issuedDate;

        @Size(max = 1000)
        private String notes;

        @Valid
        private List<SampleRequest> samples;
    }

    @Data
    public static class TaskUpdateRequest {

        @Size(max = 64)
        private String issuerName;

        private LocalDate issuedDate;

        @Size(max = 1000)
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskResponse {
        private Long id;
        private String taskCode;
        private Long projectId;
        private String taskStatus;
        private String taskStatusLabel;
        private String issuerName;
        private LocalDate issuedDate;
        private String receiverName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String reviewerName;
        private String reviewNote;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDetailResponse {
        private Long id;
        private String taskCode;
        private Long projectId;
        private String taskStatus;
        private String taskStatusLabel;
        private String issuerName;
        private LocalDate issuedDate;
        private String receiverName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String reviewerName;
        private String reviewNote;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<SampleResponse> samples;
    }

    @Data
    public static class TaskQuery {

        private Long projectId;
        private String taskStatus;
        private String keyword;

        @NotNull
        private Integer page;

        @NotNull
        private Integer size;
    }

    @Data
    public static class ReceiveRequest {

        @NotBlank
        @Size(max = 64)
        private String receiverName;
    }

    @Data
    public static class ReviewRequest {

        @NotNull
        private Boolean approved;

        @Size(max = 500)
        private String reviewNote;
    }

    // -------------------------------------------------------------------------
    // Sample DTOs
    // -------------------------------------------------------------------------

    @Data
    public static class SampleRequest {

        @NotBlank
        @Size(max = 64)
        private String sampleCode;

        @NotBlank
        @Size(max = 200)
        private String factorName;

        private Long factorId;

        private Integer sampleCount;

        private LocalDate sampleDate;

        @Size(max = 32)
        private String sampleType;

        @Size(max = 500)
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SampleResponse {
        private Long id;
        private Long taskId;
        private String sampleCode;
        private String factorName;
        private Long factorId;
        private Integer sampleCount;
        private LocalDate sampleDate;
        private String sampleType;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
