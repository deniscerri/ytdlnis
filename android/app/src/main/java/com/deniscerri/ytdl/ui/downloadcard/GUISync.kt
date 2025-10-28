package com.deniscerri.ytdl.ui.downloadcard

import com.deniscerri.ytdl.database.models.ResultItem

interface GUISync {
    fun updateTitleAuthor(t: String, a: String)
    fun updateUI(res: ResultItem?)
}