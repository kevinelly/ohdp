package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 职业危害项目主表
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_project", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ohdp_project_code", columnNames = "project_code")
})
public class OhdpProject extends BaseAuditEntity {

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "company_profile_id")
    private Long companyProfileId;

    @Column(name = "company_address", nullable = false, length = 255)
    private String companyAddress;

    @Column(name = "detect_type", nullable = false, length = 64)
    private String detectType;

    @Column(name = "detect_type_dict_item_id")
    private Long detectTypeDictItemId;

    @Column(name = "sampling_date")
    private LocalDate samplingDate;

    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "sample_name", length = 200)
    private String sampleName;

    @Column(name = "submit_unit", length = 200)
    private String submitUnit;

    @Column(name = "submit_unit_company_id")
    private Long submitUnitCompanyId;

    @Column(name = "entrust_unit", length = 200)
    private String entrustUnit;

    @Column(name = "entrust_unit_company_id")
    private Long entrustUnitCompanyId;

    @Column(name = "task_origin", length = 3000)
    private String taskOrigin;

    @Column(name = "task_origin_template_id")
    private Long taskOriginTemplateId;

    @Column(name = "evaluation_basis", length = 3000)
    private String evaluationBasis;

    @Column(name = "evaluation_basis_template_id")
    private Long evaluationBasisTemplateId;

    @Column(name = "writer_name", length = 64)
    private String writerName;

    @Column(name = "writer_staff_id")
    private Long writerStaffId;

    @Column(name = "reviewer_name", length = 64)
    private String reviewerName;

    @Column(name = "reviewer_staff_id")
    private Long reviewerStaffId;

    @Column(name = "approver_name", length = 64)
    private String approverName;

    @Column(name = "approver_staff_id")
    private Long approverStaffId;

    @Column(name = "writer_cert_no", length = 128)
    private String writerCertNo;

    @Column(name = "reviewer_cert_no", length = 128)
    private String reviewerCertNo;

    @Column(name = "approver_cert_no", length = 128)
    private String approverCertNo;

    @Column(name = "statement_date")
    private LocalDate statementDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    /**
     * 北海市 2026 定期检测报告的项目快照数据。
     *
     * <p>该字段保存单位基本信息、原辅材料、设备、防护设施、采样计划、
     * 现场条件和结论建议等结构化内容。数据作为项目快照保存，避免主数据
     * 后续修改导致已经归档的报告内容发生变化。</p>
    */
    @Lob
    @Column(name = "report_supplement_json", columnDefinition = "LONGTEXT")
    private String reportSupplementJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProjectStatus status;
}
