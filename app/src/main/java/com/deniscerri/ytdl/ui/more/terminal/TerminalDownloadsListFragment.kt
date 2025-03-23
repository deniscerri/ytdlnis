package com.deniscerri.ytdl.ui.more.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.TerminalItem
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdl.ui.adapter.TerminalDownloadsAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.work.downloader.DownloadManager
import com.deniscerri.ytdl.work.downloader.DownloadWorker
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class TerminalDownloadsListFragment : Fragment(), TerminalDownloadsAdapter.OnItemClickListener {
    private var topAppBar: MaterialToolbar? = null
    private lateinit var noResults: RelativeLayout
    private lateinit var terminalViewModel: TerminalViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        terminalViewModel = ViewModelProvider(this)[TerminalViewModel::class.java]
        return inflater.inflate(R.layout.fragment_terminal_download_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            noResults = view.findViewById(R.id.no_results)
            val recycler = view.findViewById<RecyclerView>(R.id.terminal_recycler)
            val adapter = TerminalDownloadsAdapter(this@TerminalDownloadsListFragment, requireActivity())
            recycler.adapter = adapter
            recycler.enableFastScroll()
            recycler.layoutManager = GridLayoutManager(requireContext(), resources.getInteger(R.integer.grid_size))
            topAppBar = requireActivity().findViewById(R.id.custom_command_toolbar)
            topAppBar!!.setNavigationOnClickListener { requireActivity().finish() }
            topAppBar?.menu?.forEach { it.isVisible = false }
            topAppBar?.menu?.get(0)?.isVisible = true

            topAppBar?.setOnMenuItemClickListener { m: MenuItem ->
                when(m.itemId){
                    R.id.add -> {
                        findNavController().navigate(R.id.terminalFragment)
                    }
                }
                true
            }

            terminalViewModel.getTerminals().collectLatest {
                adapter.submitList(it)
                noResults.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            }
        }

    }
    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadProgressEvent(event: DownloadManager.DownloadProgress) {
        val progressBar = requireView().findViewWithTag<LinearProgressIndicator>("${event.downloadItemID}##progress")
        val outputText = requireView().findViewWithTag<TextView>("${event.downloadItemID}##output")

        requireActivity().runOnUiThread {
            kotlin.runCatching {
                outputText?.text = event.output
                progressBar?.setProgressCompat(event.progress, true)
            }
        }
    }

    override fun onCancelClick(itemID: Long) {
        terminalViewModel.cancelTerminalDownload(itemID)
    }

    override fun onCardClick(item: TerminalItem) {
        val bundle = Bundle()
        bundle.putLong("id", item.id)
        findNavController().navigate(R.id.terminalFragment, bundle)
    }
}