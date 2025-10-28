@echo off
REM Change to the directory where this script is located
cd /d "%~dp0"

echo ============================================
echo      YTDLnis Web - Starting...
echo ============================================
echo.
echo Current directory: %CD%
echo.

REM Check if Node.js is installed
where node >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Node.js is not installed!
    echo.
    echo Please install Node.js from: https://nodejs.org
    echo.
    pause
    exit /b 1
)

REM Verify we have package.json in current directory
if not exist "package.json" (
    echo ERROR: Cannot find package.json in current directory
    echo Current directory: %CD%
    echo.
    echo Please run this script from the web-app folder
    pause
    exit /b 1
)

REM Check if dependencies are installed
if not exist "node_modules\" (
    echo Installing dependencies (this may take a few minutes)...
    echo.
    call npm run install-all
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo ERROR: Failed to install dependencies
        pause
        exit /b 1
    )
)

REM Check if client build exists
if not exist "client\build\" (
    echo Building client...
    echo.
    call npm run build
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo ERROR: Failed to build client
        pause
        exit /b 1
    )
)

REM Start the server
echo.
echo ============================================
echo  Starting YTDLnis Web Server...
echo ============================================
echo.
echo The app will open in your browser at:
echo http://localhost:3000
echo.
echo Press Ctrl+C to stop the server
echo ============================================
echo.

REM Open browser after a delay
start "" cmd /c "timeout /t 3 /nobreak >nul && start http://localhost:3000"

REM Start server
call npm start

pause
