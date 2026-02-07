package com.deniscerri.ytdl.database.models

import com.deniscerri.ytdl.core.plugins.PluginBase

data class PluginItem(
    val title: String,
    val plugin: PluginBase
) {
    fun getInstance(): PluginBase = plugin.getInstance()
}