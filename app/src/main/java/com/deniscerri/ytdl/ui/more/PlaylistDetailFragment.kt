package com.deniscerri.ytdl.ui.more

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingData
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.PlaylistEntryItem
import com.deniscerri.ytdl.database.models.PlaylistItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.ChannelsViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.PlaylistViewModel
import com.deniscerri.ytdl.ui.adapter.HomeAdapter
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
import com.deniscerri.ytdl.util.PlaybackCoordinator
import com.deniscerri.ytdl.util.PlaybackQueueEntry
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistDetailFragment : Fragment(), HomeAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var homeAdapter: HomeAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var playlistViewModel: PlaylistViewModel
    private lateinit var channelsViewModel: ChannelsViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var playbackCoordinator: PlaybackCoordinator
    private lateinit var noResults: RelativeLayout
    private lateinit var mainActivity: MainActivity
    private var playlistId: Long = 0
    private var playlist: PlaylistItem? = null
    private var currentEntries: List<PlaylistEntryItem> = emptyList()
    private var currentResults: List<ResultItem> = emptyList()
    private var activeQueuedThroughIndex: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mainActivity = activity as MainActivity
        return inflater.inflate(R.layout.fragment_playlist_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        playlistId = requireArguments().getLong("playlistId")
        topAppBar = view.findViewById(R.id.toolbar)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }
        noResults = view.findViewById(R.id.no_results)

        playlistViewModel = ViewModelProvider(this)[PlaylistViewModel::class.java]
        channelsViewModel = ViewModelProvider(this)[ChannelsViewModel::class.java]
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]
        playbackCoordinator = ViewModelProvider(requireActivity())[PlaybackCoordinator::class.java]
        topAppBar.title = getString(R.string.playlists)
        viewLifecycleOwner.lifecycleScope.launch {
            playlist = withContext(Dispatchers.IO) { playlistViewModel.getPlaylist(playlistId) }
            topAppBar.title = playlist?.title ?: getString(R.string.playlists)
            playlist?.let {
                playlistViewModel.prefetchWindow(
                    it,
                    currentEntries,
                    playlistViewModel.getCursor(playlistId).coerceAtMost(currentEntries.lastIndex.coerceAtLeast(0)),
                    channelsViewModel,
                    downloadViewModel
                )
            }
        }

        homeAdapter = HomeAdapter(this, mainActivity).apply {
            useListLayout = true
            onPlayFile = { item, _ -> playFrom(currentResults.indexOfFirst { it.id == item.id }) }
        }
        recyclerView = view.findViewById(R.id.playlist_entries_recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = homeAdapter
        attachItemTouchHelper()

        view.findViewById<Chip>(R.id.playAll).setOnClickListener { playFrom(0) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playbackCoordinator.playlistTransitions.collect { transition ->
                    if (transition.playlistId != playlistId) return@collect
                    val index = transition.index
                    playlistViewModel.setCursor(playlistId, index)
                    currentResults.getOrNull(index)?.let { markPlayed(it) }
                    playlist?.let {
                        viewLifecycleOwner.lifecycleScope.launch {
                            playlistViewModel.prefetchWindow(it, currentEntries, index, channelsViewModel, downloadViewModel)
                        }
                    }
                }
            }
        }

        observeEntries()
        observeDownloadState()
    }

    private fun observeEntries() {
        viewLifecycleOwner.lifecycleScope.launch {
            playlistViewModel.getEntriesFlow(playlistId).collectLatest { entries ->
                currentEntries = entries
                currentResults = entries.map { playlistViewModel.toResultItem(it) }
                noResults.isVisible = entries.isEmpty()
                refreshAdapterState()
                homeAdapter.submitData(PagingData.from(currentResults))
                playlist?.let {
                    playlistViewModel.prefetchWindow(
                        it,
                        entries,
                        playlistViewModel.getCursor(playlistId).coerceAtMost(entries.lastIndex.coerceAtLeast(0)),
                        channelsViewModel,
                        downloadViewModel
                    )
                }
            }
        }
    }

    private fun observeDownloadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                channelsViewModel.runningDownloadUrls.collect {
                    refreshAdapterState()
                    appendReadyQueueItems()
                }
            }
        }
    }

    private suspend fun refreshAdapterState() {
        val downloaded = withContext(Dispatchers.IO) { channelsViewModel.getDownloadedPaths(currentResults) }
        val running = withContext(Dispatchers.IO) { channelsViewModel.getRunningDownloadUrls(currentResults) }
        homeAdapter.downloadedPaths.clear()
        homeAdapter.downloadedPaths.putAll(downloaded)
        homeAdapter.downloadingUrls.clear()
        homeAdapter.downloadingUrls.addAll(running)
        homeAdapter.playedUrls.clear()
        homeAdapter.playedUrls.addAll(playedUrlsFor(currentResults))
        if (homeAdapter.itemCount > 0) homeAdapter.notifyItemRangeChanged(0, homeAdapter.itemCount)
    }

    private fun playFrom(index: Int) {
        if (index !in currentResults.indices) return
        val entries = queueEntriesFrom(index)
        if (entries.isEmpty()) {
            playlist?.let {
                viewLifecycleOwner.lifecycleScope.launch {
                    playlistViewModel.prefetchWindow(it, currentEntries, index, channelsViewModel, downloadViewModel)
                }
            }
            Snackbar.make(recyclerView, R.string.playlist_item_downloading, Snackbar.LENGTH_SHORT).show()
            return
        }
        activeQueuedThroughIndex = index + entries.size - 1
        playlistViewModel.setCursor(playlistId, index)
        markPlayed(currentResults[index])
        playbackCoordinator.startPlaylist(entries, 0, playlistId)
        showPlayerSheet()
    }

    private fun queueEntriesFrom(startIndex: Int): ArrayList<PlaybackQueueEntry> {
        val downloaded = homeAdapter.downloadedPaths
        val queue = arrayListOf<PlaybackQueueEntry>()
        for (i in startIndex until currentResults.size) {
            val result = currentResults[i]
            val path = downloaded[result.url] ?: break
            queue.add(
                PlaybackQueueEntry(
                    path = path,
                    title = result.title,
                    artist = result.author,
                    thumb = result.thumb,
                    url = result.url,
                    index = i
                )
            )
        }
        return queue
    }

    private fun appendReadyQueueItems() {
        val through = activeQueuedThroughIndex ?: return
        val next = through + 1
        if (next !in currentResults.indices) return
        val newEntries = queueEntriesFrom(next)
        if (newEntries.isEmpty()) return
        activeQueuedThroughIndex = next + newEntries.size - 1
        playbackCoordinator.appendQueueEntries(newEntries)
    }

    private fun attachItemTouchHelper() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                viewLifecycleOwner.lifecycleScope.launch { playlistViewModel.reorder(playlistId, from, to) }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= currentEntries.size) return
                viewLifecycleOwner.lifecycleScope.launch { playlistViewModel.deleteEntry(currentEntries[pos]) }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private fun playedIds(): Set<String> =
        requireContext().getSharedPreferences(PLAYED_PREFS, Context.MODE_PRIVATE)
            .getStringSet(PLAYED_KEY, emptySet()) ?: emptySet()

    private fun playedUrlsFor(list: List<ResultItem>): Set<String> {
        val played = playedIds()
        return list.filter { it.url.getIDFromYoutubeURL()?.let(played::contains) == true }
            .map { it.url }
            .toSet()
    }

    private fun markPlayed(item: ResultItem) {
        val id = item.url.getIDFromYoutubeURL() ?: return
        val updated = playedIds().toMutableSet()
        if (updated.add(id)) {
            requireContext().getSharedPreferences(PLAYED_PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(PLAYED_KEY, updated).apply()
        }
        homeAdapter.playedUrls.add(item.url)
    }

    override fun onButtonClick(item: ResultItem, type: DownloadType?) {
        showSingleDownloadSheet(item, type ?: playlist?.type ?: DownloadType.video)
    }

    override fun onLongButtonClick(item: ResultItem, type: DownloadType?) {
        showSingleDownloadSheet(item, type ?: playlist?.type ?: DownloadType.video)
    }

    override fun onCardClick(item: ResultItem, isChecked: Boolean) {}

    override fun onCardDetailsClick(item: ResultItem) {
        val index = currentResults.indexOfFirst { it.id == item.id }
        if (index >= 0 && homeAdapter.downloadedPaths[item.url] != null) {
            playFrom(index)
        } else {
            showSingleDownloadSheet(item, playlist?.type ?: DownloadType.video)
        }
    }

    private fun showSingleDownloadSheet(resultItem: ResultItem, type: DownloadType) {
        if (findNavController().currentDestination?.id != R.id.playlistDetailFragment) return
        val bundle = Bundle()
        downloadCardViewModel.setResultItem(resultItem)
        downloadCardViewModel.setDownloadItem(null)
        bundle.putSerializable("type", downloadViewModel.getDownloadType(type, resultItem.url))
        findNavController().navigate(R.id.downloadBottomSheetDialog, bundle)
    }

    private fun showPlayerSheet() {
        if (parentFragmentManager.findFragmentByTag(PlayerBottomSheetDialog.TAG) != null) return
        PlayerBottomSheetDialog().show(parentFragmentManager, PlayerBottomSheetDialog.TAG)
    }

    companion object {
        private const val PLAYED_PREFS = "channel_played_ids"
        private const val PLAYED_KEY = "ids"
    }
}
