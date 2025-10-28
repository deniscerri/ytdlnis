#!/usr/bin/env node

const fs = require('fs-extra');
const path = require('path');
const { execSync } = require('child_process');

console.log('Building YTDLnis Web installers...\n');

// Ensure client is built
console.log('1. Building React client...');
try {
    execSync('cd client && npm run build', { stdio: 'inherit' });
    console.log('✓ Client built successfully\n');
} catch (error) {
    console.error('✗ Failed to build client');
    process.exit(1);
}

// Create assets directory if it doesn't exist
const assetsDir = path.join(__dirname, '../assets');
if (!fs.existsSync(assetsDir)) {
    fs.mkdirSync(assetsDir);
    console.log('Created assets directory');
}

// Check for icon files
const iconPath = path.join(assetsDir, 'icon.png');
if (!fs.existsSync(iconPath)) {
    console.log('Warning: No icon.png found in assets/');
    console.log('Using default icon...');
    // Create a simple placeholder if needed
}

// Build with electron-builder
console.log('2. Building installers with electron-builder...');
try {
    execSync('npx electron-builder --win --mac --linux', { stdio: 'inherit' });
    console.log('\n✓ Installers built successfully!');
    console.log('\nBuilt files can be found in the dist/ directory:');
    console.log('  - Windows: YTDLnis-Web-Setup.exe');
    console.log('  - macOS: YTDLnis-Web.dmg');
    console.log('  - Linux: YTDLnis-Web.AppImage');
} catch (error) {
    console.error('\n✗ Failed to build installers');
    console.error('Make sure electron-builder is installed:');
    console.error('  npm install --save-dev electron-builder');
    process.exit(1);
}

console.log('\n✓ Build complete!');

