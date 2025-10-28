const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs-extra');
const axios = require('axios');
const EventEmitter = require('events');

class YtdlpManager {
    constructor() {
        this.ytdlpPath = this.getYtdlpPath();
    }

    getYtdlpPath() {
        // Check if yt-dlp is in PATH
        const isWindows = process.platform === 'win32';
        return isWindows ? 'yt-dlp.exe' : 'yt-dlp';
    }

    async checkInstallation() {
        return new Promise((resolve) => {
            const proc = spawn(this.ytdlpPath, ['--version']);
            
            proc.on('close', (code) => {
                resolve(code === 0);
            });
            
            proc.on('error', () => {
                resolve(false);
            });
        });
    }

    async install(progressCallback) {
        const isWindows = process.platform === 'win32';
        const binDir = path.join(process.env.HOME || process.env.USERPROFILE, '.ytdlnis-web', 'bin');
        await fs.ensureDir(binDir);

        const ytdlpUrl = isWindows 
            ? 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe'
            : 'https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp';

        const ytdlpPath = path.join(binDir, isWindows ? 'yt-dlp.exe' : 'yt-dlp');

        if (progressCallback) progressCallback('Downloading yt-dlp...');

        const response = await axios({
            method: 'get',
            url: ytdlpUrl,
            responseType: 'stream'
        });

        const writer = fs.createWriteStream(ytdlpPath);
        response.data.pipe(writer);

        return new Promise((resolve, reject) => {
            writer.on('finish', async () => {
                if (!isWindows) {
                    await fs.chmod(ytdlpPath, 0o755);
                }
                
                // Update PATH or set full path
                this.ytdlpPath = ytdlpPath;
                
                if (progressCallback) progressCallback('yt-dlp installed successfully');
                resolve();
            });
            writer.on('error', reject);
        });
    }

