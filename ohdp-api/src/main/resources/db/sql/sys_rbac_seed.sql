INSERT INTO `sys_role` (`role_code`,`role_name`,`remark`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'ADMIN','系统管理员','系统内置管理员角色','ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_code`='ADMIN' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'AUTH:LOGIN','用户登录','API','POST','/api/auth/login',NULL,10,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='AUTH:LOGIN' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'AUTH:CHANGE_PASSWORD','修改密码','API','POST','/api/auth/change-password',NULL,20,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='AUTH:CHANGE_PASSWORD' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'SYSTEM:ROLE:MANAGE','角色管理','API','GET','/api/system/roles**',NULL,100,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='SYSTEM:ROLE:MANAGE' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'SYSTEM:PERMISSION:MANAGE','权限管理','API','GET','/api/system/permissions**',NULL,110,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='SYSTEM:PERMISSION:MANAGE' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'SYSTEM:USER_ROLE:ASSIGN','用户角色绑定','API','POST','/api/system/users/*/roles',NULL,120,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='SYSTEM:USER_ROLE:ASSIGN' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'SYSTEM:ROLE_PERMISSION:ASSIGN','角色权限绑定','API','POST','/api/system/roles/*/permissions',NULL,130,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='SYSTEM:ROLE_PERMISSION:ASSIGN' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'SYSTEM:ROLE_DATA_SCOPE:ASSIGN','角色数据范围配置','API','POST','/api/system/roles/*/data-scope',NULL,140,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='SYSTEM:ROLE_DATA_SCOPE:ASSIGN' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'BASIC:STORE:READ','门店查询','API','GET','/api/stores**',NULL,200,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='BASIC:STORE:READ' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'BASIC:EMPLOYEE:READ','职员查询','API','GET','/api/employees**',NULL,210,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='BASIC:EMPLOYEE:READ' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'BASIC:CUSTOMER:READ','客户查询','API','GET','/api/customers**',NULL,220,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='BASIC:CUSTOMER:READ' AND `deleted`=0);

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`)
SELECT 'BASIC:SUPPLIER:READ','供应商查询','API','GET','/api/suppliers**',NULL,230,'ACTIVE',0,NOW(),NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `perm_code`='BASIC:SUPPLIER:READ' AND `deleted`=0);

INSERT INTO `sys_user_role` (`user_id`,`role_id`,`deleted`,`created_at`,`updated_at`)
SELECT u.id, r.id, 0, NOW(), NOW()
FROM `sys_user` u
JOIN `sys_role` r ON r.role_code='ADMIN' AND r.deleted=0
WHERE u.username='admin' AND u.deleted=0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_user_role` ur WHERE ur.user_id=u.id AND ur.role_id=r.id AND ur.deleted=0
  );

INSERT INTO `sys_role_permission` (`role_id`,`permission_id`,`deleted`,`created_at`,`updated_at`)
SELECT r.id, p.id, 0, NOW(), NOW()
FROM `sys_role` r
JOIN `sys_permission` p ON p.deleted=0
WHERE r.role_code='ADMIN' AND r.deleted=0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_permission` rp WHERE rp.role_id=r.id AND rp.permission_id=p.id AND rp.deleted=0
  );

INSERT INTO `sys_role_data_scope` (`role_id`,`scope_type`,`store_ids_json`,`deleted`,`created_at`,`updated_at`)
SELECT r.id, 'ALL', NULL, 0, NOW(), NOW()
FROM `sys_role` r
WHERE r.role_code='ADMIN' AND r.deleted=0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_role_data_scope` rs WHERE rs.role_id=r.id AND rs.deleted=0
  );
