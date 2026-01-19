package com.deniscerri.ytdl.database.viewmodel

import android.app.Application
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.core.view.isVisible
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.DBManager
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.FormatRecyclerView
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.ui.downloadcard.FormatSelectionBottomSheetDialog.FormatCategory
import com.deniscerri.ytdl.ui.downloadcard.FormatSelectionBottomSheetDialog.FormatSorting
import com.deniscerri.ytdl.ui.downloadcard.FormatTuple
import com.deniscerri.ytdl.ui.downloadcard.MultipleItemFormatTuple
import com.deniscerri.ytdl.util.Extensions.isYoutubeURL
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.FormatUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer.Form
import kotlin.text.compareTo

class FormatViewModel(private val application: Application) : AndroidViewModel(application) {
    private val downloadRepository: DownloadRepository
    val selectedItems = MutableStateFlow(listOf<DownloadItem>())
    val selectedItemsSharedFlow = MutableSharedFlow<List<DownloadItem>>(replay = 1)
    var formats : Flow<List<FormatRecyclerView>>
    var showFilterBtn = MutableSharedFlow<Boolean>(1)
    var showRefreshBtn = MutableSharedFlow<Boolean>(1)
    private var canUpdate = true
    var canMultiSelectAudio = MutableStateFlow(false)
    var isMissingFormats = MutableStateFlow(false)

    private var formatUtil = FormatUtil(application)
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    val genericAudioFormats = formatUtil.getGenericAudioFormats(application.resources)
    val genericVideoFormats = formatUtil.getGenericVideoFormats(application.resources)

    var sortBy = FormatSorting.valueOf(sharedPreferences.getString("format_order", "filesize")!!)
    var filterBy = MutableStateFlow(FormatCategory.valueOf(sharedPreferences.getString("format_filter", "ALL")!!))

    private val _noFreeSpace = MutableSharedFlow<String?>()
    val noFreeSpace = _noFreeSpace.asSharedFlow()

    init {
        downloadRepository = DownloadRepository(DBManager.getInstance(application).downloadDao)
        formats = combine(listOf(selectedItemsSharedFlow, filterBy)) { f ->
            val items = selectedItems.value

            if (items.isEmpty()) {
                mutableListOf()
            }else {
                val formats = if (items.size == 1) {
                    items.first().allFormats
                }else {
                    val flatFormatCollection = items.map { it.allFormats }.flatten()

                    flatFormatCollection.groupingBy { it.format_id }.eachCount()
                        .filter { it.value == items.size }
                        .mapValues { flatFormatCollection.first { f -> f.format_id == it.key } }
                        .map { it.value }.toMutableList()
                }

                isMissingFormats.apply {
                    val vl = formats.isEmpty()
                    value = vl
                    emit(vl)
                }

                var chosenFormats: List<Format>

                if (items.size > 1) {
                    if (!isMissingFormats.value) {
                        chosenFormats = formats.mapTo(mutableListOf()) {it.copy()}
                        chosenFormats = when(items.first().type){
                            DownloadType.audio -> chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                            else -> chosenFormats
                        }
                        chosenFormats.forEach {
                            it.filesize = items.map { itm -> itm.allFormats }.flatten().filter { f -> f.format_id == it.format_id }.sumOf { itt -> itt.filesize }
                        }
                    }else{
                        chosenFormats = formats
                    }
                }else{
                    chosenFormats = formats
                    if(!isMissingFormats.value){
                        if(items.first().type == DownloadType.audio){
                            chosenFormats = chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                        }
                    }
                }

                showFilterBtn.apply {
                    val vl = chosenFormats.isNotEmpty() || items.all { it.url.isYoutubeURL() }
                    emit(vl)
                }
                showRefreshBtn.apply {
                    val vl = (isMissingFormats.value || items.isEmpty() || items.first().url.isEmpty()) && canUpdate
                    emit(vl)
                }

                //sort
                var finalFormats: List<Format> = when(sortBy){
                    FormatSorting.container -> chosenFormats.groupBy { it.container }.flatMap { it.value }
                    FormatSorting.id -> chosenFormats.sortedBy { it.format_id }
                    FormatSorting.codec -> {
                        val codecOrder = application.resources.getStringArray(R.array.video_codec_values).toMutableList()
                        codecOrder.removeAt(0)
                        chosenFormats.groupBy { format -> codecOrder.indexOfFirst { format.vcodec.matches("^(${it})(.+)?$".toRegex()) } }

                            .flatMap {
                                it.value.sortedByDescending { l -> l.filesize }
                            }
                    }
                    FormatSorting.filesize -> chosenFormats
                }

                //filter category
                when(filterBy.value){
                    FormatCategory.ALL -> {}
                    FormatCategory.SUGGESTED -> {
                        finalFormats = if (items.first().type == DownloadType.audio){
                            formatUtil.sortAudioFormats(finalFormats)
                        }else{
                            val audioFormats = finalFormats.filter {  it.vcodec.isBlank() || it.vcodec == "none" }
                            val videoFormats = finalFormats.filter {  it.vcodec.isNotBlank() && it.vcodec != "none" }

                            formatUtil.sortVideoFormats(videoFormats) + formatUtil.sortAudioFormats(audioFormats)
                        }
                    }
                    FormatCategory.SMALLEST -> {
                        val tmpFormats = finalFormats
                            .asSequence()
                            .map { it.copy() }
                            .filter { it.filesize > 0 }
                            .onEach {
                                var tmp = it.format_note.lowercase()
                                //remove brackets
                                tmp = tmp.replace(" (.*)".toRegex(), "")

                                //formats that end like 1080P
                                if (tmp.endsWith("060")){
                                    tmp = tmp.removeSuffix("60")
                                }
                                tmp = tmp.removeSuffix("p")
                                //formats that are written like 1920X1080
                                val split = tmp.split("x")
                                if (split.size > 1){
                                    tmp = split[1]
                                }
                                it.format_note = tmp
                            }
                            .groupBy { it.format_note  }
                            .map { it.value.minBy { it2 -> it2.filesize } }.toList()
                        finalFormats = finalFormats.filter { tmpFormats.map { it2 -> it2.format_id }.contains(it.format_id) }
                    }
                    FormatCategory.GENERIC -> {
                        finalFormats = listOf()
                    }
                }

                canMultiSelectAudio.apply {
                    val vl = items.first().type == DownloadType.video && finalFormats.find { it.vcodec.isBlank() || it.vcodec == "none" } != null
                    value = vl
                    emit(vl)
                }

                if (finalFormats.isEmpty()) {
                    finalFormats = if (items.first().type == DownloadType.audio){
                        genericAudioFormats
                    }else{
                        genericVideoFormats
                    }
                }


                val results = mutableListOf<FormatRecyclerView>()
                if (canMultiSelectAudio.value) {
                    results.add(FormatRecyclerView(label = application.getString(R.string.video)))
                    results.addAll(finalFormats.filter { it.vcodec.isNotBlank() && it.vcodec != "none" }.map { FormatRecyclerView(null, it) })
                    results.add(FormatRecyclerView(label = application.getString(R.string.audio)))
                    results.addAll(finalFormats.filter { it.vcodec.isBlank() || it.vcodec == "none" }.map { FormatRecyclerView(null, it) })
                }else{
                    results.addAll(finalFormats.map { FormatRecyclerView(null, it) })
                }

                results
            }
        }
    }

