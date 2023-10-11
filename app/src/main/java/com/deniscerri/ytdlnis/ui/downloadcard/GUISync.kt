package com.deniscerri.ytdlnis.ui.downloadcard

import com.deniscerri.ytdlnis.database.models.ResultItem

interface GUISync {
    fun updateTitleAuthor(t: String, a: String)
    fun updateUI(res: ResultItem?)
}