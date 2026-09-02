-- 法律法规、标准规范原文版本记录。
-- 元数据表仍通过 file_id 指向当前版本，本表保留历史版本、来源与完整性校验信息。
CREATE TABLE IF NOT EXISTS `ohdp_reference_document_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reference_type` varchar(16) NOT NULL COMMENT 'LAW 或 STANDARD',
  `reference_id` bigint NOT NULL COMMENT '法规或标准主键',
  `version_no` int NOT NULL COMMENT '递增版本号',
  `version_label` varchar(64) DEFAULT NULL COMMENT '业务版本名称',
  `original_name` varchar(255) NOT NULL,
  `stored_name` varchar(255) NOT NULL,
  `storage_path` varchar(600) NOT NULL,
  `content_type` varchar(128) NOT NULL,
  `file_size` bigint NOT NULL,
  `sha256` varchar(64) NOT NULL,
  `source_url` varchar(500) DEFAULT NULL COMMENT '官方来源网址',
  `uploaded_by` varchar(64) DEFAULT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reference_document_version` (`reference_type`,`reference_id`,`version_no`),
  KEY `idx_reference_document_owner` (`reference_type`,`reference_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法规与标准原文版本';
