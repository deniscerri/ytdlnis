package com.deniscerri.ytdl.update

import android.content.Context
import com.deniscerri.ytdl.BuildConfig

/**
 * Launch-time coordinator that re-hydrates [UpdateRegistry] from what survived the last session, so
 * the update flow is consistent across cold starts and offline relaunches:
 *
 *  • A persisted manifest re-surfaces the prompt even with no connectivity. It is dropped once the
 *    running build has caught up to it — which is how an applied update clears itself.
 *  • A staged APK that is newer than the running build resumes straight to [UpdateState.Downloaded]
 *    (the install prompt). An already-installed or stale one is deleted.
 *
 * The manifest is always saved before a download starts, so a staged APK without one belongs to an
 * update that no longer applies and is discarded with it.
 *
 * Called once per process from [com.deniscerri.ytdl.App]; the online check that discovers *new*
 * updates lives in MainActivity, gated on connectivity.
 */
object UpdateManager {

    fun restore(context: Context) {
        val appCtx = context.applicationContext

        val saved = UpdateStore.load(appCtx)?.takeIf { it.versionCode > BuildConfig.BASE_VERSION_CODE }
        if (saved == null) {
            UpdateStore.clear(appCtx)
            ApkDownloader.apkFile(appCtx).delete()
            return
        }

        UpdateRegistry.setAvailable(saved)
        ApkDownloader.stagedApk(appCtx)?.let { UpdateRegistry.update(UpdateState.Downloaded(it)) }
    }
}
