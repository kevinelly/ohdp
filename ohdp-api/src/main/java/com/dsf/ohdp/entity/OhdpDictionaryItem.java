package com.dsf.ohdp.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * @author kevin
 * @description 基础字典项维护
 * @unit Unit 作用说明：
 *       1) unit 表示“最小业务单元”或“计量单位”；
 *       2) 在本模块中用于统一数据口径，避免前后端对同一字段理解不一致；
 *       3) 典型值示例：kg（重量）、box（箱）、item（件）。
 */
@Getter
@Setter
@Entity
@Table(name = "ohdp_dictionary_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ohdp_dict_type_code", columnNames = {"dict_type", "item_code"})
})
public class OhdpDictionaryItem extends BaseAuditEntity {

    @Column(name = "dict_type", nullable = false, length = 64)
    private String dictType;

    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 128)
    private String itemName;

    @Column(name = "sort_no")
    private Integer sortNo;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
}

