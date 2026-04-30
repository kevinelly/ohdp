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
@Table(name = "sys_config")
public class SysConfig extends BaseAuditEntity {

    @Column(name = "config_key", nullable = false, unique = true, length = 128)
    private String configKey;

    @Column(name = "config_value", length = 2000)
    private String configValue;

    @Column(name = "config_name", length = 200)
    private String configName;

    @Column(name = "config_type", length = 32)
    private String configType = "STRING";

    @Column(name = "notes", length = 500)
    private String notes;
}
