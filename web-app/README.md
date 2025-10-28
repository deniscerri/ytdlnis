# YTDLnis Web

A local web application for downloading videos and audio from 1000+ websites using yt-dlp. Runs entirely on your computer with no external servers required.

## 🌟 Features

- **Download from 1000+ sites** - YouTube, Twitter, Instagram, TikTok, and more
- **Audio & Video downloads** - Extract audio or download full videos
- **Real-time progress** - Live updates via WebSocket
- **Download history** - Track all your downloads
- **Custom settings** - Configure quality, format, and output path
- **Beautiful Material UI** - Modern, responsive interface
- **Local execution** - No external servers, your data stays on your machine
- **Free & Open Source** - GPL-3.0 License

## 📋 Requirements

- Node.js 16+ (https://nodejs.org)
- yt-dlp (automatically installed on first run)
- FFmpeg (optional, for advanced processing)

## 🚀 Quick Start

### Option 1: One-Click Launcher (Recommended)

#### Windows
```bash
double-click start-windows.bat
```

#### Mac/Linux
```bash
chmod +x start-unix.sh
./start-unix.sh
```

### Option 2: Manual Installation

1. **Install dependencies:**
```bash
npm run install-all
```

2. **Start the server:**
```bash
npm start
```

3. **Open in browser:**
```
http://localhost:3000
```

## 🎯 How It Works

1. **Backend Server** - Node.js/Express server runs locally on your PC
2. **yt-dlp Integration** - Downloads and processes media files
3. **SQLite Database** - Stores download history and settings
4. **WebSocket** - Real-time progress updates
5. **React Frontend** - Beautiful web interface

## 📂 Project Structure

```
ytdlnis-web/
├── server/              # Backend server
│   ├── index.js        # Main server file
│   ├── database.js     # SQLite database
│   └── ytdlp-manager.js # yt-dlp integration
├── client/             # React frontend
│   ├── src/
│   │   ├── pages/      # Main pages
│   │   ├── components/ # Reusable components
│   │   └── context/    # WebSocket context
│   └── public/
├── installer/          # One-click installers
└── package.json        # Dependencies
```

## ⚙️ Configuration

Settings are stored in `~/.ytdlnis-web/ytdlnis.db`

Default settings:
- **Download Path:** `~/Downloads/YTDLnis`
- **Audio Format:** MP3 (192kbps)
- **Video Format:** MP4 (1080p)
- **Embed Thumbnail:** Yes
- **Embed Metadata:** Yes

## 🔧 Advanced Usage

### Custom Port

```bash
PORT=8080 npm start
```

### Development Mode

```bash
npm run dev
```

This runs the server and client in development mode with hot-reloading.

### Build for Production

```bash
npm run build
npm run package
```

Creates standalone executables for Windows, Mac, and Linux.

## 📱 Supported Sites

- YouTube
- Twitter/X
- Instagram
- TikTok
- Facebook
- Reddit
- SoundCloud
- Twitch
- And 1000+ more!

See full list: https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md

## 🐛 Troubleshooting

### yt-dlp not found
The app automatically installs yt-dlp on first run. If it fails:
```bash
# Install manually
pip install yt-dlp
# Or
npm install -g yt-dlp
```

### Port already in use
Change the port:
```bash
PORT=3001 npm start
```

### Downloads fail
1. Check your internet connection
2. Update yt-dlp: `yt-dlp -U`
3. Try a different format/quality

## 🔒 Privacy & Security

- **All processing happens locally** - No data sent to external servers
- **No tracking or analytics** - Your downloads are private
- **Open source** - Inspect the code yourself

## 🤝 Contributing

Contributions welcome! This is based on the Android app YTDLnis.

Original project: https://github.com/deniscerri/ytdlnis

## 📄 License

GPL-3.0 License - see LICENSE file

## 🙏 Credits

- **YTDLnis** - Original Android app by Denis Çerri
- **yt-dlp** - The amazing download tool
- **Material-UI** - React component library

## 📞 Support

- GitHub Issues: Report bugs and request features
- Based on YTDLnis Discord: https://discord.gg/WW3KYWxAPm

---

Made with ❤️ for the YTDLnis community

