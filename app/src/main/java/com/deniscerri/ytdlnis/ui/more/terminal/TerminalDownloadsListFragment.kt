package com.deniscerri.ytdlnis.ui.more.terminal

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
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.ui.adapter.TerminalDownloadsAdapter
import com.deniscerri.ytdlnis.database.models.TerminalItem
import com.deniscerri.ytdlnis.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdlnis.util.Extensions.enableFastScroll
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


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

        WorkManager.getInstance(requireContext())
            .getWorkInfosByTagLiveData("terminal")
            .observe(viewLifecycleOwner){ list ->
                list.forEach {work ->
                    if (work == null) return@forEach
                    val id = work.progress.getInt("id", 0)
                    if(id == 0) return@forEach

                    val progress = work.progress.getInt("progress", 0)
                    val output = work.progress.getString("output")

                    val progressBar = view.findViewWithTag<LinearProgressIndicator>("$id##progress")
                    val outputText = view.findViewWithTag<TextView>("$id##output")

                    requireActivity().runOnUiThread {
                        kotlin.runCatching {
                            progressBar?.setProgressCompat(progress, true)
                            outputText?.text = output
                        }
                    }
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