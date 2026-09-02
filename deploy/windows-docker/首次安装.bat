@echo off
chcp 65001 >nul
title BHFYZX 首次安装
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1"
echo.
pause
