-- Upgrade v5: New modules - BasicData, HazardFactor, Config, Lab, System management tables
-- Execute once against ohdp database

-- ==================== 基础数据：法律法规 ====================
CREATE TABLE IF NOT EXISTS `ohdp_law` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(300) NULL,
  `doc_no` VARCHAR(128) NULL,
  `level` VARCHAR(32) NULL COMMENT 'NATIONAL/PROVINCIAL/LOCAL',
  `publish_date` DATE NULL,
  `effective_date` DATE NULL,
  `publisher` VARCHAR(100) NULL,
  `is_valid` TINYINT(1) NOT NULL DEFAULT 1,
  `is_open` TINYINT(1) NOT NULL DEFAULT 1,
  `file_id` BIGINT NULL,
  `notes` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_law_doc_no` (`doc_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 基础数据：标准规范 ====================
CREATE TABLE IF NOT EXISTS `ohdp_standard` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `standard_name` VARCHAR(300) NOT NULL,
  `standard_code` VARCHAR(128) NULL,
  `level` VARCHAR(32) NULL COMMENT 'NATIONAL/INDUSTRY',
  `use_scope` VARCHAR(100) NULL,
  `publish_date` DATE NULL,
  `is_valid` TINYINT(1) NOT NULL DEFAULT 1,
  `is_open` TINYINT(1) NOT NULL DEFAULT 1,
  `file_id` BIGINT NULL,
  `notes` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ohdp_standard_code` (`standard_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 基础数据：地区字典 ====================
CREATE TABLE IF NOT EXISTS `ohdp_region` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `parent_id` BIGINT NULL,
  `region_code` VARCHAR(32) NOT NULL,
  `region_name` VARCHAR(100) NOT NULL,
  `level` INT NOT NULL COMMENT '1=省 2=市 3=区县',
  `sort_no` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ohdp_region_code` (`region_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 基础数据：设备管理 ====================
CREATE TABLE IF NOT EXISTS `ohdp_device` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `device_name` VARCHAR(200) NOT NULL,
  `device_type` VARCHAR(32) NOT NULL COMMENT 'LAB_INSTRUMENT/SAMPLER',
  `device_model` VARCHAR(100) NULL,
  `device_no` VARCHAR(100) NULL,
  `manufacturer` VARCHAR(200) NULL,
  `calibration_date` DATE NULL,
  `calibration_expiry` DATE NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/MAINTENANCE/SCRAPPED',
  `notes` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_device_type` (`device_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 危害因素主表 ====================
