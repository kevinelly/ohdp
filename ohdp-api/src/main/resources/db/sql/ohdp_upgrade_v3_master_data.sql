-- Upgrade for master-data driven selection.
-- Note: for MySQL 5.7/8 compatibility in production, execute with conditional checks (scripts) instead of direct rerun.

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

