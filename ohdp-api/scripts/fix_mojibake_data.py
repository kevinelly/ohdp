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
            # Make sure text tables are UTF-8 compatible.
            for table in [
                "ohdp_project",
                "ohdp_project_position",
                "ohdp_result_item",
                "ohdp_company_profile",
                "ohdp_position_template",
                "ohdp_factor_template",
                "ohdp_staff_profile",
                "ohdp_report_text_template",
            ]:
                cur.execute(f"ALTER TABLE `{table}` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")

            # Replace '?' placeholders in master templates with safe defaults.
            cur.execute(
                """
                UPDATE ohdp_position_template
                SET job_name = CONCAT('POSITION_', id)
                WHERE job_name LIKE '%?%' OR job_name = '' OR job_name IS NULL
                """
            )
            cur.execute(
                """
                UPDATE ohdp_position_template
                SET shift_system = 'single'
                WHERE IFNULL(shift_system, '') LIKE '%?%'
                """
            )
            cur.execute(
                """
                UPDATE ohdp_position_template
                SET working_hours = '8h/d'
                WHERE IFNULL(working_hours, '') LIKE '%?%'
                """
            )
            cur.execute(
                """
                UPDATE ohdp_position_template
                SET operation_type = 'fixed'
                WHERE IFNULL(operation_type, '') LIKE '%?%'
                """
            )
            cur.execute(
                """
                UPDATE ohdp_position_template
                SET operation_duration = '8h'
                WHERE IFNULL(operation_duration, '') LIKE '%?%'
                """
            )
            cur.execute(
                """
                UPDATE ohdp_position_template
                SET hazards = 'noise'
                WHERE IFNULL(hazards, '') LIKE '%?%'
                """
            )
            cur.execute(
                """
                UPDATE ohdp_position_template
                SET ppe = 'mask'
                WHERE IFNULL(ppe, '') LIKE '%?%'
                """
            )
            cur.execute(
                """
                UPDATE ohdp_position_template
                SET protection_facilities = 'local ventilation'
                WHERE IFNULL(protection_facilities, '') LIKE '%?%'
                """
            )

            cur.execute(
                """
                UPDATE ohdp_factor_template
                SET factor_name = CONCAT('FACTOR_', id)
                WHERE factor_name LIKE '%?%' OR factor_name = '' OR factor_name IS NULL
                """
            )
            cur.execute(
                """
                UPDATE ohdp_factor_template
                SET metric_type = 'TWA'
                WHERE metric_type LIKE '%?%' OR metric_type = '' OR metric_type IS NULL
                """
            )
            cur.execute(
                """
                UPDATE ohdp_factor_template
                SET default_unit = 'mg/m3'
                WHERE IFNULL(default_unit, '') LIKE '%?%'
                """
            )
            cur.execute(
                """
                UPDATE ohdp_factor_template
                SET default_limit_unit = 'mg/m3'
                WHERE IFNULL(default_limit_unit, '') LIKE '%?%'
                """
            )

            # Insert baseline templates if table is empty after cleanup.
            cur.execute("SELECT COUNT(*) FROM ohdp_position_template WHERE deleted = 0")
            pos_count = int(cur.fetchone()[0])
            if pos_count == 0:
                cur.execute(
                    """
                    INSERT INTO ohdp_position_template
                    (job_name, shift_system, working_hours, operation_type, operation_duration, hazards, ppe, protection_facilities, notes, deleted, created_at, updated_at)
                    VALUES
                    ('WELD_STATION', 'single', '8h/d', 'fixed', '8h', 'noise, fume', 'mask', 'local ventilation', 'seed', 0, NOW(), NOW())
                    """
                )

            cur.execute("SELECT COUNT(*) FROM ohdp_factor_template WHERE deleted = 0")
            factor_count = int(cur.fetchone()[0])
            if factor_count == 0:
                cur.execute(
                    """
                    INSERT INTO ohdp_factor_template
                    (factor_name, metric_type, default_unit, default_limit_value, default_limit_unit, notes, deleted, created_at, updated_at)
                    VALUES
                    ('NOISE', 'LEX8H', 'dB(A)', '85', 'dB(A)', 'seed', 0, NOW(), NOW())
                    """
                )

        conn.commit()
        print("mojibake cleanup completed")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
