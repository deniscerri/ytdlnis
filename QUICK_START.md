# Quick Start Guide - YTDLnis Web

## 🚀 How to Start the Web App

### **Method 1: Double-Click the Root Launcher (EASIEST)**
1. Go to the project root folder
2. Double-click **`start-web-app.bat`**
3. Wait for the app to open in your browser

### **Method 2: Use the Direct Launcher**
1. Navigate to the `web-app` folder
2. Double-click **`start-windows.bat`**
3. Wait for the app to open in your browser

## 📝 What Was Fixed

The original batch file was trying to run from the wrong directory. I've fixed it so that:
- ✅ It automatically changes to the correct directory
- ✅ It verifies the current directory before running
- ✅ It shows helpful error messages if something goes wrong
- ✅ It works whether you run it from the root or the web-app folder

## ⚠️ Important Notes

1. **Node.js Required**: Make sure Node.js is installed from https://nodejs.org
2. **First Run**: The first time you start it will take longer (downloads dependencies)
3. **Keep Terminal Open**: Don't close the terminal window - the server needs to keep running
4. **Browser**: The app opens automatically at http://localhost:3000

## 🎯 Using the Web App

Once the app opens in your browser:

1. **Paste a URL** - Any video URL from YouTube, Twitter, etc.
2. **Click "Get Info"** - Fetch video details
3. **Choose Download Type** - Audio or Video
4. **Click Download** - Start the download!

## 📂 Where Are Videos Saved?

Default location: `C:\Users\YourName\Downloads\YTDLnis`

You can change this in Settings (gear icon).

## 🛑 To Stop the Server

Press `Ctrl+C` in the terminal window.

## ❓ Troubleshooting

### "Node.js is not installed"
- Download and install from: https://nodejs.org
- Restart your computer after installation

### "Cannot find package.json"
- Make sure you're in the correct folder
- Try using the `start-web-app.bat` from the project root

### "Port 3000 is already in use"
- Another app is using port 3000
- Close that app first, or wait for it to finish

### Downloads are slow
- This is normal for large videos
- Check your internet connection
- Lower the quality in Settings if needed

---

**Happy Downloading! 🎉**

