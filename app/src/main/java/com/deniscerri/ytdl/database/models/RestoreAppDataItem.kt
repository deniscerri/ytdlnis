package com.deniscerri.ytdl.database.models

import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.google.gson.JsonArray

data class RestoreAppDataItem(
    var settings : JsonArray? = null,
    var downloads: List<HistoryItem>? = null,
    var queued: List<DownloadItem>? = null,
    var scheduled: List<DownloadItem>? = null,
    var cancelled: List<DownloadItem>? = null,
    var errored: List<DownloadItem>? = null,
    var saved: List<DownloadItem>? = null,
    var cookies: List<CookieItem>? = null,
    var templates: List<CommandTemplate>? = null,
    var shortcuts: List<TemplateShortcut>? = null,
    var searchHistory: List<SearchHistoryItem>? = null,
    var observeSources: List<ObserveSourcesItem>? = null,
)