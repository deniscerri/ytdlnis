@echo off
title Building YTD-lins Web EXE
echo.
echo ╔════════════════════════════════════════════════════╗
echo ║                                                     ║
echo ║         Building YTD-lins Web Executable            ║
echo ║                                                     ║
echo ╚════════════════════════════════════════════════════╝
echo.

echo Step 1: Building React client...
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
echo Step 2: Building Windows EXE...
echo This may take 5-10 minutes...
echo.

npm run build-exe

echo.
echo ==========================================
echo Build Complete!
echo Check the 'dist' folder for the EXE file
echo ==========================================
echo.
pause

