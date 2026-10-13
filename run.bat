@echo off
rem One-click run for Bachelor Chef (Windows 10/11). Logic lives in scripts\run.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run.ps1"
set EXITCODE=%ERRORLEVEL%
echo.
pause
exit /b %EXITCODE%
