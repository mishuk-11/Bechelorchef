@echo off
rem One-click stop for Bachelor Chef (Windows 10/11). Logic lives in scripts\stop.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop.ps1"
set EXITCODE=%ERRORLEVEL%
echo.
pause
exit /b %EXITCODE%
