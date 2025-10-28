@echo off
cls
echo ============================================
echo       YTDLnis Web - Quick Starter
echo ============================================
echo.

cd /d "%~dp0web-app"

if not exist package.json (
    echo ERROR: Cannot find web-app folder!
    echo.
    pause
    exit /b 1
)

echo Current folder: %CD%
echo.

REM Check for node
where node >nul 2>nul
if errorlevel 1 (
    echo ERROR: Node.js not found!
    echo Please install from: https://nodejs.org
    echo.
    pause
    exit /b 1
)

echo Starting server...
echo.
echo App will open at: http://localhost:3000
echo Press Ctrl+C to stop the server
echo.
echo ============================================
echo.

REM Open browser after 3 seconds
start /b cmd /c "ping 127.0.0.1 -n 4 >nul & start http://localhost:3000"

REM Start the server
node server/index.js

pause

