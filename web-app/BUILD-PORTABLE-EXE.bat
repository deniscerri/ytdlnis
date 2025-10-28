@echo off
title Building YTD-lins Portable EXE
echo.
echo ╔══════════════════════════════════════════════════════╗
echo ║                                                      ║
echo ║    Building Portable Windows EXE...                 ║
echo ║                                                      ║
echo ║  This takes 10-15 minutes. Please wait!             ║
echo ║                                                      ║
echo ╚══════════════════════════════════════════════════════╝
echo.

REM Build React app first
echo Step 1: Building React app...
cd client
call npm run build
if errorlevel 1 (
    echo ERROR: Failed to build!
    pause
    exit /b 1
)
cd ..

echo.
echo Step 2: Building portable EXE...
echo This downloads Electron and packages everything...
echo.

REM Build portable EXE
npx electron-builder --win

echo.
echo ╔══════════════════════════════════════════════════════╗
echo ║                                                      ║
echo ║         ✅ BUILD COMPLETE!                          ║
echo ║                                                      ║
echo ║  EXE Location: dist\YTD-lins Web-Portable-1.0.0.exe ║
echo ║                                                      ║
echo ╚══════════════════════════════════════════════════════╝
echo.
pause