CREATE TABLE IF NOT EXISTS `ohdp_hazard_factor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `factor_name` VARCHAR(200) NOT NULL,
  `factor_code` VARCHAR(64) NULL,
  `factor_category` VARCHAR(32) NOT NULL COMMENT 'PHYSICAL/CHEMICAL/DUST/BIOLOGICAL/RADIATION/GENERAL/OTHER',
  `cas_no` VARCHAR(64) NULL,
  `english_name` VARCHAR(200) NULL,
  `sort_no` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_hazard_factor_name` (`factor_name`),
  KEY `idx_ohdp_hazard_factor_category` (`factor_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 危害因素限值 ====================
CREATE TABLE IF NOT EXISTS `ohdp_hazard_factor_limit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `factor_id` BIGINT NOT NULL,
  `limit_type` VARCHAR(32) NOT NULL COMMENT 'PC_TWA/PC_STEL/MAC/OEL/MEL/OTHER',
  `limit_value` DECIMAL(16,6) NULL,
  `limit_unit` VARCHAR(32) NULL,
  `standard_code` VARCHAR(128) NULL,
  `notes` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_factor_limit_factor_id` (`factor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 危害因素检测方法关联 ====================
CREATE TABLE IF NOT EXISTS `ohdp_hazard_factor_method` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `factor_id` BIGINT NOT NULL,
  `method_id` BIGINT NOT NULL,
  `is_default` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_hazard_factor_method_factor` (`factor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== SDS 安全技术说明书 ====================
CREATE TABLE IF NOT EXISTS `ohdp_sds` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `factor_id` BIGINT NULL,
  `substance_name` VARCHAR(200) NOT NULL,
  `cas_no` VARCHAR(64) NULL,
  `version` VARCHAR(32) NULL,
  `file_id` BIGINT NULL,
  `notes` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_sds_factor_id` (`factor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 配置中心：检测方法 ====================
CREATE TABLE IF NOT EXISTS `ohdp_detection_method` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `method_name` VARCHAR(200) NOT NULL,
  `method_code` VARCHAR(64) NULL,
  `method_abbr` VARCHAR(32) NULL,
  `formula` VARCHAR(500) NULL,
  `formula_code` VARCHAR(64) NULL,
  `standard_code` VARCHAR(128) NULL,
  `result_decimal_places` INT NOT NULL DEFAULT 4,
  `calc_decimal_places` INT NOT NULL DEFAULT 4,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ohdp_detection_method_code` (`method_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 配置中心：机构配置 ====================
CREATE TABLE IF NOT EXISTS `ohdp_org_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `org_name` VARCHAR(200) NULL,
  `org_code` VARCHAR(64) NULL,
  `org_address` VARCHAR(255) NULL,
  `contact_name` VARCHAR(64) NULL,
  `contact_phone` VARCHAR(50) NULL,
  `cert_no` VARCHAR(128) NULL,
  `cert_file_id` BIGINT NULL,
  `sample_no_prefix` VARCHAR(20) NOT NULL DEFAULT 'S',
  `sample_no_year_format` VARCHAR(10) NOT NULL DEFAULT 'YYYY',
  `controlled_no_prefix` VARCHAR(20) NOT NULL DEFAULT 'KZ',
  `report_no_prefix` VARCHAR(20) NOT NULL DEFAULT 'R',
  `report_no_year_format` VARCHAR(10) NOT NULL DEFAULT 'YYYY',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 配置中心：检测分类 ====================
CREATE TABLE IF NOT EXISTS `ohdp_detect_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `type_name` VARCHAR(200) NOT NULL,
  `type_code` VARCHAR(64) NOT NULL,
  `type_abbr` VARCHAR(32) NULL,
  `sort_no` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ohdp_detect_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 配置中心：报告模板目录 ====================
CREATE TABLE IF NOT EXISTS `ohdp_report_template_catalog` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `detect_type_id` BIGINT NULL,
  `catalog_name` VARCHAR(200) NOT NULL,
  `catalog_code` VARCHAR(64) NULL,
  `parent_id` BIGINT NULL,
  `sort_no` INT NOT NULL DEFAULT 0,
  `is_required` TINYINT(1) NOT NULL DEFAULT 0,
  `notes` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_report_catalog_detect_type` (`detect_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 实验室：任务单 ====================
CREATE TABLE IF NOT EXISTS `ohdp_lab_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_code` VARCHAR(64) NOT NULL,
  `project_id` BIGINT NOT NULL,
  `task_status` VARCHAR(32) NOT NULL,
  `issuer_name` VARCHAR(64) NULL,
  `issued_date` DATE NULL,
  `receiver_name` VARCHAR(64) NULL,
  `start_time` DATETIME NULL,
  `end_time` DATETIME NULL,
  `reviewer_name` VARCHAR(64) NULL,
  `review_note` VARCHAR(500) NULL,
  `notes` VARCHAR(1000) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ohdp_lab_task_code` (`task_code`),
  KEY `idx_ohdp_lab_task_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 实验室：任务单样品明细 ====================
CREATE TABLE IF NOT EXISTS `ohdp_lab_task_sample` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `sample_code` VARCHAR(64) NOT NULL,
  `factor_name` VARCHAR(200) NOT NULL,
  `factor_id` BIGINT NULL,
  `sample_count` INT NULL,
  `sample_date` DATE NULL,
  `sample_type` VARCHAR(32) NULL,
  `notes` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_lab_task_sample_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 实验室：检测结果 ====================
CREATE TABLE IF NOT EXISTS `ohdp_lab_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `task_id` BIGINT NOT NULL,
  `sample_id` BIGINT NULL,
  `factor_id` BIGINT NULL,
  `factor_name` VARCHAR(200) NOT NULL,
  `method_id` BIGINT NULL,
  `method_name` VARCHAR(200) NULL,
  `intercept_a` DECIMAL(20,8) NULL,
  `slope_b` DECIMAL(20,8) NULL,
  `correlation_r` DECIMAL(10,8) NULL,
  `desorption_volume` DECIMAL(10,4) NOT NULL DEFAULT 1,
  `desorption_efficiency` DECIMAL(10,4) NOT NULL DEFAULT 100,
  `sampling_volume` DECIMAL(10,4) NULL,
  `result_decimal_places` INT NOT NULL DEFAULT 4,
  `calc_decimal_places` INT NOT NULL DEFAULT 4,
  `result_value` DECIMAL(16,6) NULL,
  `result_unit` VARCHAR(32) NULL,
  `min_detect_value` DECIMAL(16,6) NULL,
  `min_quantify_value` DECIMAL(16,6) NULL,
  `analyst_name` VARCHAR(64) NULL,
  `analysis_date` DATE NULL,
  `notes` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_lab_result_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 实验室：标准曲线数据点 ====================
CREATE TABLE IF NOT EXISTS `ohdp_lab_curve_point` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `lab_result_id` BIGINT NOT NULL,
  `point_index` INT NOT NULL,
  `concentration` DECIMAL(16,6) NULL,
  `absorbance` DECIMAL(16,6) NULL,
  `absorbance_raw` DECIMAL(16,6) NULL,
  `is_excluded` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_lab_curve_result_id` (`lab_result_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 系统管理：部门 ====================
CREATE TABLE IF NOT EXISTS `sys_department` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `parent_id` BIGINT NULL,
  `dept_name` VARCHAR(100) NOT NULL,
  `dept_code` VARCHAR(64) NULL,
  `sort_no` INT NOT NULL DEFAULT 0,
  `leader_name` VARCHAR(64) NULL,
  `contact_phone` VARCHAR(50) NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sys_dept_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 系统管理：岗位 ====================
CREATE TABLE IF NOT EXISTS `sys_post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_name` VARCHAR(100) NOT NULL,
  `post_code` VARCHAR(64) NULL,
  `sort_no` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `notes` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 系统管理：通知公告 ====================
CREATE TABLE IF NOT EXISTS `sys_notice` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT NULL,
  `notice_type` VARCHAR(32) NULL,
  `publisher` VARCHAR(64) NULL,
  `publish_time` DATETIME NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 系统管理：系统日志 ====================
CREATE TABLE IF NOT EXISTS `sys_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_name` VARCHAR(64) NULL,
  `operation` VARCHAR(200) NULL,
  `method` VARCHAR(200) NULL,
  `params` TEXT NULL,
  `result_type` VARCHAR(20) NULL,
  `ip` VARCHAR(50) NULL,
  `cost_time` BIGINT NULL,
  `module` VARCHAR(64) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sys_log_user_name` (`user_name`),
  KEY `idx_sys_log_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 系统管理：附件文件 ====================
CREATE TABLE IF NOT EXISTS `sys_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `file_name` VARCHAR(255) NOT NULL,
  `original_name` VARCHAR(255) NULL,
  `file_type` VARCHAR(64) NULL,
  `file_size` BIGINT NULL,
  `file_path` VARCHAR(500) NOT NULL,
  `storage_type` VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
  `uploader` VARCHAR(64) NULL,
  `biz_type` VARCHAR(64) NULL,
  `biz_id` BIGINT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sys_file_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==================== 系统管理：参数配置 ====================
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(128) NOT NULL,
  `config_value` VARCHAR(2000) NULL,
  `config_name` VARCHAR(200) NULL,
  `config_type` VARCHAR(32) NOT NULL DEFAULT 'STRING',
  `notes` VARCHAR(500) NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
