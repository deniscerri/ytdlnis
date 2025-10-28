const path = require('path');
const fs = require('fs-extra');

class Database {
    constructor() {
        const dbDir = path.join(process.env.HOME || process.env.USERPROFILE, '.ytdlnis-web');
        fs.ensureDirSync(dbDir);
        this.downloadsPath = path.join(dbDir, 'downloads.json');
        this.settingsPath = path.join(dbDir, 'settings.json');
        this.downloads = [];
        this.settings = {};
    }

    async initialize() {
        // Load existing data or create empty files
        if (fs.existsSync(this.downloadsPath)) {
            this.downloads = await fs.readJson(this.downloadsPath);
        } else {
            await fs.writeJson(this.downloadsPath, []);
        }

        if (fs.existsSync(this.settingsPath)) {
            this.settings = await fs.readJson(this.settingsPath);
        } else {
            await this.setDefaultSettings();
        }
        
        return Promise.resolve();
    }

    async saveDownloads() {
        await fs.writeJson(this.downloadsPath, this.downloads, { spaces: 2 });
    }

    async saveSettings() {
        await fs.writeJson(this.settingsPath, this.settings, { spaces: 2 });
    }

    async setDefaultSettings() {
        this.settings = {
            downloadPath: path.join(process.env.HOME || process.env.USERPROFILE, 'Downloads', 'YTDLnis'),
            audioFormat: 'mp3',
            videoFormat: 'mp4',
            audioQuality: '192',
            videoQuality: '1080',
            embedThumbnail: 'true',
            embedMetadata: 'true',
            removeAudio: 'false',
            theme: 'system'
        };
        
        await this.saveSettings();
        
        // Ensure download directory exists
        await fs.ensureDir(this.settings.downloadPath);
    }

    async addDownload(download) {
        this.downloads.push({
            ...download,
            title: download.title || '',
            format: download.format || ''
        });
        await this.saveDownloads();
    }

    async updateDownloadProgress(id, progress, speed = null, eta = null) {
        const download = this.downloads.find(d => d.id === id);
        if (download) {
            download.progress = progress;
            download.speed = speed;
            download.eta = eta;
            download.status = 'downloading';
            await this.saveDownloads();
        }
    }

    async updateDownloadStatus(id, status, filePath = null, error = null) {
        const download = this.downloads.find(d => d.id === id);
        if (download) {
            download.status = status;
            download.filePath = filePath;
            download.error = error;
            if (status === 'completed') {
                download.completedAt = new Date().toISOString();
            }
            await this.saveDownloads();
        }
    }

    async getHistory(limit = 100) {
        return this.downloads
            .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
            .slice(0, limit);
    }

    async getActiveDownloads() {
        return this.downloads
            .filter(d => d.status === 'queued' || d.status === 'downloading')
            .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    }

    async deleteHistoryItem(id) {
        this.downloads = this.downloads.filter(d => d.id !== id);
        await this.saveDownloads();
    }

    async getSettings() {
        return { ...this.settings };
    }

    async updateSettings(newSettings) {
        this.settings = { ...this.settings, ...newSettings };
        await this.saveSettings();
    }

    close() {
        // No-op for file-based storage
    }
}

module.exports = Database;

