-- 可跟踪的功能意见收集。意见提交后自动进入“已采纳”，所有处理动作写入不可变事件时间线。
CREATE TABLE IF NOT EXISTS `feedback_ticket` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `ticket_no` VARCHAR(40) NOT NULL,
  `title` VARCHAR(120) NOT NULL,
  `description` VARCHAR(4000) NOT NULL,
  `category` VARCHAR(32) NOT NULL,
  `urgency` VARCHAR(16) NOT NULL,
  `priority` VARCHAR(16) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `source_module` VARCHAR(100) NULL,
  `source_page` VARCHAR(500) NULL,
  `related_type` VARCHAR(32) NULL,
  `related_code` VARCHAR(100) NULL,
  `submitter_id` BIGINT NOT NULL,
  `submitter_name` VARCHAR(64) NOT NULL,
  `assignee_id` BIGINT NULL,
  `assignee_name` VARCHAR(64) NULL,
  `planned_completion_date` DATE NULL,
  `planned_version` VARCHAR(64) NULL,
  `resolution_summary` VARCHAR(2000) NULL,
  `resolved_at` DATETIME NULL,
  `closed_at` DATETIME NULL,
  `lock_version` BIGINT NOT NULL DEFAULT 0,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_ticket_no` (`ticket_no`),
  KEY `idx_feedback_ticket_submitter` (`submitter_id`,`updated_at`),
  KEY `idx_feedback_ticket_assignee` (`assignee_id`,`updated_at`),
  KEY `idx_feedback_ticket_status` (`status`,`priority`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能意见工单';

CREATE TABLE IF NOT EXISTS `feedback_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `ticket_id` BIGINT NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `from_status` VARCHAR(32) NULL,
  `to_status` VARCHAR(32) NULL,
  `content` VARCHAR(4000) NULL,
  `operator_id` BIGINT NOT NULL,
  `operator_name` VARCHAR(64) NOT NULL,
  `internal_only` TINYINT(1) NOT NULL DEFAULT 0,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_feedback_event_ticket` (`ticket_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能意见事件时间线';

CREATE TABLE IF NOT EXISTS `feedback_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `ticket_id` BIGINT NOT NULL,
  `original_name` VARCHAR(500) NOT NULL,
  `stored_name` VARCHAR(255) NOT NULL,
  `storage_path` VARCHAR(1000) NOT NULL,
  `media_type` VARCHAR(255) NOT NULL,
  `file_size` BIGINT NOT NULL,
  `sha256` VARCHAR(64) NOT NULL,
  `uploader_id` BIGINT NOT NULL,
  `uploader_name` VARCHAR(64) NOT NULL,
  `uploaded_at` DATETIME NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_feedback_attachment_ticket` (`ticket_id`,`uploaded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能意见附件';

CREATE TABLE IF NOT EXISTS `feedback_read_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `ticket_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `last_read_event_id` BIGINT NULL,
  `read_at` DATETIME NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_read_ticket_user` (`ticket_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='功能意见已读位置';

INSERT INTO `sys_role` (`role_code`,`role_name`,`remark`,`status`,`deleted`,`created_at`,`updated_at`) VALUES
('FEEDBACK_HANDLER','意见处理员','查看全部意见、记录处理安排并推动意见流转','ACTIVE',0,NOW(),NOW())
ON DUPLICATE KEY UPDATE `role_name`=VALUES(`role_name`),`remark`=VALUES(`remark`),`status`='ACTIVE',`deleted`=0,`updated_at`=NOW();

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`) VALUES
('FEEDBACK:READ_ALL','查看全部意见','BUTTON',NULL,NULL,NULL,700,'ACTIVE',0,NOW(),NOW()),
('FEEDBACK:HANDLE','处理意见','BUTTON',NULL,NULL,NULL,701,'ACTIVE',0,NOW(),NOW()),
('FEEDBACK:CLOSE','关闭或退回意见','BUTTON',NULL,NULL,NULL,702,'ACTIVE',0,NOW(),NOW())
ON DUPLICATE KEY UPDATE `perm_name`=VALUES(`perm_name`),`perm_type`=VALUES(`perm_type`),`sort_no`=VALUES(`sort_no`),`status`='ACTIVE',`deleted`=0,`updated_at`=NOW();

INSERT INTO `sys_role_permission` (`role_id`,`permission_id`,`deleted`,`created_at`,`updated_at`)
SELECT r.id, p.id, 0, NOW(), NOW()
FROM `sys_role` r
JOIN `sys_permission` p ON p.perm_code IN ('FEEDBACK:READ_ALL','FEEDBACK:HANDLE','FEEDBACK:CLOSE') AND p.deleted=0
WHERE r.role_code='FEEDBACK_HANDLER' AND r.deleted=0
ON DUPLICATE KEY UPDATE `deleted`=0,`updated_at`=NOW();
