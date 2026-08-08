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
 * Owns the music lookup for a single download card. The picked result itself lives on the
 * download item, this only drives the search lifecycle.
 *
 * The card is shown before the video info is fetched, so the lookup follows the video data:
 * it waits while the title is still unknown and re-runs once the real title arrives, unless
 * the user already made the result their own.
 *
 * A search only returns what the catalogue could answer in one request. The extended tags are
 * completed afterwards, for the shown match alone, and the state is re-emitted when they land
 * so the card fills itself in without ever blocking the result the user is already reading.
 */
class MusicViewModel : ViewModel() {

    sealed class SearchState {
        data object Idle : SearchState()
        /** The video info is not fetched yet, so there is nothing to search for. */
        data object Waiting : SearchState()
        data object Loading : SearchState()
        /** [matches] ranked by the catalogue, [selected] is the one the card shows. */
        data class Found(val matches: List<MusicMetadata>, val selected: Int = 0) : SearchState()
        data object NotFound : SearchState()
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()

    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private var lastQuery: String? = null

    /** Matches already completed, so picking one back costs nothing. */
    private val detailed = mutableSetOf<Int>()

    /** Set once the user searches, picks or edits a result, so syncs stop overwriting it. */
    private var pinned = false

    /** Keeps the lookup in sync with the video info, whenever it becomes available or changes. */
    fun syncWithVideo(title: String, uploader: String, url: String) {
        if (pinned) return

        if (title.isBlank() || title == url) {
            _state.value = SearchState.Waiting
            return
        }

        val query = "$title|$uploader"
        if (query == lastQuery) return
        lastQuery = query
        launchSearch { MusicMetadataUtil.searchFromVideo(title, uploader) }
    }

    /** Manual lookup with a user supplied artist and song name, optionally aimed at one catalogue. */
    fun search(artist: String, song: String, providerId: String?) {
        pin()
        launchSearch { MusicMetadataUtil.search(artist, song, providerId) }
    }

    /** Shows another of the matches, completing its extended tags on the way in. */
    fun select(index: Int) {
        val found = _state.value as? SearchState.Found ?: return
        if (index == found.selected || index !in found.matches.indices) return
        pin()
        _state.value = found.copy(selected = index)
        loadDetails(index)
    }

    /**
     * Protects the current result from being replaced. Also drops a pending completion: once
     * the user is typing, their fields are the truth and nothing may write over them.
     */
    fun pin() {
        pinned = true
        detailsJob?.cancel()
    }

    fun reset() {
        searchJob?.cancel()
        detailsJob?.cancel()
        detailed.clear()
        lastQuery = null
        pinned = false
        _state.value = SearchState.Idle
    }

    private fun launchSearch(query: suspend () -> List<MusicMetadata>) {
        searchJob?.cancel()
        detailsJob?.cancel()
        detailed.clear()
        _state.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            val matches = query()
            if (matches.isEmpty()) {
                _state.value = SearchState.NotFound
                return@launch
            }
            _state.value = SearchState.Found(matches)
            loadDetails(0)
        }
    }

    /** Replaces one match with its completed form, leaving the rest of the result list alone. */
    private fun loadDetails(index: Int) {
        if (index in detailed) return
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            val found = _state.value as? SearchState.Found ?: return@launch
            val completed = MusicMetadataUtil.details(found.matches[index])

            //a search that landed meanwhile owns a different list, this result is stale
            val current = _state.value as? SearchState.Found ?: return@launch
            if (current.matches !== found.matches) return@launch

            detailed += index
            _state.value = current.copy(
                matches = current.matches.toMutableList().also { it[index] = completed }
            )
        }
    }
}
