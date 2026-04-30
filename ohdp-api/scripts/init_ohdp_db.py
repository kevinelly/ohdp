from __future__ import annotations

import pathlib
import re
from dataclasses import dataclass

import pymysql
from pymysql.constants import CLIENT


@dataclass
class DbConfig:
    host: str
    port: int
    database: str
    username: str
    password: str


def parse_properties(path: pathlib.Path) -> dict[str, str]:
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def load_db_config(app_properties: pathlib.Path) -> DbConfig:
    props = parse_properties(app_properties)
    jdbc = props["spring.datasource.url"]
    username = props["spring.datasource.username"]
    password = props["spring.datasource.password"]

    # jdbc:mysql://host:port/db?params
    match = re.match(r"^jdbc:mysql://([^:/?#]+)(?::(\d+))?/([^?]+)", jdbc)
    if not match:
        raise ValueError(f"Unsupported datasource url: {jdbc}")
    host = match.group(1)
    port = int(match.group(2) or 3306)
    db = match.group(3)
    return DbConfig(host=host, port=port, database=db, username=username, password=password)


def execute_sql_file(conn: pymysql.connections.Connection, path: pathlib.Path) -> None:
    sql_text = path.read_text(encoding="utf-8")
    with conn.cursor() as cursor:
        cursor.execute(sql_text)
        while cursor.nextset():
            pass
    conn.commit()
    print(f"executed: {path.name}")


def main() -> None:
    root = pathlib.Path(__file__).resolve().parents[1]
    app_properties = root / "src" / "main" / "resources" / "application.properties"
    config = load_db_config(app_properties)

    connect_kwargs = dict(
        host=config.host,
        port=config.port,
        user=config.username,
        password=config.password,
        charset="utf8mb4",
        init_command="SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci",
        autocommit=False,
        client_flag=CLIENT.MULTI_STATEMENTS,
    )

    with pymysql.connect(**connect_kwargs) as conn:
        with conn.cursor() as cursor:
            cursor.execute(
                f"CREATE DATABASE IF NOT EXISTS `{config.database}` "
                "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"
            )
        conn.commit()
        print(f"database ready: {config.database}")

    sql_dir = root / "src" / "main" / "resources" / "db" / "sql"
    sql_order = [
        "sys_user.sql",
        "sys_user_token.sql",
        "sys_role.sql",
        "sys_permission.sql",
        "sys_user_role.sql",
        "sys_role_permission.sql",
        "sys_role_data_scope.sql",
        "ohdp_core.sql",
        "sys_rbac_seed.sql",
    ]

    with pymysql.connect(db=config.database, **connect_kwargs) as conn:
        for file_name in sql_order:
            execute_sql_file(conn, sql_dir / file_name)
    print("ohdp database initialization finished")


if __name__ == "__main__":
    main()
