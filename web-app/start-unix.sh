#!/bin/bash

echo "============================================"
echo "     YTDLnis Web - Starting..."
echo "============================================"
echo ""

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "ERROR: Node.js is not installed!"
    echo ""
    echo "Please install Node.js from: https://nodejs.org"
    echo ""
    read -p "Press Enter to exit..."
    exit 1
fi

# Check if dependencies are installed
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    echo "This may take a few minutes on first run..."
    echo ""
    npm run install-all
    if [ $? -ne 0 ]; then
        echo ""
        echo "ERROR: Failed to install dependencies"
        read -p "Press Enter to exit..."
        exit 1
    fi
fi

# Check if client build exists
if [ ! -d "client/build" ]; then
    echo "Building client..."
    echo ""
    npm run build
    if [ $? -ne 0 ]; then
        echo ""
        echo "ERROR: Failed to build client"
        read -p "Press Enter to exit..."
        exit 1
    fi
fi

# Start the server
echo ""
echo "============================================"
echo " Starting YTDLnis Web Server..."
echo "============================================"
echo ""
echo "The app will open in your browser at:"
echo "http://localhost:3000"
echo ""
echo "Press Ctrl+C to stop the server"
echo "============================================"
echo ""

# Open browser after a delay (different commands for Mac and Linux)
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    (sleep 3 && open http://localhost:3000) &
else
    # Linux
    (sleep 3 && xdg-open http://localhost:3000) &
fi

# Start server
npm start

