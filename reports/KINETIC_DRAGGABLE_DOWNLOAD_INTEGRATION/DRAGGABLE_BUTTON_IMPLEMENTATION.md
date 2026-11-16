# Draggable Button Implementation

## Component
`DraggableDownloadButton.kt`

## Features
- **Floating Overlay**: Positioned above the WebView inside `BrowserScreen`.
- **Free Dragging**: Supports smooth horizontal and vertical dragging using `detectDragGestures`.
- **Boundary Clamping**: Automatically clamped within usable screen bounds.
- **Position Persistence**: Coordinates saved via SharedPreferences (`kinetic_button_prefs`), surviving rotation and app restarts.
- **Visual States**:
  - 0 candidates: Dark gray (`#333333`) with download icon.
  - >0 candidates: Kinetic yellow (`#FFCC00`) with candidate badge count.
- **Tap vs Drag Separation**: Dragging prevents accidental clicks; tapping opens `AllVideosBottomSheet`.
