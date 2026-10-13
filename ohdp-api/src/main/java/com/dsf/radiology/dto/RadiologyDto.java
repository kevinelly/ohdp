package com.dsf.radiology.dto;

import com.dsf.radiology.model.RadiologyCaseStatus;
import com.dsf.radiology.model.RadiologyFieldRole;
import com.dsf.radiology.model.RadiologyJudgement;
import com.dsf.radiology.model.RadiologyModality;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

public final class RadiologyDto {
    private RadiologyDto() {
    }

    @Data
    public static class CaseRequest {
        @NotBlank
        private String caseCode;
        @NotNull
        private RadiologyModality modality;
        private String sampleAcceptanceNo;
        @NotBlank
        private String inspectedUnitName;
        private String inspectedUnitAddress;
        private String contactName;
        private String contactPhone;
        @NotBlank
        private String equipmentName;
        private String equipmentModel;
        private String equipmentNo;
        private String manufacturer;
        private String phantomName;
        private String phantomManufacturer;
        private String phantomModel;
        private String phantomSerialNo;
        private String phantomManualReference;
        private String detectionCategory;
        private String detectionLocation;
        private LocalDate inspectionDate;
        private LocalDate reportDate;
        private String standardBasis;
        private String instrumentDescription;
        private String writerName;
        private String reviewerName;
        private String approverName;
        private Boolean hasAec = true;
        private Boolean hasRawImages = true;
        private String featureReason;
        private String agencyName;
        private String region;
        private LocalDate inspectionEndDate;
        private String protectionMode = "UNSUBTRACTED";
        private String rpdUnit = "nSv/h";
        private Long expectedDataVersion;
        private String notes;
    }

    public record CaseSummary(
            Long id,
            String caseCode,
            RadiologyModality modality,
            Integer revisionNo,
            String inspectedUnitName,
            String equipmentName,
            String equipmentModel,
            LocalDate inspectionDate,
            RadiologyCaseStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record CaseDetail(
            Long id,
            String caseCode,
            RadiologyModality modality,
            Integer revisionNo,
            Long sourceCaseId,
            String sampleAcceptanceNo,
            String inspectedUnitName,
            String inspectedUnitAddress,
            String contactName,
            String contactPhone,
            String equipmentName,
            String equipmentModel,
            String equipmentNo,
            String manufacturer,
            String phantomName,
            String phantomManufacturer,
            String phantomModel,
            String phantomSerialNo,
            String phantomManualReference,
            String detectionCategory,
            String detectionLocation,
            LocalDate inspectionDate,
            LocalDate reportDate,
            String standardBasis,
            String instrumentDescription,
            String writerName,
            String reviewerName,
            String approverName,
            String reviewComment,
            String approvalComment,
            String notes,
            String calculationVersion,
            RadiologyCaseStatus status,
            List<MeasurementResponse> measurements,
            List<ProtectionPointResponse> protectionPoints,
            List<AttachmentResponse> attachments,
            List<ReportResponse> reports,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long dataVersion,
            Long calculatedDataVersion,
            boolean calculationFresh,
            Boolean hasAec,
            Boolean hasRawImages,
            String featureReason,
            String agencyName,
            String region,
            LocalDate inspectionEndDate,
            String protectionMode,
            String rpdUnit,
            List<ItemCalculationResponse> itemResults,
            List<WorkflowEvent> workflowHistory,
            List<String> submissionIssues, java.util.Map<String,String> itemConditions) {
    }
    public record WorkflowEvent(String action, String operator, String comment, Long dataVersion, LocalDateTime at) {}

    @Data
    public static class MeasurementRequest {
        @NotBlank
        private String itemCode;
        @NotBlank
        private String fieldCode;
        private BigDecimal valueNum;
        private String valueText;
    }

    @Data
    public static class MeasurementBatchRequest {
        private Long expectedDataVersion;
        @NotEmpty
        @Valid
        private List<MeasurementRequest> values;
        private java.util.Map<String,String> conditions;
    }

    public record MeasurementResponse(
            Long id,
            String itemCode,
            String fieldCode,
            String fieldLabel,
            RadiologyFieldRole fieldRole,
            BigDecimal valueNum,
            String valueText,
            String unit,
            Integer sortNo,
            String valueSource, String displayValue) {
    }

    public record ItemCalculationResponse(
            String itemCode,
            String itemName,
            boolean complete,
            String message,
            BigDecimal resultValue,
            String resultText,
            String resultUnit,
            RadiologyJudgement judgement,
            String ruleDescription) {
    }

    public record CalculationResponse(
            Long caseId,
            String calculationVersion,
            int completedItems,
            int incompleteItems,
            List<ItemCalculationResponse> items) {
    }

    @Data
    public static class ProtectionPointRequest {
        @NotBlank
        private String pointCode;
        @NotBlank
        private String pointName;
        private String locationDescription;
        @NotEmpty
        private List<@NotNull BigDecimal> readings;
        private BigDecimal annualWorkloadMas;
        private BigDecimal occupancyFactor;
        private BigDecimal useFactor;
        private Integer sortNo;
    }

    @Data
    public static class ProtectionPointBatchRequest {
        private Long expectedDataVersion;
        @NotEmpty
        @Valid
        private List<ProtectionPointRequest> points;
    }

    public record ProtectionPointResponse(
            Long id,
            String pointCode,
            String pointName,
            String locationDescription,
            List<BigDecimal> readings,
            BigDecimal averageValue,
            BigDecimal resultValue,
            BigDecimal controlLimit,
            BigDecimal annualWorkloadMas,
            BigDecimal occupancyFactor,
            BigDecimal useFactor,
            BigDecimal annualEffectiveDose,
            BigDecimal annualDoseLimit,
            RadiologyJudgement judgement,
            Integer sortNo) {
    }

    @Data
    public static class RuleRequest {
        @NotNull
        private RadiologyModality modality;
        @NotBlank
        private String itemCode;
        @NotBlank
        private String ruleVersion;
        private BigDecimal lowerLimit;
        private BigDecimal upperLimit;
        private String expectedText;
        private String unit;
        @NotBlank
        private String standardCode;
        @NotBlank
        private String standardName;
        private String detectionCategory;
        private Boolean lowerInclusive = true;
        private Boolean upperInclusive = true;
        private Boolean enabled = true;
    }

    public record RuleResponse(
            Long id,
            RadiologyModality modality,
            String itemCode,
            String itemName,
            String ruleVersion,
            BigDecimal lowerLimit,
            BigDecimal upperLimit,
            String expectedText,
            String unit,
            String standardCode,
            String standardName,
            Boolean enabled, String detectionCategory, Boolean lowerInclusive, Boolean upperInclusive) {
    }

    @Data
    public static class WorkflowRequest {
        private Long expectedDataVersion;
        private String operatorName;
        private String comment;
    }

    public record ImportCellError(int column, String header, String value, String message) {
    }

    public record ImportPreview(
            String fileName,
            String sheetName,
            boolean dryRun,
            int recognizedFields,
            int appliedValues,
            List<String> ignoredHeaders,
            List<ImportCellError> errors) {
    }

    public record AttachmentResponse(
            Long id,
            Long caseId,
            String category,
            String originalName,
            String mediaType,
            Long fileSize,
            String sha256,
            String uploadedBy,
            LocalDateTime uploadedAt) {
    }

    public record ReportResponse(
            Long id,
            Long caseId,
            String reportNo,
            String fileName,
            String sha256,
            String generatedBy,
            LocalDateTime generatedAt) {
    }
}
