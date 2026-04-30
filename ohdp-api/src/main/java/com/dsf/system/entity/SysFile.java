package com.dsf.system.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_file")
public class SysFile extends BaseAuditEntity {

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "file_type", length = 64)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "storage_type", length = 32)
    private String storageType = "LOCAL";

    @Column(name = "uploader", length = 64)
    private String uploader;

    @Column(name = "biz_type", length = 64)
    private String bizType;

    @Column(name = "biz_id")
    private Long bizId;
}
