-- One-time repair script for legacy garbled text (mojibake).
-- Run manually after full DB backup.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER DATABASE `ohdp` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `ohdp_project` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ohdp_project_position` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ohdp_result_item` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ohdp_report_file` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ohdp_report_operation_log` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ohdp_company_profile` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ohdp_position_template` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ohdp_factor_template` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `ohdp_dictionary_item` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Optional: repair records already written as latin1-misread UTF-8.
-- Keep commented by default; enable per-column after verification in a staging DB first.
-- UPDATE ohdp_project
-- SET company_name = CONVERT(BINARY CONVERT(company_name USING latin1) USING utf8mb4)
-- WHERE company_name REGEXP '[ÃÂÅÄæçéèê]';
