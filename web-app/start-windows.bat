@echo off
echo ============================================
echo      YTDLnis Web - Starting...
echo ============================================
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

REM Check if dependencies are installed
if not exist "node_modules\" (
    echo Installing dependencies...
    echo This may take a few minutes on first run...
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

REM Check if FFmpeg is installed
if not exist "ffmpeg\ffmpeg.exe" (
    echo.
    echo Installing FFmpeg (required for video downloads)...
    echo.
    call install-ffmpeg.bat
    echo.
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
start "" timeout /t 3 /nobreak >nul && start http://localhost:3000

REM Start server
call npm start

pause

