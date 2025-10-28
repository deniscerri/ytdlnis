@echo off
echo Installing FFmpeg for YTDLnis Web...
echo.

REM Create ffmpeg directory
if not exist "%~dp0ffmpeg" mkdir "%~dp0ffmpeg"
cd "%~dp0ffmpeg"

REM Download FFmpeg using PowerShell
echo Downloading FFmpeg (this may take a minute)...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip' -OutFile 'ffmpeg.zip'}"

if errorlevel 1 (
    echo Failed to download FFmpeg
    pause
    exit /b 1
)

REM Extract FFmpeg
echo Extracting FFmpeg...
powershell -Command "Expand-Archive -Path 'ffmpeg.zip' -DestinationPath '.' -Force"

REM Find and move ffmpeg files
for /d %%i in (ffmpeg-*) do (
    move "%%i\bin\ffmpeg.exe" .
    move "%%i\bin\ffprobe.exe" .
    rmdir /s /q "%%i"
)

REM Cleanup
del ffmpeg.zip

echo.
echo ✓ FFmpeg installed successfully!
echo Location: %~dp0ffmpeg\ffmpeg.exe
echo.
pause

