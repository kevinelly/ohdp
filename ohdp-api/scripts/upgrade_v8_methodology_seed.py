"""Run the v8 migration: seed factor templates with Chinese names + methodology data."""
from __future__ import annotations

import pathlib
import re

import pymysql
from pymysql.constants import CLIENT


def main() -> None:
    root = pathlib.Path(__file__).resolve().parents[1]
    props_file = root / "src" / "main" / "resources" / "application-mysql.properties"
    props: dict[str, str] = {}
    for line in props_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()

    jdbc = props["spring.datasource.url"]
    match = re.match(r"^jdbc:mysql://([^:/?#]+)(?::(\d+))?/([^?]+)", jdbc)
    host, port, db = match.group(1), int(match.group(2) or 3306), match.group(3)

    sql = (root / "src" / "main" / "resources" / "db" / "sql" / "ohdp_upgrade_v8_methodology_seed.sql").read_text(encoding="utf-8")

    with pymysql.connect(
        host=host, port=port, db=db,
        user=props["spring.datasource.username"],
        password=props["spring.datasource.password"],
        charset="utf8mb4",
        init_command="SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci",
        client_flag=CLIENT.MULTI_STATEMENTS,
    ) as conn:
        with conn.cursor() as cur:
            cur.execute(sql)
            while cur.nextset():
                pass
        conn.commit()
        print("v8 migration applied: seeded factor templates with Chinese names + methodology")


if __name__ == "__main__":
    main()
