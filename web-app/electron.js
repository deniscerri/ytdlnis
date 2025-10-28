const { app, BrowserWindow } = require('electron');
const path = require('path');
const { spawn } = require('child_process');

let mainWindow;
let serverProcess;
const PORT = 3000;

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1400,
        height: 900,
        minWidth: 1000,
        minHeight: 700,
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true
        },
        icon: path.join(__dirname, 'assets/icon.png'),
        show: false
    });

    // Show window when ready
    mainWindow.once('ready-to-show', () => {
        mainWindow.show();
    });

    // Start the Express server
    startServer();

    mainWindow.on('closed', () => {
        mainWindow = null;
    });

    // Open DevTools in development
    if (process.env.NODE_ENV === 'development') {
        mainWindow.webContents.openDevTools();
    }
}

function startServer() {
    const serverPath = path.join(__dirname, 'server/index.js');
    serverProcess = spawn('node', [serverPath], {
        cwd: __dirname,
        stdio: 'ignore',
        shell: true
    });

    serverProcess.stdout?.on('data', (data) => {
        console.log(`Server: ${data}`);
    });

    serverProcess.stderr?.on('data', (data) => {
        console.error(`Server Error: ${data}`);
    });

    // Wait for server to start
    waitForServer(() => {
        mainWindow.loadURL(`http://localhost:${PORT}`);
    });
}

function waitForServer(callback) {
    const http = require('http');
    const checkServer = () => {
        const req = http.get(`http://localhost:${PORT}`, (res) => {
            callback();
        });
        req.on('error', () => {
            setTimeout(checkServer, 500);
        });
    };
    checkServer();
}

app.on('ready', () => {
    createWindow();
});

app.on('window-all-closed', () => {
    // Kill the server process
    if (serverProcess) {
        serverProcess.kill();
        serverProcess = null;
    }
    
    if (process.platform !== 'darwin') {
        app.quit();
    }
});

app.on('activate', () => {
    if (mainWindow === null) {
        createWindow();
    }
});

app.on('before-quit', () => {
    if (serverProcess) {
        serverProcess.kill();
    }
});

// Handle app quit
app.on('will-quit', (event) => {
    if (serverProcess) {
        event.preventDefault();
        serverProcess.kill();
        setTimeout(() => {
            app.exit(0);
        }, 1000);
    }
});

