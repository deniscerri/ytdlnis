@echo off
echo Starting YTDLnis Web...

cd /d "%~dp0"

start "" "http://localhost:3000"

node server/index.js

