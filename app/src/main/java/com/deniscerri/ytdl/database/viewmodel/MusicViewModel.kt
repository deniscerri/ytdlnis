package com.deniscerri.ytdl.database.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.database.models.MusicMetadata
import com.deniscerri.ytdl.util.extractors.music.MusicMetadataUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the music lookup for a single download card. The picked/edited result itself lives in
 * the download item, this only drives the search lifecycle so it survives configuration changes.
 */
class MusicViewModel : ViewModel() {

    sealed class SearchState {
        data object Idle : SearchState()
        data object Loading : SearchState()
        data class Found(val matches: List<MusicMetadata>) : SearchState()
        data object NotFound : SearchState()
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    /** Automatic lookup from the fetched video info. */
    fun searchFromVideo(videoTitle: String, uploader: String) =
        launchSearch { MusicMetadataUtil.searchFromVideo(videoTitle, uploader) }

    /** Manual lookup with a user supplied artist and song name. */
    fun search(artist: String, song: String) =
        launchSearch { MusicMetadataUtil.search(artist, song) }

    /** Marks the state as resolved without a new request (restored or user edited metadata). */
    fun setMatches(matches: List<MusicMetadata>) {
        searchJob?.cancel()
        _state.value = if (matches.isEmpty()) SearchState.NotFound else SearchState.Found(matches)
    }

    fun reset() {
        searchJob?.cancel()
        _state.value = SearchState.Idle
    }

    private fun launchSearch(query: suspend () -> List<MusicMetadata>) {
        searchJob?.cancel()
        _state.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            val matches = query()
            _state.value = if (matches.isEmpty()) SearchState.NotFound else SearchState.Found(matches)
        }
    }
}
