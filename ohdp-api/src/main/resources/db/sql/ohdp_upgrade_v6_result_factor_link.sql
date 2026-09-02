-- v6: Add factor_id to ohdp_result_item for linking to ohdp_hazard_factor
ALTER TABLE ohdp_result_item
    ADD COLUMN factor_id BIGINT NULL COMMENT '关联危害因素ID' AFTER position_id;
