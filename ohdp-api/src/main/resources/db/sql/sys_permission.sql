CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `perm_code` VARCHAR(64) NOT NULL COMMENT '权限编码',
  `perm_name` VARCHAR(128) NOT NULL COMMENT '权限名称',
  `perm_type` VARCHAR(16) NOT NULL COMMENT '权限类型(MENU/BUTTON/API)',
  `api_method` VARCHAR(10) NULL COMMENT '接口方法(GET/POST/PUT/DELETE)',
  `api_path` VARCHAR(255) NULL COMMENT '接口路径(支持Ant风格)',
  `parent_id` BIGINT NULL COMMENT '父权限ID',
  `sort_no` INT NULL COMMENT '排序号',
  `status` VARCHAR(16) NOT NULL COMMENT '状态(ACTIVE/INACTIVE)',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_permission_code` (`perm_code`),
  KEY `idx_sys_permission_method_path` (`api_method`,`api_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统管理-权限管理';
