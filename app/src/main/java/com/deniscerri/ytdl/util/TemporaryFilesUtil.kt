package com.deniscerri.ytdl.util

import android.content.Context
import com.deniscerri.ytdl.R
import java.io.File

object TemporaryFilesUtil {

    /**
     * @param needsIdleDownloads files in this category can be in use while a download is running
     */
    enum class Category(val key: String, val title: Int, val description: Int, val needsIdleDownloads: Boolean) {
        UNFINISHED_DOWNLOADS("temp_unfinished_downloads", R.string.unfinished_downloads, R.string.unfinished_downloads_summary, true),
        INFO_JSONS("temp_info_jsons", R.string.info_jsons, R.string.info_jsons_summary, true),
        YTDLP_CACHE("temp_ytdlp_cache", R.string.ytdlp_cache, R.string.ytdlp_cache_summary, true),
        TEMP_CONFIGS("temp_configs", R.string.temporary_configurations, R.string.temporary_configurations_summary, true),
        DOWNLOADED_APKS("temp_downloaded_apks", R.string.downloaded_apks, R.string.downloaded_apks_summary, false);

        companion object {
            fun fromKey(key: String?) = values().firstOrNull { it.key == key }
        }
    }

    private const val COOKIES_FILE = "cookies.txt"

    /**
     * Files or folders that make up a category. Folders listed here are emptied but kept in place
     */
    private fun roots(context: Context, category: Category) : List<File> {
        val cache = File(FileUtil.getCachePath(context))
        return when (category) {
            Category.UNFINISHED_DOWNLOADS -> {
                listOf(File(FileUtil.getCacheDownloadsPath(context)))
            }
            Category.INFO_JSONS -> listOf(File(FileUtil.getInfoJsonPath(context)))
            Category.YTDLP_CACHE -> listOf(File(FileUtil.getCacheYTDLPPath(context)))
            Category.TEMP_CONFIGS -> {
                // the app cache dir, minus the active cookies file and the cache folder when it lives in there
                context.cacheDir.listFiles()?.filter {
                    it.name != COOKIES_FILE && cache.absolutePath != it.absolutePath && !cache.absolutePath.startsWith(it.absolutePath + File.separator)
                } ?: emptyList()
            }
            Category.DOWNLOADED_APKS -> listOf(File(FileUtil.getDefaultApksPath()))
        }
    }

    fun getSize(context: Context, category: Category) : Long {
        return roots(context, category).sumOf { root ->
            runCatching {
                root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
        }
    }

    fun clear(context: Context, category: Category) {
        val keepInPlace = category != Category.UNFINISHED_DOWNLOADS && category != Category.TEMP_CONFIGS
        roots(context, category).forEach { root ->
            runCatching {
                if (keepInPlace && root.isDirectory) {
                    root.listFiles()?.forEach { it.deleteRecursively() }
                } else {
                    root.deleteRecursively()
                }
            }
        }
    }

    fun formatSize(size: Long) : String = if (size <= 1) "0 B" else FileUtil.convertFileSize(size)
}
