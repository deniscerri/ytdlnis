# Upstream YTDLnis Restoration

**Reference Upstream Snapshot**: `deniscerri/ytdlnis@17ba89dcb6527d2eac1215edb839ac2f6eae3c69`

## Restoration Strategy
- Preserved Kinetic's robust multi-module Jetpack Compose architecture and WebView browser.
- Restored and integrated the core download queue engine using Room (`DownloadDao`, `DownloadItem`) and WorkManager (`DownloadWorker`).
- Maintained clean separation between browser navigation, video candidate discovery, bottom sheet selection, and background download execution.
