CREATE TABLE IF NOT EXISTS `ohdp_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_code` VARCHAR(64) NOT NULL,
  `company_name` VARCHAR(200) NOT NULL,
  `company_profile_id` BIGINT NULL,
  `company_address` VARCHAR(255) NOT NULL,
  `detect_type` VARCHAR(64) NOT NULL,
  `detect_type_dict_item_id` BIGINT NULL,
  `sampling_date` DATE NULL,
  `report_date` DATE NULL,
  `sample_name` VARCHAR(200) NULL,
  `submit_unit` VARCHAR(200) NULL,
  `submit_unit_company_id` BIGINT NULL,
  `entrust_unit` VARCHAR(200) NULL,
  `entrust_unit_company_id` BIGINT NULL,
  `task_origin` VARCHAR(3000) NULL,
  `task_origin_template_id` BIGINT NULL,
  `evaluation_basis` VARCHAR(3000) NULL,
  `evaluation_basis_template_id` BIGINT NULL,
  `writer_name` VARCHAR(64) NULL,
  `writer_staff_id` BIGINT NULL,
  `reviewer_name` VARCHAR(64) NULL,
  `reviewer_staff_id` BIGINT NULL,
  `approver_name` VARCHAR(64) NULL,
  `approver_staff_id` BIGINT NULL,
  `writer_cert_no` VARCHAR(128) NULL,
  `reviewer_cert_no` VARCHAR(128) NULL,
  `approver_cert_no` VARCHAR(128) NULL,
  `statement_date` DATE NULL,
  `notes` VARCHAR(1000) NULL,
  `status` VARCHAR(32) NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ohdp_project_code` (`project_code`),
  KEY `idx_ohdp_project_company_name` (`company_name`),
  KEY `idx_ohdp_project_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_project_position` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NULL,
  `job_name` VARCHAR(100) NOT NULL,
  `shift_system` VARCHAR(50) NULL,
  `working_hours` VARCHAR(50) NULL,
  `worker_count` INT NULL,
  `work_location` VARCHAR(150) NULL,
  `operation_type` VARCHAR(100) NULL,
  `operation_content` VARCHAR(255) NULL,
  `operation_duration` VARCHAR(50) NULL,
  `hazards` VARCHAR(500) NULL,
  `ppe` VARCHAR(500) NULL,
  `protection_facilities` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_position_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_project_position_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `position_id` BIGINT NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_position_rel_project` (`project_id`),
  KEY `idx_ohdp_position_rel_position` (`position_id`),
  UNIQUE KEY `uk_ohdp_position_rel_project_position` (`project_id`, `position_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_result_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `position_id` BIGINT NULL,
  `job_name` VARCHAR(100) NOT NULL,
  `factor_name` VARCHAR(120) NOT NULL,
  `metric_type` VARCHAR(40) NOT NULL,
  `value_raw` VARCHAR(80) NULL,
  `value_num` DECIMAL(16,6) NULL,
  `unit` VARCHAR(32) NULL,
  `limit_value` DECIMAL(16,6) NULL,
  `limit_unit` VARCHAR(32) NULL,
  `judgement` VARCHAR(20) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_result_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_staff_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `staff_name` VARCHAR(64) NOT NULL,
  `staff_title` VARCHAR(64) NULL,
  `cert_no` VARCHAR(128) NOT NULL,
  `contact_phone` VARCHAR(50) NULL,
  `notes` VARCHAR(1000) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_staff_name` (`staff_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_report_text_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `template_type` VARCHAR(32) NOT NULL,
  `template_name` VARCHAR(128) NOT NULL,
  `template_content` VARCHAR(6000) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_report_text_type` (`template_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_report_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `file_path` VARCHAR(500) NOT NULL,
  `template_version` VARCHAR(64) NULL,
  `generated_by` VARCHAR(64) NULL,
  `generated_at` DATETIME NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ohdp_report_project` (`project_id`),
  KEY `idx_ohdp_report_generated_at` (`generated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_report_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `operation_type` VARCHAR(32) NOT NULL,
  `operator_name` VARCHAR(64) NULL,
  `operation_message` VARCHAR(1000) NULL,
  `operated_at` DATETIME NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_report_log_project` (`project_id`),
  KEY `idx_ohdp_report_log_time` (`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_company_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_name` VARCHAR(200) NOT NULL,
  `company_address` VARCHAR(255) NOT NULL,
  `contact_name` VARCHAR(100) NULL,
  `contact_phone` VARCHAR(50) NULL,
  `industry_type` VARCHAR(100) NULL,
  `notes` VARCHAR(1000) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ohdp_company_name` (`company_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_position_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `job_name` VARCHAR(100) NOT NULL,
  `shift_system` VARCHAR(50) NULL,
  `working_hours` VARCHAR(50) NULL,
  `operation_type` VARCHAR(100) NULL,
  `operation_duration` VARCHAR(50) NULL,
  `hazards` VARCHAR(500) NULL,
  `ppe` VARCHAR(500) NULL,
  `protection_facilities` VARCHAR(500) NULL,
  `notes` VARCHAR(1000) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_position_template_name` (`job_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_factor_template` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `factor_name` VARCHAR(120) NOT NULL,
  `metric_type` VARCHAR(40) NOT NULL,
  `default_unit` VARCHAR(32) NULL,
  `default_limit_value` VARCHAR(32) NULL,
  `default_limit_unit` VARCHAR(32) NULL,
  `notes` VARCHAR(1000) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_factor_template_name` (`factor_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `ohdp_dictionary_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dict_type` VARCHAR(64) NOT NULL,
  `item_code` VARCHAR(64) NOT NULL,
  `item_name` VARCHAR(128) NOT NULL,
  `sort_no` INT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ohdp_dict_type_code` (`dict_type`, `item_code`),
  KEY `idx_ohdp_dict_type_sort` (`dict_type`, `sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
