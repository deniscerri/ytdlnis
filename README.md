# YTDLnis - Android App + Web App

<div align="center">
  <img src="android/fastlane/metadata/android/en-US/images/icon.png" width="20%" alt="YTDLnis Icon"/>
  
  <h3>Full-featured video/audio downloader with Android & Web interfaces</h3>
  
  [![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
  [![Original](https://img.shields.io/badge/Original-deniscerri/ytdlnis-green.svg)](https://github.com/deniscerri/ytdlnis)
</div>

---

## 📌 About This Repository

This is a **modified version** of [YTDLnis](https://github.com/deniscerri/ytdlnis) that includes both:
- **Android App** - Original full-featured Android application
- **Web App** - New web-based interface with modern UI

### Credits
- **Original Project**: [YTDLnis](https://github.com/deniscerri/ytdlnis) by [Denis Çerri](https://github.com/deniscerri)
- **Web App Addition**: Modified and extended by [MK Shaon](https://github.com/mkshaonexe)

---

## 📂 Repository Structure

```
ytdlnis/
├── android/          # Android application (original)
│   ├── app/         # Main Android app source code
│   ├── gradle/      # Gradle build system
│   └── README-*.md  # Documentation in multiple languages
│
├── web-app/         # Web application (new addition)
│   ├── client/      # React frontend
│   ├── server/      # Node.js backend
│   └── README.md    # Web app documentation
│
└── README.md        # This file
```

---

## 🚀 Quick Start

### For Android App
```bash
cd android/
# Follow the Android app build instructions
./gradlew assembleDebug
```

### For Web App
```bash
cd web-app/
# Follow the web app setup instructions
npm install
npm start
```

---

## 💡 Features

### 🤖 Android App Features
- Download audio/video from 1000+ websites using yt-dlp
- Playlist processing and batch downloads
- Schedule downloads by date/time
- Custom commands and templates
- SponsorBlock support
- Material You interface
- And much more...

👉 **[See full Android app features](android/README.md)**

### 🌐 Web App Features (New!)
- **Web-based UI** - Access from any device via browser
- **RESTful API** - Programmatic access to download features
- **WebSocket support** - Real-time download updates
- **Modern React interface** - Clean and intuitive design
- **Cross-platform** - Works on Windows, Mac, Linux

👉 **[See web app documentation](web-app/README.md)**

---

## 📖 Documentation

- **Android App**: See [`android/README.md`](android/README.md) and language-specific READMEs
- **Web App**: See [`web-app/README.md`](web-app/README.md)
- **Setup Guide**: See [`web-app/SETUP.md`](web-app/SETUP.md)
- **Usage Guide**: See [`web-app/USAGE.md`](web-app/USAGE.md)

---

## 📄 License

This project is licensed under **GNU GPL v3.0** - see the [LICENSE](LICENSE) file.

### Important Notes:
- Original YTDLnis created by Denis Çerri
- Web app modifications by MK Shaon
- Both components are open source under GPL v3
- You may use, modify, and distribute this software
- You must maintain the same GPL v3 license
- You must credit the original authors

### Trademark Notice
Except for the source code licensed under GPLv3, all other parties are prohibited from using the "YTDLnis" name as a downloader app, and the same is true for its derivatives.

---

## 🙏 Acknowledgments

### Original Project
- **[Denis Çerri](https://github.com/deniscerri)** - Creator of YTDLnis Android app
- **[yt-dlp](https://github.com/yt-dlp/yt-dlp)** - The powerful download engine
- **[youtubedl-android](https://github.com/yausername/youtubedl-android)** - yt-dlp Android port
- All original contributors to YTDLnis

### Web App
- Built with React, Node.js, and Express
- Inspired by the original Android app's feature set

---

## 🔗 Links

- **Original Repository**: [deniscerri/ytdlnis](https://github.com/deniscerri/ytdlnis)
- **This Fork**: [mkshaonexe/ytdlnis](https://github.com/mkshaonexe/ytdlnis)
- **Original Website**: [ytdlnis.org](https://ytdlnis.org)

---

## 🤝 Contributing

Contributions are welcome! Please read:
- Android app: [`android/CONTRIBUTING.MD`](android/CONTRIBUTING.MD)
- Web app: [`web-app/CONTRIBUTING.md`](web-app/CONTRIBUTING.md)

---

## 📞 Support

- For Android app issues: Check the [original repository](https://github.com/deniscerri/ytdlnis)
- For web app issues: Open an issue in this repository
- Discord: [YTDLnis Community](https://discord.gg/WW3KYWxAPm)
- Telegram: [@ytdlnis](https://t.me/ytdlnis)

---

<div align="center">
  <p>Made with ❤️ by the open-source community</p>
  <p>
    <strong>Original</strong>: Denis Çerri | 
    <strong>Web Extension</strong>: MK Shaon
  </p>
</div>
