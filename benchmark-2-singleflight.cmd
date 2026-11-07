@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-benchmark.ps1" -Suite singleflight
set "result=%ERRORLEVEL%"
echo.
if not "%result%"=="0" echo Benchmark failed. See the message and saved log above.
pause
exit /b %result%
