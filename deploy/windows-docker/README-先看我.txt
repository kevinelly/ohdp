BHFYZX Windows Docker 完全离线部署包 V1.0.6
===============================================

目标：首次安装、日常启动和单机运行均不需要互联网。

前提：目标 Windows 电脑已经安装好 WSL 2 和 Docker Desktop，Docker Desktop
已启动并使用 Linux containers。Docker Desktop 无需登录账号。

本包已经包含：
1. MySQL 8.4 镜像
2. BHFYZX API 镜像（已包含 Java 程序）
3. BHFYZX Web 镜像（已包含 Nginx 和编译后的网页）

首次安装：
1. 将完整压缩包复制到目标电脑。
2. 完整解压到 R:\BHFYZX-Windows-Docker-Offline-V1.0.6。
3. 启动 Docker Desktop，等待 Engine running。
4. 双击根目录的“01-首次安装.bat”。
5. 等待镜像校验、导入和三个容器启动；不要关闭窗口。
6. 浏览器访问 http://localhost:8088。

已有 V1.0.5 数据升级：
1. 保留原来的 R:\BHFYZX-Windows-Docker-Offline-V1.0.5 目录，不要删除。
2. 完整解压 V1.0.6 到 R:\BHFYZX-Windows-Docker-Offline-V1.0.6。
3. 双击 deploy\windows-docker\升级系统.bat。
4. 脚本会复制原 .env、导入新镜像并保留原数据库、报告和附件数据卷。

安装脚本不会执行 docker pull，也不会访问 Docker Hub、npm、Maven 或云端数据库。
Web、API、MySQL、数据库文件、报告和附件全部在本机运行及保存。

日常使用：
- 启动：双击“02-启动系统.bat”
- 停止：双击“03-停止系统.bat”
- 备份：双击“04-备份数据.bat”
- 管理员密码无法使用：双击 deploy\windows-docker\重置管理员密码.bat

注意：
- 不要直接在 ZIP 压缩包内运行 BAT。
- 不要删除 deploy\windows-docker\images 下的镜像包，至少在确认安装及备份正常前保留。
- 不要执行 docker compose down -v 或 docker volume prune，这会删除本机业务数据。
- 只在本机使用时，不要运行“开放局域网访问.bat”。

更详细的步骤见 deploy\windows-docker\部署说明.md。
