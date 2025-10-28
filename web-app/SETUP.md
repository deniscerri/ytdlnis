# Setup Guide for YTDLnis Web

## Installation Methods

### Method 1: One-Click Launcher (Easiest)

#### For Windows Users:
1. Download the repository
2. Double-click `start-windows.bat`
3. The script will:
   - Check for Node.js
   - Install dependencies
   - Build the app
   - Start the server
   - Open your browser automatically

#### For Mac/Linux Users:
1. Download the repository
2. Open Terminal in the project folder
3. Run:
```bash
chmod +x start-unix.sh
./start-unix.sh
```

### Method 2: Manual Setup

#### Step 1: Install Node.js

**Windows:**
1. Download from https://nodejs.org
2. Run the installer
3. Restart your computer

**Mac:**
```bash
brew install node
```

**Linux (Ubuntu/Debian):**
```bash
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs
```

#### Step 2: Install Dependencies

Open terminal in project folder:
```bash
# Install server dependencies
npm install

# Install client dependencies
cd client
npm install
cd ..
```

#### Step 3: Build the Client

```bash
cd client
npm run build
cd ..
```

#### Step 4: Start the Server

```bash
npm start
```

#### Step 5: Open in Browser

Navigate to: http://localhost:3000

## First Run

On first startup, the app will:
1. Create a database at `~/.ytdlnis-web/ytdlnis.db`
2. Download and install yt-dlp automatically
3. Create default download folder at `~/Downloads/YTDLnis`

## Verify Installation

1. Open http://localhost:3000
2. Paste a YouTube URL
3. Click "Get Info"
4. If video information appears, installation is successful!

## Optional: Install FFmpeg

For advanced features (format conversion, thumbnail embedding):

**Windows:**
1. Download from https://ffmpeg.org/download.html
2. Extract to C:\ffmpeg
3. Add C:\ffmpeg\bin to PATH

**Mac:**
```bash
brew install ffmpeg
```

**Linux:**
```bash
sudo apt install ffmpeg
```

## Troubleshooting

### "Node.js is not installed"
- Download and install from https://nodejs.org
- Restart your terminal/computer after installation

### "npm: command not found"
- Node.js didn't install correctly
- Reinstall Node.js
- Add Node.js to your PATH

### "Port 3000 is already in use"
- Another app is using port 3000
- Change the port:
```bash
PORT=3001 npm start
```

### "yt-dlp installation failed"
- Install manually:
```bash
pip install yt-dlp
# OR
npm install -g yt-dlp
```

### Downloads fail with "403 Forbidden"
- YouTube might be blocking requests
- Try updating yt-dlp:
```bash
yt-dlp -U
```

### "Cannot find module"
- Dependencies not installed properly
- Delete node_modules and reinstall:
```bash
rm -rf node_modules client/node_modules
npm run install-all
```

## Development Mode

For developers who want to modify the app:

```bash
# Terminal 1: Start server with auto-reload
npm run server

# Terminal 2: Start client with hot-reload
npm run client
```

Client will run on http://localhost:3001 and proxy API calls to server.

## Building for Distribution

### Create Standalone Executables

```bash
npm run build
npm run package
```

This creates executables in the `dist/` folder:
- Windows: `YTDLnis-Web-Setup.exe`
- Mac: `YTDLnis-Web.dmg`
- Linux: `YTDLnis-Web.AppImage`

## Updating

To update to the latest version:

```bash
git pull
npm run install-all
npm run build
```

## Uninstalling

1. Delete the project folder
2. Delete `~/.ytdlnis-web` folder (contains database and yt-dlp)
3. (Optional) Delete `~/Downloads/YTDLnis` folder

## System Requirements

**Minimum:**
- 2GB RAM
- 500MB free disk space
- Internet connection

**Recommended:**
- 4GB RAM
- 2GB free disk space (for downloads)
- Stable internet connection

## Support

If you encounter issues:
1. Check the Troubleshooting section
2. Search existing GitHub issues
3. Create a new issue with:
   - Your OS and version
   - Node.js version (`node --version`)
   - Error messages
   - Steps to reproduce

---

Happy downloading! 🎉

