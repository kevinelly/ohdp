CREATE TABLE IF NOT EXISTS `sys_role_data_scope` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `role_id` BIGINT NOT NULL COMMENT '角色ID',
  `scope_type` VARCHAR(32) NOT NULL COMMENT '数据范围类型(ALL/HEADQUARTERS/STORE_SELF/STORE_IDS)',
  `store_ids_json` VARCHAR(2000) NULL COMMENT '门店ID集合(JSON)',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_role_data_scope_role` (`role_id`),
  KEY `idx_sys_role_data_scope_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统管理-角色数据权限';
