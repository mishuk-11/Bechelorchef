@echo off
rem One-click install for Bachelor Chef (Windows 10/11). Logic lives in scripts\install.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\install.ps1"
set EXITCODE=%ERRORLEVEL%
echo.
pause
exit /b %EXITCODE%
