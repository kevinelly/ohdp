@echo off
chcp 65001 >nul
cd /d "%~dp0deploy\windows-docker"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy\windows-docker\upgrade.ps1"
set "RESULT=%ERRORLEVEL%"
echo.
pause
exit /b %RESULT%
