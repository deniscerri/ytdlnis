# WebView Candidate Integration

## Integration Details
- Integrated with Android WebView (`WebViewEngine`).
- Discovers video elements and page metadata upon completion of page loading (`onLoadFinished`).
- Updates `BrowserViewModel.candidates` StateFlow reactively.
- Drives badge updates on `DraggableDownloadButton`.
