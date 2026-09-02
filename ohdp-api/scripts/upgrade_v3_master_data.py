from __future__ import annotations

import pathlib
import re

import pymysql


def parse_properties(path: pathlib.Path) -> dict[str, str]:
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def load_db_config(root: pathlib.Path) -> tuple[str, int, str, str, str]:
    props = parse_properties(root / "src" / "main" / "resources" / "application.properties")
    jdbc = props["spring.datasource.url"]
    username = props["spring.datasource.username"]
    password = props["spring.datasource.password"]
    match = re.match(r"^jdbc:mysql://([^:/?#]+)(?::(\d+))?/([^?]+)", jdbc)
    if not match:
        raise ValueError(f"Unsupported datasource url: {jdbc}")
    host = match.group(1)
    port = int(match.group(2) or 3306)
    db = match.group(3)
    return host, port, db, username, password


def ensure_column(
    conn: pymysql.connections.Connection,
    db: str,
    table: str,
    column: str,
    ddl: str,
) -> None:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s AND COLUMN_NAME=%s",
            (db, table, column),
        )
        exists = cur.fetchone()[0] > 0
        if exists:
            print(f"skip column {table}.{column}")
            return
        cur.execute(ddl)
        print(f"add column {table}.{column}")


def main() -> None:
    root = pathlib.Path(__file__).resolve().parents[1]
    host, port, db, user, pwd = load_db_config(root)
    conn = pymysql.connect(
        host=host,
        port=port,
        user=user,
        password=pwd,
        db=db,
        charset="utf8mb4",
        init_command="SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci",
        autocommit=False,
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS `ohdp_staff_profile` (
                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                  `staff_name` VARCHAR(64) NOT NULL,
                  `staff_title` VARCHAR(64) NULL,
                  `cert_no` VARCHAR(128) NOT NULL,
                  `contact_phone` VARCHAR(50) NULL,
                  `notes` VARCHAR(1000) NULL,
                  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
                  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  KEY `idx_ohdp_staff_name` (`staff_name`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """
            )
            cur.execute(
                """
                CREATE TABLE IF NOT EXISTS `ohdp_report_text_template` (
                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                  `template_type` VARCHAR(32) NOT NULL,
                  `template_name` VARCHAR(128) NOT NULL,
                  `template_content` VARCHAR(6000) NOT NULL,
                  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
                  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  KEY `idx_ohdp_report_text_type` (`template_type`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                """
            )

        ensure_column(
            conn,
            db,
            "ohdp_project",
            "company_profile_id",
            "ALTER TABLE `ohdp_project` ADD COLUMN `company_profile_id` BIGINT NULL AFTER `company_name`",
        )
        ensure_column(
            conn,
            db,
            "ohdp_project",
            "detect_type_dict_item_id",
            "ALTER TABLE `ohdp_project` ADD COLUMN `detect_type_dict_item_id` BIGINT NULL AFTER `detect_type`",
        )
        ensure_column(
            conn,
            db,
            "ohdp_project",
            "task_origin_template_id",
            "ALTER TABLE `ohdp_project` ADD COLUMN `task_origin_template_id` BIGINT NULL AFTER `task_origin`",
        )
        ensure_column(
            conn,
            db,
            "ohdp_project",
            "evaluation_basis_template_id",
            "ALTER TABLE `ohdp_project` ADD COLUMN `evaluation_basis_template_id` BIGINT NULL AFTER `evaluation_basis`",
        )
        ensure_column(
            conn,
            db,
            "ohdp_project",
            "writer_staff_id",
            "ALTER TABLE `ohdp_project` ADD COLUMN `writer_staff_id` BIGINT NULL AFTER `writer_name`",
        )
        ensure_column(
            conn,
            db,
            "ohdp_project",
            "reviewer_staff_id",
            "ALTER TABLE `ohdp_project` ADD COLUMN `reviewer_staff_id` BIGINT NULL AFTER `reviewer_name`",
        )
        ensure_column(
            conn,
            db,
            "ohdp_project",
            "approver_staff_id",
            "ALTER TABLE `ohdp_project` ADD COLUMN `approver_staff_id` BIGINT NULL AFTER `approver_name`",
        )
        ensure_column(
            conn,
            db,
            "ohdp_result_item",
            "position_id",
            "ALTER TABLE `ohdp_result_item` ADD COLUMN `position_id` BIGINT NULL AFTER `project_id`",
        )
        conn.commit()
        print("upgrade v3 done")
    finally:
        conn.close()


if __name__ == "__main__":
    main()

