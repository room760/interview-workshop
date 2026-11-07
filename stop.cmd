@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop.ps1" %*
set "result=%ERRORLEVEL%"
echo.
if not "%result%"=="0" echo Shutdown failed. See the message above.
echo Press any key to close this window...
pause >nul
exit /b %result%
