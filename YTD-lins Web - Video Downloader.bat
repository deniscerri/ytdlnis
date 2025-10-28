@echo off
title YTD-lins Web - Video Downloader
cd /d "%~dp0web-app"

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                                                              ║
echo ║                    YTD-lins Web                              ║
echo ║                  Video Downloader                           ║
echo ║                                                              ║
echo ║  Opening download interface in your browser...              ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

REM Open browser
start "" "http://localhost:3000"

REM Start server
node server/index.js

