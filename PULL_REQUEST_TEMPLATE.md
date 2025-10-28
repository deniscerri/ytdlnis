# Add Web Application Interface to YTDLnis

## 📋 Summary
This Pull Request adds a **web-based interface** to YTDLnis, allowing users to access download functionality through a browser on any platform (Windows, Mac, Linux) in addition to the existing Android app.

## 🎯 Motivation
- **Cross-platform access**: Users can now use YTDLnis on desktop/laptop computers
- **Modern web UI**: Clean, responsive React-based interface
- **Real-time updates**: WebSocket support for live download progress
- **API access**: RESTful API for programmatic downloads

## ✨ What's New

### New Features Added:
1. **Web Application** (`web-app/` directory)
   - React frontend with modern UI
   - Node.js/Express backend
   - WebSocket support for real-time updates
   - SQLite database integration
   - yt-dlp integration for downloads

2. **Reorganized Structure**
   - `android/` - Original Android app (unchanged)
   - `web-app/` - New web application

3. **Documentation**
   - Comprehensive README files
   - Setup and usage guides
   - Clear attribution to original project

## 🔧 Technical Details

### Technologies Used:
- **Frontend**: React, WebSocket
- **Backend**: Node.js, Express, Socket.io
- **Database**: SQLite (better-sqlite3)
- **Downloads**: yt-dlp integration

### File Structure:
```
web-app/
├── client/          # React frontend
├── server/          # Node.js backend
├── README.md        # Documentation
├── SETUP.md         # Setup instructions
└── USAGE.md         # Usage guide
```

## 🚀 How to Test

### Prerequisites:
- Node.js 16+
- npm or yarn

### Installation:
```bash
cd web-app
npm install
npm start
```

Access at: http://localhost:3000

## 📸 Screenshots
(Web interface provides similar functionality to Android app but through browser)

## ✅ Checklist
- [x] Code follows GPL v3.0 license
- [x] Original authors properly credited
- [x] Documentation added
- [x] No breaking changes to Android app
- [x] New features are self-contained in `web-app/` directory

## 🙏 Credits & Attribution
- **Original Project**: YTDLnis by Denis Çerri
- **Web App Addition**: MK Shaon (@mkshaonexe)
- Maintains full GPL v3.0 compliance
- All original Android app functionality preserved

## 💡 Benefits to Project
1. **Expands user base** - Desktop/laptop users can now use YTDLnis
2. **No impact on Android app** - All changes are additive
3. **Open source** - Follows same GPL v3 license
4. **Well documented** - Easy for others to understand and contribute
5. **Modern stack** - Uses popular web technologies

## 📝 Notes
- Android app remains completely unchanged and functional
- Web app is optional - users can use either or both
- Both apps share the same core yt-dlp functionality
- License compliance maintained throughout

## 🔗 Related
- Original repo: https://github.com/deniscerri/ytdlnis
- My fork: https://github.com/mkshaonexe/ytdlnis

---

Thank you for considering this contribution! I'm happy to make any changes or improvements based on your feedback.

**MK Shaon**  
Inspired by the amazing YTDLnis Android app

