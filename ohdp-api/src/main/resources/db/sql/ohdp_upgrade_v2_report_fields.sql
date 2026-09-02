-- MySQL 5.7 compatibility: execute once on existing database.
ALTER TABLE `ohdp_project`
    ADD COLUMN `sample_name` VARCHAR(200) NULL AFTER `report_date`,
    ADD COLUMN `submit_unit` VARCHAR(200) NULL AFTER `sample_name`,
    ADD COLUMN `entrust_unit` VARCHAR(200) NULL AFTER `submit_unit`,
    ADD COLUMN `task_origin` VARCHAR(3000) NULL AFTER `entrust_unit`,
    ADD COLUMN `evaluation_basis` VARCHAR(3000) NULL AFTER `task_origin`,
    ADD COLUMN `writer_name` VARCHAR(64) NULL AFTER `evaluation_basis`,
    ADD COLUMN `reviewer_name` VARCHAR(64) NULL AFTER `writer_name`,
    ADD COLUMN `approver_name` VARCHAR(64) NULL AFTER `reviewer_name`,
    ADD COLUMN `writer_cert_no` VARCHAR(128) NULL AFTER `approver_name`,
    ADD COLUMN `reviewer_cert_no` VARCHAR(128) NULL AFTER `writer_cert_no`,
    ADD COLUMN `approver_cert_no` VARCHAR(128) NULL AFTER `reviewer_cert_no`,
    ADD COLUMN `statement_date` DATE NULL AFTER `approver_cert_no`;
