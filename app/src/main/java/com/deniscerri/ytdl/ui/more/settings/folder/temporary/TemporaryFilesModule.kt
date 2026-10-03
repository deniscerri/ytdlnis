package com.deniscerri.ytdl.ui.more.settings.folder.temporary

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.ui.more.settings.folder.temporary.DangerButtonPreference
import com.deniscerri.ytdl.ui.more.settings.folder.temporary.SizePreference
import com.deniscerri.ytdl.util.TemporaryFilesUtil
import com.deniscerri.ytdl.util.TemporaryFilesUtil.Category
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.work.MoveCacheFilesWorker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TemporaryFilesModule : SettingModule {

    override fun bindLogic(pref: Preference, host: SettingHost) {
        val context = pref.context

        when(pref.key) {
            "temporary_files_summary" -> {
                refreshSizes(context, host)
            }
            "clear_all_temporary_files" -> {
                (pref as DangerButtonPreference).onButtonClick = {
                    clear(context, host, Category.values().toList(), context.getString(R.string.clear_everything))
                }
            }
            "move_cache" -> {
                pref.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        val workRequest = OneTimeWorkRequestBuilder<MoveCacheFilesWorker>()
                            .addTag("cacheFiles")
                            .build()

                        WorkManager.getInstance(context).beginUniqueWork(
                            System.currentTimeMillis().toString(),
                            ExistingWorkPolicy.KEEP,
                            workRequest
                        ).enqueue()

                        WorkManager.getInstance(context)
                            .getWorkInfosByTagLiveData("cacheFiles")
                            .observe(host.hostLifecycleOwner){ list ->
                                if (list.isNullOrEmpty()) return@observe

                                if (list.first().state == WorkInfo.State.SUCCEEDED){
                                    refreshSizes(context, host)
                                }
                            }

                        true
                    }
            }
            else -> {
                val category = Category.fromKey(pref.key) ?: return
                pref.onPreferenceClickListener =
                    Preference.OnPreferenceClickListener {
                        clear(context, host, listOf(category), context.getString(category.title))
                        true
                    }
            }
        }
    }

    private fun refreshSizes(context: Context, host: SettingHost) {
        host.hostLifecycleOwner.lifecycleScope.launch {
            val sizes = withContext(Dispatchers.IO) {
                Category.values().associateWith { TemporaryFilesUtil.getSize(context, it) }
            }

            sizes.forEach { (category, size) ->
                (host.findPref(category.key) as? SizePreference)?.sizeText = TemporaryFilesUtil.formatSize(size)
            }
            (host.findPref("temporary_files_summary") as? TemporaryFilesSummaryPreference)?.totalSize =
                TemporaryFilesUtil.formatSize(sizes.values.sum())
            (host.findPref("clear_all_temporary_files") as? DangerButtonPreference)?.isEnabled = sizes.values.sum() > 0
        }
    }

    private fun clear(context: Context, host: SettingHost, categories: List<Category>, title: String) {
        host.hostLifecycleOwner.lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) {
                categories.sumOf { TemporaryFilesUtil.getSize(context, it) }
            }

            UiUtil.showGenericConfirmDialog(host.getHostContext(), title, context.getString(R.string.clear_temporary_files_confirm, TemporaryFilesUtil.formatSize(size))) {
                host.hostLifecycleOwner.lifecycleScope.launch {
                    if (categories.any { it.needsIdleDownloads }) {
                        val downloadViewModel = ViewModelProvider(host.hostViewModelStoreOwner)[DownloadViewModel::class.java]
                        val activeDownloadCount = withContext(Dispatchers.IO) {
                            downloadViewModel.getActiveDownloadsCount()
                        }
                        if (activeDownloadCount > 0) {
                            showSnackbar(host, context.getString(R.string.downloads_running_try_later))
                            return@launch
                        }
                    }

                    withContext(Dispatchers.IO) {
                        categories.forEach { TemporaryFilesUtil.clear(context, it) }
                    }
                    showSnackbar(host, context.getString(R.string.cache_cleared))
                    refreshSizes(context, host)
                }
            }
        }
    }

    private fun showSnackbar(host: SettingHost, message: String) {
        val view = host.hostView ?: return
        if (view.isAttachedToWindow) {
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
        }
    }
}
