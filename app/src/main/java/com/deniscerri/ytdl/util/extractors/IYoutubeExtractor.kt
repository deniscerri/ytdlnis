package com.deniscerri.ytdl.util.extractors

import com.deniscerri.ytdl.database.models.ChapterItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel

interface IYoutubeExtractor {
    fun getVideoData(url: String) : Result<List<ResultItem>>
    fun getFormats(url: String) : Result<List<Format>>
    fun getFormatsForAll(urls: List<String>, progress: (progress: ResultViewModel.MultipleFormatProgress) -> Unit) : Result<MutableList<MutableList<Format>>>
    fun search(query: String) : Result<ArrayList<ResultItem>>
    fun searchMusic(query: String) : Result<ArrayList<ResultItem>>
    fun getStreamingUrlAndChapters(url: String) : Result<Pair<List<String>, List<ChapterItem>?>>
    suspend fun getPlaylistData(id: String, progress: (pagedResults: MutableList<ResultItem>) -> Unit) : Result<List<ResultItem>>
    fun getTrending() : ArrayList<ResultItem>
    fun getChannelData(url: String, progress: (pagedResults: MutableList<ResultItem>) -> Unit) : Result<List<ResultItem>>
}