package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.dao.CommandTemplateDao
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.util.extractors.ytdlp.YTDLPUtil


class YTDLPViewModel(private val application: Application) : AndroidViewModel(application) {
    private val dbManager: DBManager
    private val commandTemplateDao: CommandTemplateDao
    private val ytdlpUtil: YTDLPUtil

    init {
        dbManager =  DBManager.getInstance(application)
        commandTemplateDao = DBManager.getInstance(application).commandTemplateDao
        ytdlpUtil = YTDLPUtil(application, commandTemplateDao)
    }

    fun parseYTDLRequestString(item: DownloadItem) : String {
        val req = ytdlpUtil.buildYoutubeDLRequest(item)
        return ytdlpUtil.parseYTDLRequestString(req)
    }

    fun getVersion(channel: String) : String {
        return ytdlpUtil.getVersion(application, channel)
    }

    fun getFilenameTemplatePreview(item: DownloadItem, filenameTemplate: String) : String {
        return ytdlpUtil.getFilenameTemplatePreview(item, filenameTemplate)
    }
}