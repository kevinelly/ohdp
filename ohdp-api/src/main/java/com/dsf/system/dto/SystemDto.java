package com.dsf.system.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class SystemDto {

    // ==================== Department ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentRequest {
        private Long parentId;
        private String deptName;
        private String deptCode;
        private Integer sortNo;
        private String leaderName;
        private String contactPhone;
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentResponse {
        private Long id;
        private Long parentId;
        private String deptName;
        private String deptCode;
        private Integer sortNo;
        private String leaderName;
        private String contactPhone;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ==================== Post ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostRequest {
        private String postName;
        private String postCode;
        private Integer sortNo;
        private Boolean enabled;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostResponse {
        private Long id;
        private String postName;
        private String postCode;
        private Integer sortNo;
        private Boolean enabled;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostQuery {
        private String keyword;
        private Boolean enabled;
        private int page;
        private int size;
    }

    // ==================== Notice ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoticeRequest {
        private String title;
        private String content;
        private String noticeType;
        private String publisher;
        private LocalDateTime publishTime;
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoticeResponse {
        private Long id;
        private String title;
        private String content;
        private String noticeType;
        private String publisher;
        private LocalDateTime publishTime;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoticeQuery {
        private String keyword;
        private String noticeType;
        private int page;
        private int size;
    }

    // ==================== Log ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogResponse {
        private Long id;
        private String userName;
        private String operation;
        private String method;
        private String params;
        private String resultType;
        private String ip;
        private Long costTime;
        private String module;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogQuery {
        private String userName;
        private String module;
        private String resultType;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int page;
        private int size;
    }

    // ==================== File ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileRequest {
        private String fileName;
        private String originalName;
        private String fileType;
        private Long fileSize;
        private String filePath;
        private String storageType;
        private String uploader;
        private String bizType;
        private Long bizId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileResponse {
        private Long id;
        private String fileName;
        private String originalName;
        private String fileType;
        private Long fileSize;
        private String filePath;
        private String storageType;
        private String uploader;
        private String bizType;
        private Long bizId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileQuery {
        private String bizType;
        private Long bizId;
        private int page;
        private int size;
    }

    // ==================== Config ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigRequest {
        private String configKey;
        private String configValue;
        private String configName;
        private String configType;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigResponse {
        private Long id;
        private String configKey;
        private String configValue;
        private String configName;
        private String configType;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigQuery {
        private String keyword;
        private int page;
        private int size;
    }
}
