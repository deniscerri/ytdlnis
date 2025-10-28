@echo off
title Building YTD-lins Portable EXE
echo.
echo ╔══════════════════════════════════════════════════════╗
echo ║                                                      ║
echo ║       Building YTD-lins Portable Windows EXE         ║
echo ║                                                      ║
echo ║  This will take 5-15 minutes...                   ║
echo ║  Please wait and DON'T CLOSE THIS WINDOW!           ║
echo ║                                                      ║
echo ╚══════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

echo Step 1: Building client...
echo.
cd client
call npm run build
if errorlevel 1 (
    echo ERROR: Failed to build client!
    pause
    exit /b 1
)
cd ..

echo.
echo Step 2: Building Windows Portable EXE...
echo This takes 10-15 minutes - please wait!
echo.

npx electron-builder --win --config.win.target=portable

echo.
echo ╔══════════════════════════════════════════════════════╗
echo ║                                                      ║
echo ║           ✅ BUILD COMPLETE!                        ║
echo ║                                                      ║
echo ║  EXE Location: dist\YTD-lins Web-Portable-1.0.0.exe ║
echo ║                                                      ║
echo ╚══════════════════════════════════════════════════════╝
echo.
pause

