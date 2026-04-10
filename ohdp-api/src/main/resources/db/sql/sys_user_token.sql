CREATE TABLE IF NOT EXISTS `sys_user_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `token_hash` VARCHAR(128) NOT NULL COMMENT 'Token哈希值',
  `client_type` VARCHAR(32) NULL COMMENT '客户端类型',
  `issued_at` DATETIME NOT NULL COMMENT '签发时间',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `revoked_at` DATETIME NULL COMMENT '吊销时间',
  `last_access_at` DATETIME NULL COMMENT '最后访问时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_token_hash` (`token_hash`),
  KEY `idx_sys_user_token_user_id` (`user_id`),
  KEY `idx_sys_user_token_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统管理-用户Token';
