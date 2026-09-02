@echo off
chcp 65001 >nul
title OHDP 系统启动

echo 正在启动后端服务...
start "OHDP后端" cmd /k "cd /d G:\zhy\bhfyzx\ohdp-api && mvn spring-boot:run"

echo 等待后端启动（约15秒）...
timeout /t 15 /nobreak >nul

echo 正在启动前端服务...
start "OHDP前端" cmd /k "cd /d G:\zhy\bhfyzx\ohdp-web && npm run dev"

echo.
echo ============================================
echo  后端地址: http://localhost:18080
echo  前端地址: http://localhost:5173
echo  登录账号: admin / 123456
echo ============================================
echo.
pause
