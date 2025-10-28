@echo off
cd /d "%~dp0"

echo Starting YTDLnis Web...
echo.

REM Just start the server directly
node server/index.js

pause

