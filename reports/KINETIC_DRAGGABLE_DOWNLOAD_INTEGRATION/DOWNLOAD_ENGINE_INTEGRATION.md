# Download Engine Integration

## Architecture
- Selected candidates from `AllVideosBottomSheet` are queued into the Room database via `DownloadDao`.
- `WorkManager` schedules `DownloadWorker` in the background for resilient, non-blocking media downloads.
- Real progress and completion status are reflected in `DownloadsScreen`.
