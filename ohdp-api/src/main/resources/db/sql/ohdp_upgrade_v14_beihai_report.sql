-- 《（2026）定期检测报告模板-北海》项目快照数据。
-- MySQL 5.7/8.0：新库和已被 Hibernate 建成 TINYTEXT 的旧库均可执行。
SET @report_supplement_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ohdp_project'
      AND COLUMN_NAME = 'report_supplement_json'
);
SET @report_supplement_ddl = IF(
    @report_supplement_column_exists = 0,
    'ALTER TABLE `ohdp_project` ADD COLUMN `report_supplement_json` LONGTEXT NULL AFTER `notes`',
    'ALTER TABLE `ohdp_project` MODIFY COLUMN `report_supplement_json` LONGTEXT NULL'
);
PREPARE report_supplement_stmt FROM @report_supplement_ddl;
EXECUTE report_supplement_stmt;
DEALLOCATE PREPARE report_supplement_stmt;
