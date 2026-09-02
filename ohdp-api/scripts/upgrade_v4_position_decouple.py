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


def table_exists(conn: pymysql.connections.Connection, db: str, table: str) -> bool:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s",
            (db, table),
        )
        return cur.fetchone()[0] > 0


def ensure_project_id_nullable(conn: pymysql.connections.Connection, db: str) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT IS_NULLABLE FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA=%s AND TABLE_NAME='ohdp_project_position' AND COLUMN_NAME='project_id'
            """,
            (db,),
        )
        row = cur.fetchone()
        if row and str(row[0]).upper() == "YES":
            print("skip modify ohdp_project_position.project_id nullable")
            return
        cur.execute("ALTER TABLE `ohdp_project_position` MODIFY COLUMN `project_id` BIGINT NULL")
        print("modify ohdp_project_position.project_id nullable")


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
        ensure_column(
            conn,
            db,
            "ohdp_project",
            "submit_unit_company_id",
            "ALTER TABLE `ohdp_project` ADD COLUMN `submit_unit_company_id` BIGINT NULL AFTER `submit_unit`",
        )
        ensure_column(
            conn,
            db,
            "ohdp_project",
            "entrust_unit_company_id",
            "ALTER TABLE `ohdp_project` ADD COLUMN `entrust_unit_company_id` BIGINT NULL AFTER `entrust_unit`",
        )

        ensure_project_id_nullable(conn, db)

        if not table_exists(conn, db, "ohdp_project_position_rel"):
            with conn.cursor() as cur:
                cur.execute(
                    """
                    CREATE TABLE `ohdp_project_position_rel` (
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
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
                    """
                )
                print("create table ohdp_project_position_rel")
        else:
            print("skip table ohdp_project_position_rel")

        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO `ohdp_project_position_rel` (`project_id`, `position_id`, `deleted`)
                SELECT p.`project_id`, p.`id`, 0
                FROM `ohdp_project_position` p
                LEFT JOIN `ohdp_project_position_rel` rel
                  ON rel.`project_id` = p.`project_id`
                 AND rel.`position_id` = p.`id`
                 AND rel.`deleted` = 0
                WHERE p.`deleted` = 0
                  AND p.`project_id` IS NOT NULL
                  AND rel.`id` IS NULL
                """
            )
            print(f"backfill relation rows: {cur.rowcount}")

        conn.commit()
        print("upgrade v4 done")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
