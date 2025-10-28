const express = require('express');
const cors = require('cors');
const WebSocket = require('ws');
const path = require('path');
const fs = require('fs-extra');
const http = require('http');
const { spawn } = require('child_process');
const Database = require('./database');
const YtdlpManager = require('./ytdlp-manager');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, '../client/build')));

// Database setup
const db = new Database();

// yt-dlp manager
const ytdlpManager = new YtdlpManager();

// Create HTTP server
const server = http.createServer(app);

// WebSocket setup for real-time updates
const wss = new WebSocket.Server({ server });

// Store active downloads
const activeDownloads = new Map();

// WebSocket connection
wss.on('connection', (ws) => {
    console.log('Client connected');
    
    ws.on('message', (message) => {
        console.log('Received:', message);
    });
    
    ws.on('close', () => {
        console.log('Client disconnected');
    });
});

// Broadcast to all connected clients
function broadcast(data) {
    wss.clients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify(data));
        }
    });
}

// API Routes

// Health check
app.get('/api/health', (req, res) => {
    res.json({ status: 'ok', version: '1.0.0' });
});

// Get video info
app.post('/api/info', async (req, res) => {
    try {
        const { url } = req.body;
        const info = await ytdlpManager.getInfo(url);
        res.json(info);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Get available formats
app.post('/api/formats', async (req, res) => {
    try {
        const { url } = req.body;
        const formats = await ytdlpManager.getFormats(url);
        res.json(formats);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Start download
app.post('/api/download', async (req, res) => {
    try {
        const { url, type, format, options } = req.body;
        
        const downloadId = Date.now().toString();
        
        // Save to database
        await db.addDownload({
            id: downloadId,
            url,
            type,
            format,
            status: 'queued',
            progress: 0,
            createdAt: new Date()
        });
        
        // Start download process
        const downloadProcess = ytdlpManager.download(url, type, format, options, (progress) => {
            // Update progress
            db.updateDownloadProgress(downloadId, progress.percent, progress.speed);
            
            // Broadcast progress to all clients
            broadcast({
                type: 'progress',
                downloadId,
                progress: progress.percent,
                speed: progress.speed,
                eta: progress.eta
            });
        });
        
        activeDownloads.set(downloadId, downloadProcess);
        
        downloadProcess.on('complete', async (file) => {
            await db.updateDownloadStatus(downloadId, 'completed', file.path);
            broadcast({
                type: 'complete',
                downloadId,
                filePath: file.path
            });
            activeDownloads.delete(downloadId);
        });
        
        downloadProcess.on('error', async (error) => {
            await db.updateDownloadStatus(downloadId, 'error', null, error.message);
            broadcast({
                type: 'error',
                downloadId,
                error: error.message
            });
            activeDownloads.delete(downloadId);
        });
        
        res.json({ downloadId, status: 'started' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Cancel download
app.post('/api/download/:id/cancel', async (req, res) => {
    try {
        const { id } = req.params;
        const downloadProcess = activeDownloads.get(id);
        
        if (downloadProcess) {
            downloadProcess.kill();
            activeDownloads.delete(id);
            await db.updateDownloadStatus(id, 'cancelled');
            
            broadcast({
                type: 'cancelled',
                downloadId: id
            });
            
            res.json({ status: 'cancelled' });
        } else {
            res.status(404).json({ error: 'Download not found' });
        }
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Get download history
app.get('/api/history', async (req, res) => {
    try {
        const history = await db.getHistory();
        res.json(history);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Get active downloads
app.get('/api/downloads/active', async (req, res) => {
    try {
        const active = await db.getActiveDownloads();
        res.json(active);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Delete history item
app.delete('/api/history/:id', async (req, res) => {
    try {
        const { id } = req.params;
        await db.deleteHistoryItem(id);
        res.json({ status: 'deleted' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Get settings
app.get('/api/settings', async (req, res) => {
    try {
        const settings = await db.getSettings();
        res.json(settings);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Update settings
app.post('/api/settings', async (req, res) => {
    try {
        const settings = req.body;
        await db.updateSettings(settings);
        res.json({ status: 'updated' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Check yt-dlp installation
app.get('/api/ytdlp/check', async (req, res) => {
    try {
        const installed = await ytdlpManager.checkInstallation();
        res.json({ installed });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Install/Update yt-dlp
app.post('/api/ytdlp/install', async (req, res) => {
    try {
        await ytdlpManager.install((progress) => {
            broadcast({
                type: 'ytdlp-install-progress',
                progress
            });
        });
        res.json({ status: 'installed' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Serve React app for all other routes
app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, '../client/build/index.html'));
});

// Initialize and start server
async function start() {
    try {
        // Initialize database
        await db.initialize();
        
        // Check yt-dlp installation
        const ytdlpInstalled = await ytdlpManager.checkInstallation();
        if (!ytdlpInstalled) {
            console.log('yt-dlp not found. Installing...');
            await ytdlpManager.install();
            console.log('yt-dlp installed successfully');
        }
        
        // Start server
        server.listen(PORT, () => {
            console.log(`
╔═══════════════════════════════════════════╗
║                                           ║
║       YTDLnis Web Server Running          ║
║                                           ║
║   Open in browser:                        ║
║   http://localhost:${PORT}                   ║
║                                           ║
╚═══════════════════════════════════════════╝
            `);
        });
    } catch (error) {
        console.error('Failed to start server:', error);
        process.exit(1);
    }
}

start();

