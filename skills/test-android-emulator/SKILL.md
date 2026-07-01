---
name: test-android-emulator
description: Test Android apps on a clean emulator. Use when Codex needs to build, install, launch, smoke-test, or verify Android UI/playback behavior in an emulator while avoiding physical device data, Google account state, Play Store login flows, universal APK version-code issues, or repeated broad ADB approvals.
---

# Test Android Emulator

Use this workflow to run YTDLnis on an emulator with isolated state. Keep commands targeted at a specific emulator serial and avoid touching attached physical devices.

## Rules

- Prefer a clean temporary emulator data directory over an existing saved snapshot.
- Do not use a Google account or interact with Play Store login state.
- Do not run unqualified `adb` install/launch commands when a physical device is attached.
- Do not run `adb uninstall`, `pm clear`, or data-wiping commands against the user's real device.
- Use the ABI-specific APK that matches the emulator. For the ARM64 emulator used here, install:
  `app/build/outputs/apk/github/debug/YTDLnis-1.8.9.2-github-arm64-v8a-debug.apk`
- Build with `./gradlew :app:assembleGithubDebug` when the APK is stale.
- Batch related ADB steps under the already-approved serial prefix when possible:
  `~/Library/Android/sdk/platform-tools/adb -s emulator-5554 ...`

## Clean Emulator Start

Use an existing AVD only as a hardware/system-image template. Avoid its saved data by using a temporary `-datadir` and disabling snapshots.

```bash
rm -rf /private/tmp/ytdlnis_clean_avd_data
mkdir -p /private/tmp/ytdlnis_clean_avd_data
~/Library/Android/sdk/emulator/emulator \
  -avd Medium_Phone_API_36.1 \
  -no-window \
  -no-snapshot \
  -wipe-data \
  -datadir /private/tmp/ytdlnis_clean_avd_data
```

Notes:
- `-wipe-data` is acceptable only with the temporary `-datadir`; it resets the disposable emulator data, not user app data.
- If `emulator-5554` is `unauthorized`, do not continue with install tests. Prefer the clean `-datadir` launch above over clicking through saved-account UI.
- When finished, stop the emulator process. With `-no-snapshot`, state is not saved.

## Install And Launch

After boot:

```bash
~/Library/Android/sdk/platform-tools/adb devices
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell getprop sys.boot_completed
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/github/debug/YTDLnis-1.8.9.2-github-arm64-v8a-debug.apk
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell pm grant com.deniscerri.ytdl android.permission.POST_NOTIFICATIONS
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 logcat -c
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.deniscerri.ytdl/.Default
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell pidof com.deniscerri.ytdl
```

Check for startup failures:

```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 logcat -d -t 500 YTDLnisCrash:E AndroidRuntime:E '*:S'
```

## UI Inspection

Use screenshots and UIAutomator instead of guessing coordinates.

```bash
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 exec-out screencap -p > /private/tmp/ytdlnis_screen.png
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell uiautomator dump /sdcard/window.xml
~/Library/Android/sdk/platform-tools/adb -s emulator-5554 shell cat /sdcard/window.xml
```

When tapping by coordinates, derive bounds from UIAutomator output. Example: a node with `bounds="[669,478][785,594]"` has center `(727,536)`.

## Playback Smoke Test

For persistent-player work, seed a minimal local playlist rather than relying on network downloads:

1. Launch once so Room creates `databases/YTDLnisDatabase`.
2. Pull the database with `run-as`. If the DB uses WAL, also pull `-wal`; avoid reusing copied `-shm` locks locally.
3. Insert:
   - one `playlists` row,
   - one matching `playlist_entries` row,
   - one `history` row with `downloadPath` as a JSON string list.
4. Use an app-private file path for test media:
   `/data/data/com.deniscerri.ytdl/files/ytdlnis-test.wav`
5. Push the seeded DB and media file while the app is force-stopped.

The `history.downloadPath` value should look like:

```json
["/data/data/com.deniscerri.ytdl/files/ytdlnis-test.wav"]
```

Avoid `/sdcard/Download/...` for synthetic test media on recent Android versions; scoped storage can produce `EACCES` when ExoPlayer opens a direct file URI.

## Persistent Player Assertions

For mini-player or playback handoff changes, verify all of these:

- The seeded playlist is visible in Playlists.
- The playlist detail item exposes `play_file`.
- Tapping `play_file` opens the expanded player sheet.
- Dismissing the expanded sheet does not stop or clear the playback session.
- `player_mini_bar` remains visible while browsing back to the Playlists list.
- `frame_layout` bottom is above `player_mini_bar`, and `player_mini_bar` is above `bottomNavigationView` on phone layouts.
- Logcat has no `AndroidRuntime`, `YTDLnisCrash`, or `ExoPlayerImplInternal` errors after the successful run.

Use UIAutomator resource IDs to verify:

- `com.deniscerri.ytdl:id/player_mini_bar`
- `com.deniscerri.ytdl:id/mini_player_title`
- `com.deniscerri.ytdl:id/mini_player_play_pause`
- `com.deniscerri.ytdl:id/mini_player_stop`
- `com.deniscerri.ytdl:id/bottomNavigationView`

## Report

Summarize:

- emulator launch mode, including whether a temporary `-datadir` was used,
- APK installed,
- screens/flows exercised,
- screenshots captured,
- logcat result,
- any limitations caused by synthetic test data.
