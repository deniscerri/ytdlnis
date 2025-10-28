@echo off
echo ============================================
echo    Starting YTDLnis Web Application
echo ============================================
echo.

REM Change to the web-app directory
set "WEB_APP_DIR=%~dp0web-app"

REM Check if web-app directory exists
if not exist "%WEB_APP_DIR%\start-windows.bat" (
    echo ERROR: Cannot find web-app directory
    echo Expected location: %WEB_APP_DIR%
    echo.
    pause
    exit /b 1
)

REM Navigate to web-app and run the start script
cd /d "%WEB_APP_DIR%"
call start-windows.bat
