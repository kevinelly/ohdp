@echo off
chcp 65001 >nul
cd /d "%~dp0"
powershell.exe -NoProfile -Command "Start-Process powershell.exe -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"\"%~dp0enable-lan.ps1\"\"'"
pause
