package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.FormatRecyclerView
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.ui.downloadcard.FormatSelectionBottomSheetDialog.FormatCategory
import com.deniscerri.ytdl.ui.downloadcard.FormatSelectionBottomSheetDialog.FormatSorting
import com.deniscerri.ytdl.ui.downloadcard.FormatTuple
import com.deniscerri.ytdl.ui.downloadcard.MultipleItemFormatTuple
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.FormatUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

class DownloadCardViewModel(private val application: Application) : AndroidViewModel(application) {
    var resultItem: ResultItem? = null
        private set

    var downloadItem: DownloadItem? = null
        private set

    init {

    }

    fun setDownloadItem(item: DownloadItem?) {
        downloadItem = item
    }

    fun setResultItem(item: ResultItem?) {
        resultItem = item
    }
}