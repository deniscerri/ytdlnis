@echo off
REM Hide the window initially
if not "%1"=="min" start /min cmd /c "%~nx0" min & exit

cd /d "%~dp0web-app"

title YTDLnis Web Server

echo Starting YTDLnis Web...
echo.
echo Opening browser in 3 seconds...
echo.

REM Open browser after 3 seconds
start "" "http://localhost:3000"

REM Start the server
node server/index.js

