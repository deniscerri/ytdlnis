@echo off
title YTD-lins Web
cd /d "%~dp0web-app"

echo.
echo ██╗   ██╗████████╗██████╗ ██╗     ██╗███╗   ██╗██╗███████╗
echo ╚██╗ ██╔╝╚══██╔══╝██╔══██╗██║     ██║████╗  ██║██║██╔════╝
echo  ╚████╔╝    ██║   ██║  ██║██║     ██║██╔██╗ ██║██║███████╗
echo   ╚██╔╝     ██║   ██║  ██║██║     ██║██║╚██╗██║██║╚════██║
echo    ██║      ██║   ██████╔╝███████╗██║██║ ╚████║██║███████║
echo    ╚═╝      ╚═╝   ╚═════╝ ╚══════╝╚═╝╚═╝  ╚═══╝╚═╝╚══════╝
echo.
echo                    Web Video Downloader
echo.
echo Opening download interface...
echo.

start "" "http://localhost:3000"
node server/index.js
