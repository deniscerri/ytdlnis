@echo off
echo Building portable Windows EXE...
echo.
echo Step 1: Building React client...
cd client && npm run build && cd ..

echo.
echo Step 2: Building portable EXE...
echo This will take 10-15 minutes...
echo.
electron-builder --win --config.win.target=portable

echo.
echo Done! Check the dist folder for your EXE.
pause

