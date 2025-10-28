@echo off
cd /d "%~dp0web-app"
start "" "http://localhost:3000"
node server/index.js

