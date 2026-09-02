CREATE TABLE IF NOT EXISTS qc_inspection_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_code VARCHAR(64) NOT NULL,
    modality VARCHAR(8) NOT NULL,
    revision_no INT NOT NULL DEFAULT 1,
    source_case_id BIGINT NULL,
    sample_acceptance_no VARCHAR(100) NULL,
    inspected_unit_name VARCHAR(255) NOT NULL,
    inspected_unit_address VARCHAR(500) NULL,
    contact_name VARCHAR(100) NULL,
    contact_phone VARCHAR(100) NULL,
    equipment_name VARCHAR(255) NOT NULL,
    equipment_model VARCHAR(255) NULL,
    equipment_no VARCHAR(255) NULL,
    manufacturer VARCHAR(255) NULL,
    detection_category VARCHAR(255) NULL,
    detection_location VARCHAR(500) NULL,
    inspection_date DATE NULL,
    report_date DATE NULL,
    standard_basis VARCHAR(1000) NULL,
    instrument_description VARCHAR(2000) NULL,
    writer_name VARCHAR(100) NULL,
    reviewer_name VARCHAR(100) NULL,
    approver_name VARCHAR(100) NULL,
    review_comment VARCHAR(1000) NULL,
    approval_comment VARCHAR(1000) NULL,
    notes VARCHAR(2000) NULL,
    calculation_version VARCHAR(50) NOT NULL DEFAULT '2026.1',
    case_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_qc_case_code (case_code),
    KEY idx_qc_case_modality_status (modality, case_status),
    KEY idx_qc_case_source (source_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS qc_measurement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    item_code VARCHAR(32) NOT NULL,
    field_code VARCHAR(100) NOT NULL,
    field_label VARCHAR(255) NOT NULL,
    field_role VARCHAR(20) NOT NULL,
    value_num DECIMAL(20,8) NULL,
    value_text VARCHAR(1000) NULL,
    unit VARCHAR(100) NULL,
    sort_no INT NOT NULL DEFAULT 0,
    value_source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_qc_measurement_case_item_field (case_id, item_code, field_code),
    KEY idx_qc_measurement_case_role (case_id, field_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS qc_protection_point (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    point_code VARCHAR(50) NOT NULL,
    point_name VARCHAR(255) NOT NULL,
    location_description VARCHAR(500) NULL,
    readings_json TEXT NOT NULL,
    average_value DECIMAL(20,8) NULL,
    result_value DECIMAL(20,8) NULL,
    control_limit DECIMAL(20,8) NULL,
    judgement VARCHAR(32) NOT NULL DEFAULT 'INCOMPLETE',
    sort_no INT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_qc_protection_case_code (case_id, point_code),
    KEY idx_qc_protection_case_sort (case_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS qc_acceptance_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    modality VARCHAR(8) NOT NULL,
    item_code VARCHAR(32) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    rule_version VARCHAR(50) NOT NULL,
    lower_limit DECIMAL(20,8) NULL,
    upper_limit DECIMAL(20,8) NULL,
    expected_text VARCHAR(255) NULL,
    unit VARCHAR(100) NULL,
    standard_code VARCHAR(100) NOT NULL,
    standard_name VARCHAR(500) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_qc_rule_modality_item_version (modality, item_code, rule_version),
    KEY idx_qc_rule_lookup (modality, item_code, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS qc_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    uploaded_by VARCHAR(100) NULL,
    uploaded_at DATETIME NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_qc_attachment_case_category (case_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS qc_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_id BIGINT NOT NULL,
    report_no VARCHAR(100) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    generated_by VARCHAR(100) NULL,
    generated_at DATETIME NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_qc_report_case_time (case_id, generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO qc_acceptance_rule
    (modality, item_code, item_name, rule_version, upper_limit, unit, standard_code, standard_name, enabled, deleted, created_at, updated_at)
VALUES
    ('DR', 'RPD', '场所卫生防护', 'DEMAND-DOC-2026.1', 25, 'μSv/h', 'GBZ 130—2020', '放射诊断放射防护要求（需求报告样例控制值）', 1, 0, NOW(), NOW()),
    ('CT', 'RPD', '场所卫生防护', 'DEMAND-DOC-2026.1', 25, 'μSv/h', 'GBZ 130—2020', '放射诊断放射防护要求（需求报告样例控制值）', 1, 0, NOW(), NOW()),
    ('DR', 'ART', '伪影', '2026.1', NULL, NULL, 'WS 76—2020', '设备质量控制检测规范', 1, 0, NOW(), NOW()),
    ('DR', 'ILA', '残影', '2026.1', NULL, NULL, 'WS 76—2020', '设备质量控制检测规范', 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

UPDATE qc_acceptance_rule SET expected_text = '否' WHERE modality = 'DR' AND item_code IN ('ART', 'ILA') AND rule_version = '2026.1';
