package com.deniscerri.ytdl.database.models

import com.deniscerri.ytdl.core.packages.PackageBase

data class PackageItem(
    val title: String,
    val plugin: PackageBase
) {
    fun getInstance(): PackageBase = plugin.getInstance()
}