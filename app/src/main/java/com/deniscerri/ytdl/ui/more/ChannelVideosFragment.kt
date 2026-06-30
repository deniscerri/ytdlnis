package com.deniscerri.ytdl.ui.more

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.viewmodel.ChannelsViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.ui.adapter.HomeAdapter
import com.deniscerri.ytdl.util.Extensions.getIDFromYoutubeURL
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelVideosFragment : Fragment(), HomeAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var homeAdapter: HomeAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var channelsViewModel: ChannelsViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var downloadCardViewModel: DownloadCardViewModel
    private lateinit var noResults: RelativeLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var mainActivity: MainActivity
    private var currentVideos: List<ResultItem> = emptyList()
    private var allVideos: List<ResultItem> = emptyList()
    private var useListLayout = false
    private var showHidden = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        return inflater.inflate(R.layout.fragment_channel_videos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val channelUrl = arguments?.getString("channelUrl") ?: ""
        val channelName = arguments?.getString("channelName") ?: ""

        useListLayout = requireContext()
            .getSharedPreferences(VIEW_PREFS, Context.MODE_PRIVATE)
            .getBoolean(VIEW_KEY, false)

        topAppBar = view.findViewById(R.id.toolbar)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }
        topAppBar.title = channelName
        topAppBar.inflateMenu(R.menu.channel_videos_menu)
        updateViewToggleIcon()
        topAppBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_toggle_view -> { toggleViewMode(); true }
                R.id.action_show_hidden -> {
                    // Temporarily reveal hidden videos without wiping the persisted hidden set,
                    // so swiped-away items stay hidden again next time they're filtered out.
                    showHidden = !showHidden
                    viewLifecycleOwner.lifecycleScope.launch { renderVisible() }
                    true
                }
                else -> false
            }
        }

        noResults = view.findViewById(R.id.no_results)
        progressBar = view.findViewById(R.id.progress_bar)

        homeAdapter = HomeAdapter(this, mainActivity)
        homeAdapter.useListLayout = useListLayout
        recyclerView = view.findViewById(R.id.channel_videos_recyclerView)
        recyclerView.layoutManager = layoutManagerForMode()
        recyclerView.adapter = homeAdapter
        attachSwipeToHide()

        channelsViewModel = ViewModelProvider(this)[ChannelsViewModel::class.java]
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        downloadCardViewModel = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java]

        observeDownloadState()
        loadChannelVideos(channelUrl)
    }

    /**
     * Keeps each card's state in sync with the download queue: a spinner shows while a video is
     * downloading and turns into the play button the moment the download finishes — no reload.
     */
    private fun observeDownloadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                channelsViewModel.runningDownloadUrls.collect { runningUrls ->
                    if (currentVideos.isEmpty()) return@collect
                    val running = runningUrls.toSet()
                    val downloaded = withContext(Dispatchers.IO) {
                        channelsViewModel.getDownloadedPaths(currentVideos)
                    }
                    homeAdapter.downloadedPaths.clear()
                    homeAdapter.downloadedPaths.putAll(downloaded)
                    homeAdapter.downloadingUrls.clear()
                    homeAdapter.downloadingUrls.addAll(currentVideos.map { it.url }.filter { running.contains(it) })
                    if (homeAdapter.itemCount > 0) {
                        homeAdapter.notifyItemRangeChanged(0, homeAdapter.itemCount)
                    }
                }
            }
        }
    }

    private fun loadChannelVideos(channelUrl: String) {
        homeAdapter.onPlayFile = { item, path ->
            markPlayed(item)
            PlayerBottomSheetDialog.newInstance(path, item.title, item.author, item.thumb)
                .show(parentFragmentManager, "channelPlayer")
        }

        lifecycleScope.launch {
            // 1. Show cached videos instantly, if any (avoids a slow network wait every visit).
            val cached = withContext(Dispatchers.IO) {
                channelsViewModel.getCachedChannelVideos(channelUrl)
            }
            val hasCache = cached.isNotEmpty()
            if (hasCache) displayVideos(cached)
            progressBar.isVisible = !hasCache

            // 2. Refresh from the network and replace once newer results arrive.
            try {
                val fresh = withContext(Dispatchers.IO) {
                    channelsViewModel.getChannelVideos(channelUrl)
                }
                if (fresh.isNotEmpty()) {
                    displayVideos(fresh)
                } else {
                    // Successful empty refresh is authoritative (the view model also clears the
                    // cache), so drop any stale cached rows still on screen and show the empty state.
                    displayVideos(emptyList())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed refresh is not the same as "no videos": keep any cached content on
                // screen and surface a retry instead of collapsing into the empty state.
                if (!hasCache) noResults.visibility = View.VISIBLE
                Snackbar.make(recyclerView, getString(R.string.errored), Snackbar.LENGTH_LONG)
                    .setAction(R.string.retry) { loadChannelVideos(channelUrl) }
                    .show()
            } finally {
                progressBar.isVisible = false
            }
        }
    }

    private suspend fun displayVideos(list: List<ResultItem>) {
        allVideos = list
        renderVisible()
    }

    /** Renders the fetched videos minus any the user has swiped away to hide. */
    private suspend fun renderVisible() {
        val hidden = hiddenIds()
        val visible = if (showHidden) allVideos
            else allVideos.filterNot { it.url.getIDFromYoutubeURL()?.let(hidden::contains) == true }
        currentVideos = visible
        if (visible.isEmpty()) {
            noResults.visibility = View.VISIBLE
            homeAdapter.submitData(PagingData.from(emptyList()))
            return
        }
        val downloaded = withContext(Dispatchers.IO) {
            channelsViewModel.getDownloadedPaths(visible)
        }
        val running = withContext(Dispatchers.IO) {
            channelsViewModel.getRunningDownloadUrls(visible)
        }
        homeAdapter.downloadedPaths.clear()
        homeAdapter.downloadedPaths.putAll(downloaded)
        homeAdapter.downloadingUrls.clear()
        homeAdapter.downloadingUrls.addAll(running)
        homeAdapter.playedUrls.clear()
        homeAdapter.playedUrls.addAll(playedUrlsFor(visible))
        noResults.visibility = View.GONE
        homeAdapter.submitData(PagingData.from(visible))
    }

    private fun hideVideo(item: ResultItem) {
        val id = item.url.getIDFromYoutubeURL() ?: return
        saveHiddenIds(hiddenIds().toMutableSet().apply { add(id) })
        viewLifecycleOwner.lifecycleScope.launch { renderVisible() }
        Snackbar.make(recyclerView, getString(R.string.video_hidden), Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                saveHiddenIds(hiddenIds().toMutableSet().apply { remove(id) })
                viewLifecycleOwner.lifecycleScope.launch { renderVisible() }
            }.show()
    }

    private fun hiddenIds(): Set<String> =
        requireContext().getSharedPreferences(HIDDEN_PREFS, Context.MODE_PRIVATE)
            .getStringSet(HIDDEN_KEY, emptySet()) ?: emptySet()

    private fun saveHiddenIds(ids: Set<String>) {
        requireContext().getSharedPreferences(HIDDEN_PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(HIDDEN_KEY, ids).apply()
    }

    private fun attachSwipeToHide() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= currentVideos.size) return
                hideVideo(currentVideos[pos])
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private fun layoutManagerForMode() =
        if (useListLayout) LinearLayoutManager(context)
        else GridLayoutManager(context, resources.getInteger(R.integer.grid_size))

    private fun toggleViewMode() {
        useListLayout = !useListLayout
        requireContext().getSharedPreferences(VIEW_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(VIEW_KEY, useListLayout).apply()
        homeAdapter.useListLayout = useListLayout
        recyclerView.layoutManager = layoutManagerForMode()
        recyclerView.swapAdapter(homeAdapter, true)
        updateViewToggleIcon()
    }

    private fun updateViewToggleIcon() {
        topAppBar.menu.findItem(R.id.action_toggle_view)?.setIcon(
            if (useListLayout) R.drawable.ic_view_grid else R.drawable.ic_view_list
        )
    }

    /** Persisted set of played video ids, kept regardless of whether the file still exists. */
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
        if (homeAdapter.itemCount > 0) homeAdapter.notifyItemRangeChanged(0, homeAdapter.itemCount)
    }

    override fun onButtonClick(item: ResultItem, type: DownloadType?) {
        showSingleDownloadSheet(item, type!!)
    }

    override fun onLongButtonClick(item: ResultItem, type: DownloadType?) {
        showSingleDownloadSheet(item, type!!)
    }

    override fun onCardClick(item: ResultItem, isChecked: Boolean) {
        // No multiselect in v1
    }

    override fun onCardDetailsClick(item: ResultItem) {
        // If the track is already downloaded, tapping the card plays the local file in our
        // in-app player (same as the play button) instead of the online streaming preview.
        val downloadedPath = homeAdapter.downloadedPaths[item.url]
        if (downloadedPath != null) {
            markPlayed(item)
            PlayerBottomSheetDialog.newInstance(downloadedPath, item.title, item.author, item.thumb)
                .show(parentFragmentManager, "channelPlayer")
            return
        }
        if (parentFragmentManager.findFragmentByTag("resultDetails") == null) {
            val bundle = Bundle()
            bundle.putParcelable("result", item)
            findNavController().navigate(R.id.resultCardDetailsDialog, bundle)
        }
    }

    private fun showSingleDownloadSheet(resultItem: ResultItem, type: DownloadType) {
        if (findNavController().currentBackStack.value.firstOrNull { it.destination.id == R.id.downloadBottomSheetDialog } == null &&
            findNavController().currentDestination?.id == R.id.channelVideosFragment
        ) {
            val bundle = Bundle()
            downloadCardViewModel.setResultItem(resultItem)
            downloadCardViewModel.setDownloadItem(null)
            bundle.putSerializable("type", downloadViewModel.getDownloadType(type, resultItem.url))
            findNavController().navigate(R.id.downloadBottomSheetDialog, bundle)
        }
    }

    companion object {
        private const val PLAYED_PREFS = "channel_played_ids"
        private const val PLAYED_KEY = "ids"
        private const val VIEW_PREFS = "channel_video_view"
        private const val VIEW_KEY = "list_layout"
        private const val HIDDEN_PREFS = "channel_hidden_ids"
        private const val HIDDEN_KEY = "ids"
    }
}
