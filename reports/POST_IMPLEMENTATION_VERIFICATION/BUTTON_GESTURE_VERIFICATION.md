# Button Gesture Verification

**Status**: PASS
**Verification**:
- `DraggableDownloadButton` supports 2D dragging via `detectDragGestures`.
- Position coordinates are persisted to Jetpack DataStore preferences (`ButtonDataStore`).
- Tap vs. drag is strictly distinguished so dragging never triggers a click.
- Button remains visible and correctly inset within screen boundaries.
