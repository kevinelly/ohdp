-- Upgrade v4: decouple position from project, and normalize project unit references.

ALTER TABLE `ohdp_project`
  ADD COLUMN `submit_unit_company_id` BIGINT NULL AFTER `submit_unit`,
  ADD COLUMN `entrust_unit_company_id` BIGINT NULL AFTER `entrust_unit`;

ALTER TABLE `ohdp_project_position`
  MODIFY COLUMN `project_id` BIGINT NULL;

CREATE TABLE IF NOT EXISTS `ohdp_project_position_rel` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `project_id` BIGINT NOT NULL,
  `position_id` BIGINT NOT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ohdp_position_rel_project` (`project_id`),
  KEY `idx_ohdp_position_rel_position` (`position_id`),
  UNIQUE KEY `uk_ohdp_position_rel_project_position` (`project_id`, `position_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- backfill from old binding
INSERT INTO `ohdp_project_position_rel` (`project_id`, `position_id`, `deleted`)
SELECT p.`project_id`, p.`id`, 0
FROM `ohdp_project_position` p
LEFT JOIN `ohdp_project_position_rel` rel
  ON rel.`project_id` = p.`project_id`
 AND rel.`position_id` = p.`id`
 AND rel.`deleted` = 0
WHERE p.`deleted` = 0
  AND p.`project_id` IS NOT NULL
  AND rel.`id` IS NULL;
