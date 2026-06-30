package com.deniscerri.ytdl.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RelativeLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CancellationException
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ChannelItem
import com.deniscerri.ytdl.database.viewmodel.ChannelsViewModel
import com.deniscerri.ytdl.ui.adapter.ChannelsAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelSearchFragment : Fragment(), ChannelsAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchAdapter: ChannelsAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var channelsViewModel: ChannelsViewModel
    private lateinit var noResults: RelativeLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var searchInput: AppCompatEditText
    private lateinit var searchButton: MaterialButton
    private lateinit var mainActivity: MainActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        return inflater.inflate(R.layout.fragment_channel_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        topAppBar = view.findViewById(R.id.toolbar)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }

        noResults = view.findViewById(R.id.no_results)
        progressBar = view.findViewById(R.id.progress_bar)
        searchInput = view.findViewById(R.id.search_input)
        searchButton = view.findViewById(R.id.search_button)

        searchAdapter = ChannelsAdapter(this, mainActivity)
        recyclerView = view.findViewById(R.id.search_results_recyclerView)
        recyclerView.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))
        recyclerView.adapter = searchAdapter

        channelsViewModel = ViewModelProvider(this)[ChannelsViewModel::class.java]

        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }

        searchInput.setOnEditorActionListener { _, _, _ ->
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
            true
        }
    }

    private fun performSearch(query: String) {
        progressBar.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    channelsViewModel.searchChannels(query)
                }
                if (results.isEmpty()) {
                    searchAdapter.submitList(emptyList())
                    noResults.visibility = View.VISIBLE
                } else {
                    noResults.visibility = View.GONE
                    searchAdapter.submitList(results)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchAdapter.submitList(emptyList())
                noResults.visibility = View.VISIBLE
                Snackbar.make(requireView(), getString(R.string.search_failed), Snackbar.LENGTH_SHORT).show()
            } finally {
                progressBar.isVisible = false
            }
        }
    }

    override fun onItemClick(item: ChannelItem) {
        val anchor = view ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                channelsViewModel.insert(item)
            }
            if (result > 0) {
                Snackbar.make(anchor, getString(R.string.channel_saved), Snackbar.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } else {
                Snackbar.make(anchor, getString(R.string.channel_already_exists), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDelete(item: ChannelItem) {
        // No delete in search
    }
}
