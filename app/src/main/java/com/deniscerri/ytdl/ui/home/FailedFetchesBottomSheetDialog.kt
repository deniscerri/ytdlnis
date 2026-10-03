package com.deniscerri.ytdl.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel.FailedQuery
import com.deniscerri.ytdl.util.Extensions.isURL
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Lists the home screen queries that failed to fetch, with options to retry them or download them anyway
 */
class FailedFetchesBottomSheetDialog : BottomSheetDialogFragment() {

    interface Listener {
        fun onDownloadAnyway(url: String)
    }

    private lateinit var resultViewModel: ResultViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.failed_fetches_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        val subtitle = view.findViewById<TextView>(R.id.failed_fetches_subtitle)
        val adapter = FailedFetchesAdapter(
            onRetry = { retry(listOf(it.query)) },
            onDownloadAnyway = { item ->
                resultViewModel.removeFailedQuery(item.query)
                dismiss()
                (parentFragment as? Listener)?.onDownloadAnyway(item.query)
            }
        )
        view.findViewById<RecyclerView>(R.id.failed_fetches_recycler).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }

        // the list closes the sheet by itself once it's empty
        view.findViewById<MaterialButton>(R.id.delete_all_failed).setOnClickListener {
            resultViewModel.clearFailedQueries()
        }

        view.findViewById<MaterialButton>(R.id.retry_all).setOnClickListener {
            retry(resultViewModel.failedQueries.value.map { it.query })
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                resultViewModel.failedQueries.collect { list ->
                    if (list.isEmpty()) {
                        dismiss()
                        return@collect
                    }
                    subtitle.text = resources.getQuantityString(R.plurals.items_failed_to_fetch, list.size, list.size)
                    adapter.submitList(list)
                }
            }
        }
    }

    private fun retry(queries: List<String>) {
        // retried queries come back to the list if they fail again
        queries.forEach { resultViewModel.removeFailedQuery(it) }
        resultViewModel.retryFailedQueries(queries)
    }

    private class FailedFetchesAdapter(
        private val onRetry: (FailedQuery) -> Unit,
        private val onDownloadAnyway: (FailedQuery) -> Unit
    ) : ListAdapter<FailedQuery, FailedFetchesAdapter.ViewHolder>(DIFF) {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val query: TextView = view.findViewById(R.id.failed_query)
            val error: TextView = view.findViewById(R.id.failed_error)
            val retry: MaterialButton = view.findViewById(R.id.retry)
            val downloadAnyway: MaterialButton = view.findViewById(R.id.download_anyway)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.failed_fetch_item, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.query.text = item.query
            holder.error.text = item.error.trim()
            holder.error.maxLines = 3
            // tap to see the whole error, long press to copy it
            holder.error.setOnClickListener {
                holder.error.maxLines = if (holder.error.maxLines == 3) Int.MAX_VALUE else 3
            }
            holder.error.setOnLongClickListener {
                val clipboard = it.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("error", item.error))
                true
            }
            holder.retry.setOnClickListener { onRetry(item) }
            // search terms have nothing to download without results
            holder.downloadAnyway.isVisible = item.query.isURL()
            holder.downloadAnyway.setOnClickListener { onDownloadAnyway(item) }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<FailedQuery>() {
                override fun areItemsTheSame(oldItem: FailedQuery, newItem: FailedQuery) = oldItem.query == newItem.query
                override fun areContentsTheSame(oldItem: FailedQuery, newItem: FailedQuery) = oldItem == newItem
            }
        }
    }
}
