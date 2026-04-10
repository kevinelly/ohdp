# OHDP Web

职业危害数据处理平台前端（Vue3）。

## 1. 安装依赖

```powershell
cd G:\zhy\bhfyzx\ohdp-web
npm install
```

## 2. 启动前端

```powershell
npm run dev
```

默认地址：`http://127.0.0.1:5173`

已配置 Vite 代理：
- `/api` -> `http://127.0.0.1:18080`

## 3. 功能覆盖（MVP）

- 登录
- 项目新建/查询/更新/删除
- 岗位信息录入与删除
- 检测结果录入与删除
- 自动判定
- 报告生成与下载
