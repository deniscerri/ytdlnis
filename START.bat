@echo off
REM Ultra simple launcher - just clicks and opens browser!

cd /d "%~dp0web-app"

echo.
echo ====================================
echo   YTDLnis Web is starting...
echo ====================================
echo.
echo Opening your browser in 3 seconds...
echo.

REM Open browser automatically
start "" "http://localhost:3000"

REM Start the server
node server/index.js

