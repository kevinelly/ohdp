-- 放射诊疗检测工作流权限。界面按钮权限与 API 权限分离，便于按岗位组合授权。
INSERT INTO `sys_role` (`role_code`,`role_name`,`remark`,`status`,`deleted`,`created_at`,`updated_at`) VALUES
('RADIOLOGY_REVIEWER','放射检测审核员','复核 DR/CT 原始值、计算结果与防护点位','ACTIVE',0,NOW(),NOW()),
('RADIOLOGY_APPROVER','放射检测批准人','批准已审核的 DR/CT 检测案例','ACTIVE',0,NOW(),NOW()),
('RADIOLOGY_REPORTER','放射检测报告员','生成、归档并修订 DR/CT 检测报告','ACTIVE',0,NOW(),NOW()),
('RADIOLOGY_RULE_ADMIN','放射规则管理员','维护 DR/CT 版本化判定规则','ACTIVE',0,NOW(),NOW())
ON DUPLICATE KEY UPDATE `role_name`=VALUES(`role_name`),`remark`=VALUES(`remark`),`status`='ACTIVE',`deleted`=0,`updated_at`=NOW();

INSERT INTO `sys_permission` (`perm_code`,`perm_name`,`perm_type`,`api_method`,`api_path`,`parent_id`,`sort_no`,`status`,`deleted`,`created_at`,`updated_at`) VALUES
('RADIOLOGY:WORKFLOW:REVIEW','放射检测审核按钮','BUTTON',NULL,NULL,NULL,500,'ACTIVE',0,NOW(),NOW()),
('RADIOLOGY:WORKFLOW:REVIEW:API','放射检测审核接口','API','POST','/api/radiology/cases/*/workflow/review',NULL,501,'ACTIVE',0,NOW(),NOW()),
('RADIOLOGY:WORKFLOW:APPROVE','放射检测批准按钮','BUTTON',NULL,NULL,NULL,510,'ACTIVE',0,NOW(),NOW()),
('RADIOLOGY:WORKFLOW:APPROVE:API','放射检测批准接口','API','POST','/api/radiology/cases/*/workflow/approve',NULL,511,'ACTIVE',0,NOW(),NOW()),
('RADIOLOGY:REPORT:GENERATE','放射检测报告按钮','BUTTON',NULL,NULL,NULL,520,'ACTIVE',0,NOW(),NOW()),
('RADIOLOGY:REPORT:GENERATE:API','放射检测报告生成接口','API','POST','/api/radiology/cases/*/reports',NULL,521,'ACTIVE',0,NOW(),NOW()),
('RADIOLOGY:REVISION:CREATE:API','放射检测修订接口','API','POST','/api/radiology/cases/*/workflow/revision',NULL,522,'ACTIVE',0,NOW(),NOW()),
('RADIOLOGY:RULE:MANAGE','放射判定规则按钮','BUTTON',NULL,NULL,NULL,530,'ACTIVE',0,NOW(),NOW()),
('RADIOLOGY:RULE:CREATE:API','放射判定规则新增接口','API','POST','/api/radiology/rules',NULL,531,'ACTIVE',0,NOW(),NOW()),
('RADIOLOGY:RULE:UPDATE:API','放射判定规则修改接口','API','PUT','/api/radiology/rules/*',NULL,532,'ACTIVE',0,NOW(),NOW()),
('RADIOLOGY:RULE:DELETE:API','放射判定规则删除接口','API','DELETE','/api/radiology/rules/*',NULL,533,'ACTIVE',0,NOW(),NOW())
ON DUPLICATE KEY UPDATE `perm_name`=VALUES(`perm_name`),`perm_type`=VALUES(`perm_type`),`api_method`=VALUES(`api_method`),`api_path`=VALUES(`api_path`),`sort_no`=VALUES(`sort_no`),`status`='ACTIVE',`deleted`=0,`updated_at`=NOW();

INSERT INTO `sys_role_permission` (`role_id`,`permission_id`,`deleted`,`created_at`,`updated_at`)
SELECT r.id, p.id, 0, NOW(), NOW()
FROM `sys_role` r
JOIN `sys_permission` p ON p.perm_code IN ('RADIOLOGY:WORKFLOW:REVIEW','RADIOLOGY:WORKFLOW:REVIEW:API') AND p.deleted=0
WHERE r.role_code='RADIOLOGY_REVIEWER' AND r.deleted=0
ON DUPLICATE KEY UPDATE `deleted`=0,`updated_at`=NOW();

INSERT INTO `sys_role_permission` (`role_id`,`permission_id`,`deleted`,`created_at`,`updated_at`)
SELECT r.id, p.id, 0, NOW(), NOW()
FROM `sys_role` r
JOIN `sys_permission` p ON p.perm_code IN ('RADIOLOGY:WORKFLOW:APPROVE','RADIOLOGY:WORKFLOW:APPROVE:API') AND p.deleted=0
WHERE r.role_code='RADIOLOGY_APPROVER' AND r.deleted=0
ON DUPLICATE KEY UPDATE `deleted`=0,`updated_at`=NOW();

INSERT INTO `sys_role_permission` (`role_id`,`permission_id`,`deleted`,`created_at`,`updated_at`)
SELECT r.id, p.id, 0, NOW(), NOW()
FROM `sys_role` r
JOIN `sys_permission` p ON p.perm_code IN ('RADIOLOGY:REPORT:GENERATE','RADIOLOGY:REPORT:GENERATE:API','RADIOLOGY:REVISION:CREATE:API') AND p.deleted=0
WHERE r.role_code='RADIOLOGY_REPORTER' AND r.deleted=0
ON DUPLICATE KEY UPDATE `deleted`=0,`updated_at`=NOW();

INSERT INTO `sys_role_permission` (`role_id`,`permission_id`,`deleted`,`created_at`,`updated_at`)
SELECT r.id, p.id, 0, NOW(), NOW()
FROM `sys_role` r
JOIN `sys_permission` p ON p.perm_code IN ('RADIOLOGY:RULE:MANAGE','RADIOLOGY:RULE:CREATE:API','RADIOLOGY:RULE:UPDATE:API','RADIOLOGY:RULE:DELETE:API') AND p.deleted=0
WHERE r.role_code='RADIOLOGY_RULE_ADMIN' AND r.deleted=0
ON DUPLICATE KEY UPDATE `deleted`=0,`updated_at`=NOW();