    async getInfo(url) {
        return new Promise((resolve, reject) => {
            const args = ['-J', '--no-playlist', url];
            const proc = spawn(this.ytdlpPath, args);
            
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
                    try {
                        const info = JSON.parse(output);
                        resolve({
                            title: info.title,
                            thumbnail: info.thumbnail,
                            duration: info.duration,
                            uploader: info.uploader,
                            description: info.description,
                            formats: info.formats
                        });
                    } catch (e) {
                        reject(new Error('Failed to parse video info'));
                    }
                } else {
                    reject(new Error(error || 'Failed to get video info'));
                }
            });
        });
    }

    async getFormats(url) {
        return new Promise((resolve, reject) => {
            const args = ['-F', url];
            const proc = spawn(this.ytdlpPath, args);
            
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
                    resolve(this.parseFormats(output));
                } else {
                    reject(new Error(error || 'Failed to get formats'));
                }
            });
        });
    }

    parseFormats(output) {
        const lines = output.split('\n');
        const formats = [];
        
        for (const line of lines) {
            if (line.match(/^\d+/)) {
                const parts = line.trim().split(/\s+/);
                if (parts.length >= 3) {
                    formats.push({
                        id: parts[0],
                        extension: parts[1],
                        resolution: parts[2],
                        note: parts.slice(3).join(' ')
                    });
                }
            }
        }
        
        return formats;
    }

    download(url, type, format, options = {}, progressCallback) {
        const emitter = new EventEmitter();
        
        const downloadPath = options.downloadPath || path.join(
            process.env.HOME || process.env.USERPROFILE,
            'Downloads',
            'YTDLnis'
        );
        
        fs.ensureDirSync(downloadPath);

        // Set FFmpeg location
        const ffmpegPath = path.join(__dirname, '..', 'ffmpeg', 'ffmpeg.exe');
        const args = [
            '--no-playlist',
            '--newline',
            '-o', path.join(downloadPath, '%(title)s.%(ext)s')
        ];
        
        // Add FFmpeg location if it exists
        if (fs.existsSync(ffmpegPath)) {
            args.push('--ffmpeg-location', path.join(__dirname, '..', 'ffmpeg'));
        }

        // Add format arguments based on type
        if (type === 'audio') {
            args.push('-x');
            args.push('--audio-format', format || 'mp3');
            args.push('--audio-quality', options.audioQuality || '192');
            if (options.embedThumbnail) {
                args.push('--embed-thumbnail');
                args.push('--convert-thumbnails', 'jpg');
            }
            if (options.embedMetadata) {
                args.push('--embed-metadata');
            }
        } else if (type === 'video') {
            if (format) {
                args.push('-f', format);
            } else {
                args.push('-f', 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best');
            }
            args.push('--merge-output-format', options.videoFormat || 'mp4');
            if (options.embedSubs) {
                args.push('--embed-subs');
            }
            if (options.embedThumbnail) {
                args.push('--embed-thumbnail');
            }
            args.push('--embed-metadata');
        }

        // Add URL
        args.push(url);

        console.log('Running yt-dlp with args:', args);
        const proc = spawn(this.ytdlpPath, args);
        
        let lastProgress = 0;
        let downloadedFile = null;

        proc.stdout.on('data', (data) => {
            const output = data.toString();
            console.log('[yt-dlp]', output);
            
            // Parse progress from yt-dlp output
            const progressMatch = output.match(/\[download\]\s+(\d+\.?\d*)%/);
            const speedMatch = output.match(/at\s+(\S+\/s)/);
            const etaMatch = output.match(/ETA\s+(\S+)/);
            
            // Check for destination file
            const destMatch = output.match(/\[download\] Destination: (.+)/);
            if (destMatch) {
                downloadedFile = destMatch[1].trim();
            }
            
            // Check for merger
            const mergeMatch = output.match(/\[Merger\] Merging formats into "(.+)"/);
            if (mergeMatch) {
                downloadedFile = mergeMatch[1].trim();
            }
            
            if (progressMatch) {
                const percent = parseFloat(progressMatch[1]);
                if (percent > lastProgress) {
                    lastProgress = percent;
                    progressCallback({
                        percent,
                        speed: speedMatch ? speedMatch[1] : null,
                        eta: etaMatch ? etaMatch[1] : null
                    });
                }
            }
        });

        proc.stderr.on('data', (data) => {
            const output = data.toString();
            console.error('[yt-dlp error]', output);
        });

        proc.on('close', (code) => {
            console.log('yt-dlp process closed with code:', code);
            
            // Give FFmpeg a moment to finish if it's processing
            setTimeout(() => {
                if (code === 0) {
                    // Report 100% completion
                    progressCallback({ percent: 100 });
                    
                    // Find the downloaded file
                    if (!downloadedFile || !fs.existsSync(downloadedFile)) {
                        // Try to find the latest file in download directory
                        try {
                            const files = fs.readdirSync(downloadPath)
                                .filter(f => !f.startsWith('.') && !f.endsWith('.part') && !f.endsWith('.ytdl'))
                                .map(f => path.join(downloadPath, f))
                                .filter(f => {
                                    try {
                                        return fs.statSync(f).isFile();
                                    } catch {
                                        return false;
                                    }
                                });
                            
                            if (files.length > 0) {
                                downloadedFile = files.sort((a, b) => 
                                    fs.statSync(b).mtime - fs.statSync(a).mtime
                                )[0];
                            }
                        } catch (err) {
                            console.error('Error finding downloaded file:', err);
                        }
                    }
                    
                    if (downloadedFile && fs.existsSync(downloadedFile)) {
                        console.log('Download completed:', downloadedFile);
                        emitter.emit('complete', { path: downloadedFile });
                    } else {
                        console.error('Download completed but file not found in:', downloadPath);
                        emitter.emit('error', new Error('Downloaded file not found'));
                    }
                } else {
                    emitter.emit('error', new Error(`Download failed with code ${code}`));
                }
            }, 1000); // Wait 1 second for FFmpeg to finish
        });

        emitter.kill = () => proc.kill();
        
        return emitter;
    }
}

module.exports = YtdlpManager;

