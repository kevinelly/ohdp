-- 2026.4：CT 模体信息、DR 年有效剂量复核参数。
-- Docker 部署使用 Hibernate update 自动执行等价结构变更；本脚本供手工升级数据库时使用。

ALTER TABLE qc_inspection_case
    ADD COLUMN phantom_name VARCHAR(255) NULL AFTER manufacturer,
    ADD COLUMN phantom_manufacturer VARCHAR(255) NULL AFTER phantom_name,
    ADD COLUMN phantom_model VARCHAR(255) NULL AFTER phantom_manufacturer,
    ADD COLUMN phantom_serial_no VARCHAR(255) NULL AFTER phantom_model,
    ADD COLUMN phantom_manual_reference VARCHAR(1000) NULL AFTER phantom_serial_no;

ALTER TABLE qc_protection_point
    ADD COLUMN annual_workload_mas DECIMAL(20,8) NULL AFTER control_limit,
    ADD COLUMN occupancy_factor DECIMAL(20,8) NULL AFTER annual_workload_mas,
    ADD COLUMN use_factor DECIMAL(20,8) NULL AFTER occupancy_factor,
    ADD COLUMN annual_effective_dose DECIMAL(20,8) NULL AFTER use_factor,
    ADD COLUMN annual_dose_limit DECIMAL(20,8) NULL AFTER annual_effective_dose;
