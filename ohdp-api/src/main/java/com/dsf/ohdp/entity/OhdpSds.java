package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description SDS 安全技术说明书
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_sds")
public class OhdpSds extends BaseAuditEntity {

    /**
     * 关联 ohdp_hazard_factor.id，可为 null
     */
    @Column(name = "factor_id")
    private Long factorId;

    @Column(name = "substance_name", nullable = false, length = 200)
    private String substanceName;

    @Column(name = "cas_no", length = 64)
    private String casNo;

    @Column(name = "version", length = 32)
    private String version;

    /**
     * 关联 sys_file.id，文件已上传后才有
     */
    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "notes", length = 500)
    private String notes;
}
