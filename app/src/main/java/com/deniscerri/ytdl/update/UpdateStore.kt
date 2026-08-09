package com.deniscerri.ytdl.update

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * Persists the discovered [AppUpdate] across launches so the prompt survives a cold start. Once a
 * newer build is found online its manifest is saved here; on the next launch [UpdateManager]
 * re-hydrates [UpdateRegistry] from it — so a user who first saw the prompt online still sees it
 * after relaunching offline. Cleared once the running build has caught up to the saved version.
 *
 * Lives in the app's default preferences alongside every other setting, so there is one store to
 * back up and one to reset.
 */
object UpdateStore {

    private const val KEY_UPDATE = "app_update_manifest"

    fun save(context: Context, update: AppUpdate) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit { putString(KEY_UPDATE, update.toJson()) }
    }

    fun load(context: Context): AppUpdate? =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_UPDATE, null)
            ?.let { runCatching { AppUpdate.fromJson(it) }.getOrNull() }

    fun clear(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit { remove(KEY_UPDATE) }
    }
}
