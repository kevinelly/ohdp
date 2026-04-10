# OHDP API

职业危害数据处理平台后端（Spring Boot 3.5 + MySQL）。

## 1. 初始化数据库

```powershell
cd G:\zhy\bhfyzx\ohdp-api
python scripts/init_ohdp_db.py
```

脚本会自动：
- 连接 `application.properties` 中的 MySQL
- 创建数据库 `ohdp`
- 执行 `src/main/resources/db/sql` 下的核心建表与种子脚本

## 2. 启动后端

```powershell
cd G:\zhy\bhfyzx\ohdp-api
.\mvnw spring-boot:run
```

默认端口：`18080`

Swagger 地址：
- `http://127.0.0.1:18080/swagger-ui.html`

## 3. 默认管理员账号

- username: `admin`
- password: `123456`

## 4. MVP 接口

- 登录：`POST /api/auth/login`
- 项目：`/api/ohdp/projects`
- 岗位：`/api/ohdp/projects/{id}/positions`
- 结果：`/api/ohdp/projects/{id}/results`
- 判定：`POST /api/ohdp/projects/{id}/judge`
- 报告生成：`POST /api/ohdp/projects/{id}/report/generate`
- 报告下载：`GET /api/ohdp/reports/{reportId}/download`
