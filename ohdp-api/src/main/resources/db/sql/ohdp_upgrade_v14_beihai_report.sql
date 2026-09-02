-- 《（2026）定期检测报告模板-北海》项目快照数据。
-- MySQL 5.7/8.0：在现有数据库执行一次。
ALTER TABLE `ohdp_project`
    ADD COLUMN `report_supplement_json` LONGTEXT NULL AFTER `notes`;
