# YTDLnis Web - Usage Guide

## Getting Started

### 1. Starting the App

**Windows:**
```
Double-click start-windows.bat
```

**Mac/Linux:**
```bash
./start-unix.sh
```

The app will automatically open in your default browser at http://localhost:3000

### 2. Basic Download

1. **Copy a video URL** from any supported site (YouTube, Twitter, etc.)
2. **Paste it** into the URL field on the Home page
3. **Click "Get Info"** to fetch video details
4. **Choose download type:**
   - **Audio** - Extract audio only (MP3, M4A, etc.)
   - **Video** - Download full video (MP4, MKV, etc.)
5. **Click "Download"**

That's it! The download will start automatically.

## Features

### Home Page

**URL Input**
- Paste any video URL
- Support for playlists
- Multiple URLs (one per line)

**Get Info Button**
- Fetches video metadata
- Shows thumbnail, title, duration
- Displays available formats

**Download Type**
- Toggle between Audio and Video
- Settings apply based on type

### Downloads Page

**Active Downloads**
- Real-time progress bars
- Download speed and ETA
- Cancel button for each download

**Queue Management**
- See all queued downloads
- Reorder downloads
- Pause/Resume functionality

### History Page

**Download Records**
- All completed downloads
- Failed downloads with error messages
- Download date and time

**Actions**
- Open file location
- Delete history entry
- Re-download

### Settings Page

**Appearance**
- Dark/Light mode toggle

**Download Path**
- Set default save location
- Browse for folder

**Audio Settings**
- Format: MP3, M4A, OPUS, FLAC, WAV
- Quality: 128-320 kbps
- Embed thumbnail
- Embed metadata

**Video Settings**
- Format: MP4, MKV, WebM
- Quality: 480p - 4K
- Embed subtitles
- Embed thumbnail
- Remove audio (video only)

## Advanced Usage

### Downloading Playlists

1. Paste playlist URL
2. App will detect all videos
3. Choose to download:
   - All videos
   - Specific range
   - Selected videos

### Custom Quality

For specific quality:
1. Click "Advanced" after getting info
2. Select exact format from list
3. Choose video + audio combination

### Batch Downloads

Multiple URLs at once:
1. Paste multiple URLs (one per line)
2. Click "Get Info"
3. Select download type
4. All will be queued

### Scheduled Downloads

Coming soon - ability to schedule downloads for later.

## Tips & Tricks

### Best Quality Downloads

**Video:**
- Format: MP4
- Quality: 1080p or 4K
- Keep "Embed Thumbnail" on

**Audio:**
- Format: FLAC (lossless) or MP3 (smaller)
- Quality: 320 kbps
- Keep "Embed Metadata" on

### Faster Downloads

1. Close other downloads
2. Use wired connection
3. Lower quality if needed

### Save Storage

1. Use lower quality settings
2. Audio-only for music
3. Delete history entries

## Keyboard Shortcuts

- `Enter` in URL field → Get Info
- `Ctrl/Cmd + V` → Paste URL
- `Ctrl/Cmd + ,` → Settings (coming soon)

## Supported Sites

**Popular:**
- YouTube, YouTube Music
- Twitter/X
- Instagram, Instagram Stories
- TikTok
- Facebook
- Reddit
- SoundCloud
- Twitch

**Full List:**
See https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md

## Troubleshooting

### "Failed to fetch video info"

**Solutions:**
1. Check internet connection
2. Verify URL is correct
3. Try updating yt-dlp in Settings
4. Some videos may be region-locked

### "Download failed"

**Common causes:**
- Poor internet connection
- Video was deleted
- Private/restricted video
- Format not available

**Solutions:**
1. Try different quality
2. Check if video is accessible in browser
3. Update yt-dlp
4. Try audio-only

### "Slow download speed"

**Solutions:**
1. Close other downloads
2. Use ethernet instead of WiFi
3. Lower quality setting
4. Check your internet speed

### "File not found after download"

**Solutions:**
1. Check download path in Settings
2. Verify disk space available
3. Check antivirus didn't quarantine file
4. Look in History page for actual location

## FAQ

**Q: Can I download age-restricted videos?**
A: Yes, but you may need to use cookies from your browser (coming soon).

**Q: How do I download a specific part of a video?**
A: Use the "Cut Video" feature (coming soon).

**Q: Can I download subtitles?**
A: Yes, enable "Embed Subtitles" in video settings.

**Q: Does this work offline?**
A: No, you need internet to download videos.

**Q: Is this legal?**
A: Depends on your country and the content. Only download content you have rights to.

**Q: How do I update yt-dlp?**
A: Go to Settings → Check for Updates, or run `yt-dlp -U` in terminal.

**Q: Can I run this on multiple computers?**
A: Yes! Just install on each computer. Each runs its own local server.

**Q: Where are my downloads stored?**
A: Default: `~/Downloads/YTDLnis` (can be changed in Settings)

**Q: Can I close the browser while downloading?**
A: No, keep the browser or app open. The server must run.

**Q: How much disk space do I need?**
A: Depends on downloads. 1080p video ≈ 500MB-2GB per hour.

## Getting Help

1. Check this guide
2. Check SETUP.md for installation issues
3. Search GitHub issues
4. Create a new issue with details

---

Happy downloading! 🎉

