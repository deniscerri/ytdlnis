@echo off
cd /d "%~dp0web-app"

echo.
echo ╔══════════════════════════════════════════════════════╗
echo ║                                                      ║
echo ║        Creating YTD-lins Web Windows EXE             ║
echo ║                                                      ║
echo ╚══════════════════════════════════════════════════════╝
echo.
echo This will create a production Windows executable.
echo The build process takes about 5-10 minutes.
echo.
pause

REM Check if node_modules exist
if not exist "node_modules\" (
    echo Installing dependencies...
    call npm install
)

REM Build the React client
echo.
echo Building React application...
echo.
cd client
call npm run build
cd ..

REM Build the Electron EXE
echo.
echo Building Windows executable...
echo This will take 5-10 minutes...
echo.
call npm run build-exe

echo.
echo ╔══════════════════════════════════════════════════════╗
echo ║                                                      ║
echo ║              BUILD COMPLETE!                         ║
echo ║                                                      ║
echo ║  Your EXE files are in: web-app\dist\               ║
echo ║                                                      ║
echo ║  • YTD-lins Web-Setup.exe (Installer)               ║
echo ║  • YTD-lins Web-Portable.exe (No install)            ║
echo ║                                                      ║
echo ╚══════════════════════════════════════════════════════╝
echo.
pause

