# Agent Notes

## Building and Installing YTDLnis

- Build the phone APK with `./gradlew :app:assembleGithubDebug`.
- Install the ABI-specific APK for the connected Pixel/ARM64 phone:
  `app/build/outputs/apk/github/debug/YTDLnis-1.8.9.2-github-arm64-v8a-debug.apk`.
- Do not install the universal APK on this phone. Its versionCode is lower than the ABI split APK and can trigger downgrade/version confusion.
- Install with `adb install -r <apk>` only. Do not use `adb uninstall`, `pm clear`, or any command that wipes app data.
- Do not use `adb install -d` unless the user explicitly asks for a downgrade.
- Do not edit app version fields while building/installing. Check `app/build.gradle`, root Gradle files, `gradle.properties`, and `app/src/main/AndroidManifest.xml` before committing if version drift is suspected.
- Verify after install with:
  `adb shell dumpsys package com.deniscerri.ytdl | rg 'versionCode|dataDir|primaryCpuAbi'`.
- Expected phone install metadata from this session:
  `versionCode=108090204`, `primaryCpuAbi=arm64-v8a`, `dataDir=/data/user/0/com.deniscerri.ytdl`.
- To verify launch without taking over app data:
  `adb logcat -c`, then `adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.deniscerri.ytdl/.Default`, then check `adb shell pidof com.deniscerri.ytdl` and `adb logcat -d -t 500 YTDLnisCrash:E AndroidRuntime:E '*:S'`.
