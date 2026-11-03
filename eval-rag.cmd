@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0eval-rag.ps1"
if errorlevel 1 (
  echo.
  echo RAG evaluation failed. See the message above.
)
echo.
pause
