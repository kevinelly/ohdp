package com.dsf.ohdp.dto;

import com.dsf.ohdp.entity.ProjectStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author kevin
 * @description 职业危害模块 DTO 定义
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
public final class OhdpDto {

    private OhdpDto() {
    }

    @Data
    public static class ProjectRequest {
        private Long companyProfileId;
        private Long detectTypeDictItemId;
        private Long taskOriginTemplateId;
        private Long evaluationBasisTemplateId;
        private Long writerStaffId;
        private Long reviewerStaffId;
        private Long approverStaffId;

        @Size(max = 200)
        private String companyName;

        @Size(max = 255)
        private String companyAddress;

        @Size(max = 64)
        private String detectType;

        private LocalDate samplingDate;
        private LocalDate reportDate;

        @Size(max = 200)
        private String sampleName;

        @Size(max = 200)
        private String submitUnit;

        private Long submitUnitCompanyId;

        @Size(max = 200)
        private String entrustUnit;

        private Long entrustUnitCompanyId;

        @Size(max = 3000)
        private String taskOrigin;

        @Size(max = 3000)
        private String evaluationBasis;

        @Size(max = 64)
        private String writerName;

        @Size(max = 64)
        private String reviewerName;

        @Size(max = 64)
        private String approverName;

        @Size(max = 128)
        private String writerCertNo;

        @Size(max = 128)
        private String reviewerCertNo;

        @Size(max = 128)
        private String approverCertNo;

        private LocalDate statementDate;

        @Size(max = 1000)
        private String notes;

