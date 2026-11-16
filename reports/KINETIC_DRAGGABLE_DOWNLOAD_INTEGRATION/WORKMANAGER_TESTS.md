# WorkManager Tests

## Worker
- `DownloadWorker` executes asynchronously via WorkManager.
- Updates database states (`QUEUED`, `DOWNLOADING`, `COMPLETED`, `FAILED`) and output file paths.
