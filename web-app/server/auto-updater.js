const axios = require('axios');
const fs = require('fs-extra');
const path = require('path');
const { spawn } = require('child_process');

class AutoUpdater {
    constructor() {
        this.currentVersion = '1.0.0';
        this.updateUrl = 'https://api.github.com/repos/ytdlnis/ytdlnis-web/releases/latest';
        this.checkInterval = 24 * 60 * 60 * 1000; // 24 hours
    }

    async checkForUpdates() {
        try {
            const response = await axios.get(this.updateUrl);
            const latestVersion = response.data.tag_name.replace('v', '');
            
            if (this.isNewerVersion(latestVersion, this.currentVersion)) {
                return {
                    available: true,
                    version: latestVersion,
                    downloadUrl: response.data.assets[0]?.browser_download_url,
                    releaseNotes: response.data.body
                };
            }
            
            return { available: false };
        } catch (error) {
            console.error('Failed to check for updates:', error.message);
            return { available: false, error: error.message };
        }
    }

    isNewerVersion(latest, current) {
        const latestParts = latest.split('.').map(Number);
        const currentParts = current.split('.').map(Number);
        
        for (let i = 0; i < 3; i++) {
            if (latestParts[i] > currentParts[i]) return true;
            if (latestParts[i] < currentParts[i]) return false;
        }
        
        return false;
    }

    async updateYtdlp() {
        return new Promise((resolve, reject) => {
            const proc = spawn('yt-dlp', ['-U']);
            
            let output = '';
            let error = '';

            proc.stdout.on('data', (data) => {
                output += data.toString();
            });

            proc.stderr.on('data', (data) => {
                error += data.toString();
            });

            proc.on('close', (code) => {
                if (code === 0) {
                    resolve({ success: true, message: output });
                } else {
                    reject(new Error(error || 'Failed to update yt-dlp'));
                }
            });
        });
    }

    startAutoCheck(callback) {
        // Check immediately
        this.checkForUpdates().then(callback);
        
        // Then check periodically
        setInterval(() => {
            this.checkForUpdates().then(callback);
        }, this.checkInterval);
    }
}

module.exports = AutoUpdater;