        @Valid
        private ReportSupplement reportSupplement;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectResponse {
        private Long id;
        private String projectCode;
        private Long companyProfileId;
        private String companyName;
        private String companyAddress;
        private Long detectTypeDictItemId;
        private String detectType;
        private LocalDate samplingDate;
        private LocalDate reportDate;
        private String sampleName;
        private String submitUnit;
        private Long submitUnitCompanyId;
        private String entrustUnit;
        private Long entrustUnitCompanyId;
        private Long taskOriginTemplateId;
        private String taskOrigin;
        private Long evaluationBasisTemplateId;
        private String evaluationBasis;
        private Long writerStaffId;
        private String writerName;
        private Long reviewerStaffId;
        private String reviewerName;
        private Long approverStaffId;
        private String approverName;
        private String writerCertNo;
        private String reviewerCertNo;
        private String approverCertNo;
        private LocalDate statementDate;
        private String notes;
        private ReportSupplement reportSupplement;
        private ProjectStatus status;
        private String statusLabel;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /**
     * 《（2026）定期检测报告模板-北海》专用报告补充数据。
     * 所有字段均为项目快照；未开展或不适用的项目允许留空，但报告预检会
     * 对正式报告必须内容给出明确提示。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportSupplement {
        @Size(max = 80)
        private String reportHeaderName;
        @Size(max = 64)
        private String socialCreditCode;
        @Size(max = 100)
        private String legalRepresentative;
        @Size(max = 255)
        private String registeredAddress;
        @Size(max = 255)
        private String technicalServiceAddress;
        @Size(max = 100)
        private String contactName;
        @Size(max = 50)
        private String contactPhone;
        @Size(max = 200)
        private String industryCategory;
        @Size(max = 50)
        private String commissioningDate;
        @Size(max = 100)
        private String economicType;
        @Size(max = 100)
        private String companyScale;
        private Integer employeeCount;
        private Integer outsourcedWorkerCount;
        private Integer dispatchedWorkerCount;

        @Size(max = 6000)
        private String detectionScope;
        @Size(max = 6000)
        private String lawsRegulations;
        @Size(max = 6000)
        private String basicMaterials;
        @Size(max = 6000)
        private String productionProcess;
        @Size(max = 3000)
        private String radiationSourceSummary;
        @Size(max = 40)
        private String radiationSourceMode;
        @Size(max = 100)
        private String radiationSeparateReportNo;
        @Size(max = 2000)
        private String vocAnalysisConclusion;
        @Size(max = 2000)
        private String exclusionScreeningConclusion;
        @Size(max = 2000)
        private String rawRecordCompletenessNote;
        @Size(max = 2000)
        private String qualificationCertificatePlan;
        private String qualificationCertificateFileName;
        private String qualificationCertificateImageDataUrl;
        @Size(max = 2000)
        private String attachmentDeliveryPlan;
        @Size(max = 2000)
        private String photoEmbeddingPlan;
        @Valid
        private List<ReportPhoto> reportPhotos;
        @Size(max = 1000)
        private String multiDayDisplayRule;
        @Size(max = 2000)
        private String overtimeReductionRule;
        @Size(max = 2000)
        private String physicalFactorScope;
        @Size(max = 2000)
        private String specialMeasurementRule;
        @Size(max = 1000)
        private String signatureStampMode;
        @Size(max = 3000)
        private String productionCondition;
        @Size(max = 3000)
        private String samplingLayoutDescription;
        @Size(max = 3000)
        private String detectionConditions;
        @Size(max = 1000)
        private String environmentConditions;
        @Size(max = 1000)
        private String onsiteInspectors;
        @Size(max = 2000)
        private String planDeviation;
        @Size(max = 100)
        private String riskCategory;
        @Size(max = 2000)
        private String majorHazards;
        @Size(max = 3000)
        private String overLimitSummary;
        @Size(max = 4000)
        private String majorProblems;
        @Size(max = 6000)
        private String recommendations;

        @Valid
        private List<MaterialItem> materials;
        private Boolean vocAnalysisPerformed;
        @Size(max = 50) private String vocSamplingDate;
        @Size(max = 50) private String vocDetectionDate;
        @Size(max = 500) private String vocDetectionBasis;
        @Size(max = 300) private String vocDetectionMethod;
        @Size(max = 300) private String vocInstrument;
        private String vocTestingOrganization;
        private String vocSourceReportNo;
        private String mixedExposureAssessment;
        private String mixedExposureAssessmentBasis;
        @Size(max = 50) private String vocTotalPeakAreaPercent;
        @Size(max = 50) private String vocInorganicPeakAreaPercent;
        @Valid
        private List<VocAnalysisItem> vocAnalysisItems;
        @Valid
        private List<XrayDeviceItem> xrayDevices;
        @Valid
        private List<SealedSourceItem> sealedSources;
        @Valid
        private List<UnsealedSourceItem> unsealedSources;
        private Boolean exclusionScreeningPerformed;
        @Valid
        private List<ExclusionScreeningItem> exclusionScreeningItems;
        @Valid
        private List<EquipmentItem> equipment;
        @Valid
        private List<ProtectionFacilityItem> protectionFacilities;
        @Valid
        private List<PpeItem> personalProtectiveEquipment;
        @Valid
        private List<SamplingPlanItem> samplingPlans;
        private Boolean materialsConfirmedNotApplicable;
        private Boolean equipmentConfirmedNotApplicable;
        private Boolean protectionFacilitiesConfirmedNotApplicable;
        private Boolean ppeConfirmedNotApplicable;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportPhoto {
        @Size(max = 255)
        private String fileName;
        @Size(max = 255)
        private String caption;
        @Size(max = 100)
        private String takenDate;
        private String imageDataUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaterialItem {
        @Size(max = 150) private String workplace;
        @Size(max = 150) private String materialName;
        @Size(max = 150) private String processStage;
        @Size(max = 500) private String mainComponents;
        @Size(max = 100) private String annualAmount;
        @Size(max = 50) private String physicalState;
        @Size(max = 300) private String positions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VocAnalysisItem {
        @Size(max = 150) private String chemicalName;
        @Size(max = 200) private String manufacturerBatchNo;
        @Size(max = 1000) private String detectedComponents;
        @Size(max = 100) private String casNo;
        @Size(max = 50) private String peakAreaPercent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class XrayDeviceItem {
        @Size(max = 30) private String serialNo;
        @Size(max = 150) private String workplace;
        @Size(max = 150) private String deviceName;
        @Size(max = 100) private String category;
        @Size(max = 100) private String model;
        @Size(max = 150) private String manufacturer;
        @Size(max = 300) private String mainParameters;
        @Size(max = 100) private String radiationType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SealedSourceItem {
        @Size(max = 30) private String serialNo;
        @Size(max = 150) private String workplace;
        @Size(max = 100) private String nuclideName;
        @Size(max = 100) private String code;
        @Size(max = 100) private String factoryActivity;
        @Size(max = 100) private String category;
        @Size(max = 150) private String installationLocation;
        @Size(max = 200) private String purpose;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnsealedSourceItem {
        @Size(max = 150) private String workplace;
        @Size(max = 100) private String nuclideName;
        @Size(max = 100) private String dailyMaxActivity;
        @Size(max = 100) private String dailyEquivalentMaxActivity;
        @Size(max = 100) private String annualMaxActivity;
        @Size(max = 100) private String physicochemicalProperty;
        @Size(max = 100) private String operationMode;
        @Size(max = 200) private String storageMethodLocation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExclusionScreeningItem {
        @Size(max = 150) private String workplace;
        @Size(max = 100) private String jobName;
        @Size(max = 150) private String factorName;
        @Size(max = 100) private String ctwa;
        @Size(max = 100) private String pcTwa;
        @Size(max = 100) private String contactJudgement;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquipmentItem {
        @Size(max = 150) private String workplace;
        @Size(max = 150) private String location;
        @Size(max = 150) private String equipmentName;
        private Integer totalCount;
        private Integer runningCount;
        @Size(max = 300) private String operatingPositions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProtectionFacilityItem {
        @Size(max = 150) private String workplace;
        @Size(max = 150) private String locationOrPosition;
        @Size(max = 150) private String facilityName;
        @Size(max = 100) private String facilityType;
        private Integer totalCount;
        private Integer runningCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PpeItem {
        @Size(max = 150) private String ppeName;
        @Size(max = 150) private String manufacturer;
        @Size(max = 100) private String model;
        @Size(max = 300) private String protectionParameters;
        @Size(max = 300) private String positions;
        @Size(max = 100) private String usageStatus;
        @Size(max = 100) private String category;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SamplingPlanItem {
        @Size(max = 150) private String workplace;
        @Size(max = 100) private String jobName;
        @Size(max = 100) private String operationType;
        private Integer workersPerShift;
        @Size(max = 300) private String targetLocationTiming;
        @Size(max = 150) private String factorName;
        @Size(max = 100) private String limitType;
        @Size(max = 100) private String contactType;
        @Size(max = 100) private String detectionMode;
        @Size(max = 100) private String detectionTimeType;
        private Integer pointsPerDay;
        private Integer timesPerDay;
        private Integer days;
    }

    @Data
    public static class PositionRequest {
        private Long positionTemplateId;

        @Size(max = 100)
        private String jobName;

        @Size(max = 50)
        private String shiftSystem;

        @Size(max = 50)
        private String workingHours;

        private Integer workerCount;

        @Size(max = 150)
        private String workLocation;

        @Size(max = 100)
        private String operationType;

        @Size(max = 255)
        private String operationContent;

        @Size(max = 50)
        private String operationDuration;

        @Size(max = 500)
        private String hazards;

        @Size(max = 500)
        private String ppe;

        @Size(max = 500)
        private String protectionFacilities;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionResponse {
        private Long id;
        private Long projectId;
        private String jobName;
        private String shiftSystem;
        private String workingHours;
        private Integer workerCount;
        private String workLocation;
        private String operationType;
        private String operationContent;
        private String operationDuration;
        private String hazards;
        private String ppe;
        private String protectionFacilities;
    }

    @Data
    public static class ResultItemRequest {
        @Size(max = 1000)
        private String factorCategory;
        @Size(max = 1000)
        private String pointCode;
        @Size(max = 1000)
        private String sampleCode;
        @Size(max = 1000)
        private String detectionMode;
        @Size(max = 1000)
        private String noiseType;
        private LocalDate laboratoryDate;
        @Size(max = 1000)
        private String detectionBasis;
        @Size(max = 1000)
        private String detectionMethod;
        @Size(max = 1000)
        private String instrument;
        @Size(max = 1000)
        private String testingOrganization;
        @Size(max = 1000)
        private String sourceRecordNo;
        @Size(max = 1000)
        private String detectionLimit;
        @Size(max = 1000)
        private String quantificationLimit;
        @Size(max = 1000)
        private String evaluationBasis;
        @Size(max = 1000)
        private String overLimitReason;
        private Integer weeklyWorkDays;
        private Boolean reductionApplicable;
        private BigDecimal peakLimitMultiplier;
        private BigDecimal peakExposureMinutes;
        private Integer peakTimesPerShift;
        private BigDecimal peakIntervalMinutes;
        @Size(max = 1000)
        private String mixedExposureGroup;
        @Size(max = 1000)
        private String mixedExposureBasis;
        @Size(max = 1000)
        private String measurementConditions;

        private Long factorTemplateId;
        private Long positionId;
        private Long factorId;

        @Size(max = 100)
        private String jobName;

        @Size(max = 120)
        private String factorName;

        @Size(max = 40)
        private String metricType;

        @Size(max = 80)
        private String valueRaw;

        private BigDecimal valueNum;

        @Size(max = 32)
        private String unit;

        private BigDecimal limitValue;

        @Size(max = 32)
        private String limitUnit;

        private LocalDate measurementDate;

        @Size(max = 80)
        private String measurementBodyPart;

        @Size(max = 120)
        private String measurementBand;

        private BigDecimal dailyExposureHours;
        private BigDecimal weeklyExposureHours;
    }

    @Data
    public static class ResultBatchRequest {
        @Valid
        @NotNull
        private List<ResultItemRequest> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultBatchResponse {
        private Long projectId;
        private Integer addedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultItemResponse {
        @Size(max = 1000)
        private String factorCategory;
        @Size(max = 1000)
        private String pointCode;
        @Size(max = 1000)
        private String sampleCode;
        @Size(max = 1000)
        private String detectionMode;
        @Size(max = 1000)
        private String noiseType;
        private LocalDate laboratoryDate;
        @Size(max = 1000)
        private String detectionBasis;
        @Size(max = 1000)
        private String detectionMethod;
        @Size(max = 1000)
        private String instrument;
        @Size(max = 1000)
        private String testingOrganization;
        @Size(max = 1000)
        private String sourceRecordNo;
        @Size(max = 1000)
        private String detectionLimit;
        @Size(max = 1000)
        private String quantificationLimit;
        @Size(max = 1000)
        private String evaluationBasis;
        @Size(max = 1000)
        private String overLimitReason;
        private Integer weeklyWorkDays;
        private Boolean reductionApplicable;
        private BigDecimal peakLimitMultiplier;
        private BigDecimal peakExposureMinutes;
        private Integer peakTimesPerShift;
        private BigDecimal peakIntervalMinutes;
        @Size(max = 1000)
        private String mixedExposureGroup;
        @Size(max = 1000)
        private String mixedExposureBasis;
        @Size(max = 1000)
        private String measurementConditions;

        private Long id;
        private Long projectId;
        private Long positionId;
        private Long factorId;
        private String jobName;
        private String factorName;
        private String metricType;
        private String valueRaw;
        private BigDecimal valueNum;
        private String unit;
        private BigDecimal limitValue;
        private String limitUnit;
        private LocalDate measurementDate;
        private String measurementBodyPart;
        private String measurementBand;
        private BigDecimal dailyExposureHours;
        private BigDecimal weeklyExposureHours;
        private BigDecimal reductionFactor;
        private BigDecimal adjustedLimitValue;
        private String judgement;
        private String judgementLabel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JudgeResponse {
        private Long projectId;
        private int totalCount;
        private int judgedCount;
        private int qualifiedCount;
        private int unqualifiedCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportFileResponse {
        private Long id;
        private Long projectId;
        private String fileName;
        private String filePath;
        private String templateVersion;
        private String generatedBy;
        private LocalDateTime generatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportOperationLogResponse {
        private Long id;
        private Long projectId;
        private String operationType;
        private String operationTypeLabel;
        private String operatorName;
        private String operationMessage;
        private LocalDateTime operatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportPrecheckResponse {
        private Long projectId;
        private Boolean passed;
        private List<String> missingItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportPreviewResponse {
        private ProjectResponse project;
        private List<PositionResponse> positions;
        private List<ResultItemResponse> results;
        private ReportPrecheckResponse precheck;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportCenterResponse {
        private Long projectId;
        private String projectCode;
        private String companyName;
        private ProjectStatus projectStatus;
        private String projectStatusLabel;
        private String reportStatus;
        private String reportStatusLabel;
        private Long reportId;
        private String fileName;
        private String generatedBy;
        private LocalDateTime generatedAt;
        private LocalDateTime projectCreatedAt;
    }

    @Data
    public static class ProjectQuery {
        private String projectCode;
        private String companyName;
        private ProjectStatus status;

        @NotNull
        private Integer page;

        @NotNull
        private Integer size;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyProfileRequest {
        @NotBlank
        @Size(max = 200)
        private String companyName;

        @NotBlank
        @Size(max = 255)
        private String companyAddress;

        @Size(max = 100)
        private String contactName;

        @Size(max = 50)
        private String contactPhone;

        @Size(max = 100)
        private String industryType;

        @Size(max = 1000)
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyProfileResponse {
        private Long id;
        private String companyName;
        private String companyAddress;
        private String contactName;
        private String contactPhone;
        private String industryType;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionTemplateRequest {
        @NotBlank
        @Size(max = 100)
        private String jobName;

        @Size(max = 50)
        private String shiftSystem;

        @Size(max = 50)
        private String workingHours;

        @Size(max = 100)
        private String operationType;

        @Size(max = 50)
        private String operationDuration;

        @Size(max = 500)
        private String hazards;

        @Size(max = 500)
        private String ppe;

        @Size(max = 500)
        private String protectionFacilities;

        @Size(max = 1000)
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionTemplateResponse {
        private Long id;
        private String jobName;
        private String shiftSystem;
        private String workingHours;
        private String operationType;
        private String operationDuration;
        private String hazards;
        private String ppe;
        private String protectionFacilities;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FactorTemplateRequest {
        @NotBlank
        @Size(max = 120)
        private String factorName;

        @NotBlank
        @Size(max = 40)
        private String metricType;

        @Size(max = 32)
        private String defaultUnit;

        @Size(max = 32)
        private String defaultLimitValue;

        @Size(max = 32)
        private String defaultLimitUnit;

        @Size(max = 1000)
        private String detectionBasis;

        @Size(max = 500)
        private String detectionMethod;

        @Size(max = 1000)
        private String detectionInstruments;

        @Size(max = 1000)
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FactorTemplateResponse {
        private Long id;
        private String factorName;
        private String metricType;
        private String defaultUnit;
        private String defaultLimitValue;
        private String defaultLimitUnit;
        private String detectionBasis;
        private String detectionMethod;
        private String detectionInstruments;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DictionaryItemRequest {
        @NotBlank
        @Size(max = 64)
        private String dictType;

        @NotBlank
        @Size(max = 64)
        private String itemCode;

        @NotBlank
        @Size(max = 128)
        private String itemName;

        private Integer sortNo;
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DictionaryItemResponse {
        private Long id;
        private String dictType;
        private String itemCode;
        private String itemName;
        private Integer sortNo;
        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffProfileRequest {
        @Size(max = 100)
        private String profession;
        @NotBlank
        @Size(max = 64)
        private String staffName;

        @Size(max = 64)
        private String staffTitle;

        @NotBlank
        @Size(max = 128)
        private String certNo;

        @Size(max = 50)
        private String contactPhone;

        @Size(max = 1000)
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffProfileResponse {
        @Size(max = 100)
        private String profession;
        private Long id;
        private String staffName;
        private String staffTitle;
        private String certNo;
        private String contactPhone;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportTextTemplateRequest {
        @NotBlank
        @Size(max = 32)
        private String templateType;

        @NotBlank
        @Size(max = 128)
        private String templateName;

        @NotBlank
        @Size(max = 6000)
        private String templateContent;

        private Boolean enabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportTextTemplateResponse {
        private Long id;
        private String templateType;
        private String templateName;
        private String templateContent;
        private Boolean enabled;
    }
}
