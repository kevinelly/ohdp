# Windows 单机安装包

此目录生成 Windows 10/11 x64 的 EXE 安装包。安装版将现有 Vue 构建文件、Spring Boot JAR、Java 17 运行时和报告模板一起交付，以 H2 文件数据库供当前 Windows 用户在本机使用。无需 Docker、WSL、Node.js 或独立数据库服务。

默认输出：`output/installers/BHFYZX-Setup-1.0.12-x64.exe`。

## 构建

构建机需要 Java 17 JDK、Node.js（已安装前端依赖）、Windows .NET Framework C# 编译器、Inno Setup 6。目标电脑只需 Windows 自带的 .NET Framework 4.x 和浏览器。

先准备 `output/windows-desktop-sample/online-sample.zip` 和同名 `.summary.json`。`export-online-sample.py --key <SSH 私钥路径> --output output/windows-desktop-sample/online-sample.zip` 使用线上 MySQL 一致性只读事务获取职业卫生与放射检测业务表，并读取对应附件。它不修改生产数据，不导出账号、权限、令牌或服务器凭据。服务器已缺失的附件记录跳过，不伪造文件。

```powershell
.\deploy\windows-desktop\build.ps1 `
    -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot' `
    -InnoCompiler 'C:\Program Files (x86)\Inno Setup 6\ISCC.exe'
```

默认先构建前端并执行后端测试，再生成安装包。已确认构建完成时可使用 `-SkipApplicationBuild`；可通过 `-RuntimeImage` 复用 `jlink` 生成的运行时。每次使用独立暂存目录，不删除已有工作文件。构建输出另附 SHA-256 校验文件和使用说明。

## 运行设计

- 程序默认安装到 `%LOCALAPPDATA%\Programs\BHFYZX`，安装时可选择其他路径；运行数据独立保存到 `%LOCALAPPDATA%\BHFYZX\Data`。
- 通过安装目录的 `application-desktop.properties` 启用 `desktop` 配置，不改动原有服务器、本地开发或 Docker 配置。
- 仅监听 `127.0.0.1`，默认端口 18788；冲突时选择其他可用端口。
- 管理员默认密码为 `Test@345`，数据库访问密码仍随机生成。`desktop` 配置首次升级会将现有 admin 密码迁移到默认密码一次，后续不会覆盖用户自行修改的密码。服务器配置不受影响。
- 启动器用 Windows 互斥量防止重复启动，等待 Spring 完成初始化后打开默认浏览器。
- 停止、备份、覆盖安装及卸载通过随机令牌保护的本地关闭接口等待数据库正常关闭。
- 报告生成使用内置模板和 Java 库；单机版关闭 Office COM 后台刷新，文档设置为打开时更新域，最终目录和页码在 Word/WPS 中核对。
- 安装包携带线上业务快照和可读取附件，安装时默认勾选导入。安装选项记录为一次性请求，删除测试数据后重启不会自动再次导入。
- 启动窗口提供导入/删除入口。导入使用事务、重新分配 ID、修复引用、独立记录导入行与附件所有权，重复导入幂等。删除前检查后来业务的引用；不全库清空。共享的现有基础资料不覆盖、不标为测试记录。
- 测试数据操作期间拦截业务 API，避免检查引用和删除之间发生并发写入；端点仅在 desktop 配置启用，并校验启动器随机令牌。
- 卸载仅删除安装文件，保留独立数据目录。原有 Docker/MySQL 数据不会自动迁入此单机版。
- 两份 Word 操作手册随前端静态资源打包；网页左侧、帮助中心与启动窗口均提供下载入口，离线可保存。更新手册时同步替换 `ohdp-web/public/manuals` 下的文件，构建会校验成品与源文件一致。

## 验收

`smoke-test.ps1` 需要 PowerShell 7，必须指向专用测试安装目录和全新的测试数据目录。

```powershell
.\deploy\windows-desktop\smoke-test.ps1 `
    -InstallDir 'S:\test\安装目录' `
    -DataDir 'S:\test\空数据目录' `
    -Installer 'S:\codex\bhfyzx\output\installers\BHFYZX-Setup-1.0.12-x64.exe'
```

`smoke-test.ps1` 应用于不导入测试数据的测试安装（安装参数 `/TASKS=""`），创建测试业务记录，验证账号、前端、报告生成、重复启动、端口冲突、数据库持久化和备份恢复。`test-sample-backend.ps1` 在独立临时数据库中验证完整线上快照导入、附件下载、删除保留独立业务、重新导入及用户改密后的重启行为。`BHFYZX_DATA_DIR` 只用于测试隔离或明确指定的迁移路径，正常安装无需设置。不要用测试安装覆盖正在使用的正式安装。

安装程序由 [Inno Setup](https://jrsoftware.org/isdl.php) 构建。中文安装文本来自 [Inno Setup 简体中文翻译](https://github.com/kira-96/Inno-Setup-Chinese-Simplified-Translation)，保留了语言文件中的作者说明。Java 运行时携带其 `legal` 目录和 `NOTICE`。当前 EXE 未使用代码签名证书签名。