    fun setItems(list: List<DownloadItem>, updateFormats: Boolean? = null) = viewModelScope.launch {
        canUpdate = updateFormats ?: canUpdate
        selectedItems.apply {
            value = list
            emit(list)
        }
        selectedItemsSharedFlow.emit(list)
    }

    fun setItem(item: DownloadItem, updateFormats: Boolean? = null) = viewModelScope.launch {
        canUpdate = updateFormats ?: canUpdate
        selectedItems.apply {
            value = listOf(item)
            emit(listOf(item))
        }
        selectedItemsSharedFlow.emit(listOf(item))
    }


    fun getFormatsForItemsBasedOnFormat(item: Format, audioFormats: List<Format>? = null) : MutableList<MultipleItemFormatTuple> {
        val formatsToReturn = mutableListOf<MultipleItemFormatTuple>()
        val f = if (genericAudioFormats.contains(item) || genericVideoFormats.contains(item)) item else null

        selectedItems.value.forEach {
            formatsToReturn.add(
                MultipleItemFormatTuple(
                    it.url,
                    FormatTuple(
                        f ?: it.allFormats.firstOrNull { af -> af.format_id == item.format_id },
                        audioFormats?.map { sa ->
                            it.allFormats.first { a -> a.format_id == sa.format_id }
                        }?.ifEmpty { null }
                    )
                )
            )
        }

        return formatsToReturn
    }

    fun checkFreeSpace(size: Long, path: String) = viewModelScope.launch {
        _noFreeSpace.emit(null)
        if (size > 10L) {
            File(FileUtil.formatPath(path)).apply {
                if (size > this.freeSpace && this.freeSpace >= 10L) {
                    val warningTxt = application.getString(R.string.no_free_space_warning) +
                            "\n" + "${application.getString(R.string.file_size)}:\t${FileUtil.convertFileSize(size)}" +
                            "\n" + "${application.getString(R.string.freespace)}:\t${FileUtil.convertFileSize(this.freeSpace)}"

                    _noFreeSpace.emit(warningTxt)
                }
            }
        }
    }
}