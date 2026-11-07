@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-benchmark.ps1" -Suite recovery %*
set "result=%ERRORLEVEL%"
echo.
if not "%result%"=="0" echo Some tests failed. See the saved result directory above.
echo Press any key to close this window...
pause >nul
exit /b %result%
