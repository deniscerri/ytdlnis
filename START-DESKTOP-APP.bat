@echo off
cd /d "%~dp0web-app"
title YTDLnis Web
echo Starting YTDLnis Desktop App...
start "" "http://localhost:3000"
node server/index.js

