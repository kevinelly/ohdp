@echo off
chcp 65001 >nul
title BHFYZX 重置管理员密码
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset-admin.ps1"
echo.
pause
