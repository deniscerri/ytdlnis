package com.deniscerri.ytdl.ui.home

import android.content.res.ColorStateList
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
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
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel.AvailableUpdate
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Dialog listing the app and package updates found at startup. Picking one hands it to the parent fragment.
 */
class UpdatesDialog : DialogFragment() {

    interface Listener {
        fun onUpdateSelected(update: AvailableUpdate)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val titleView = LayoutInflater.from(requireContext()).inflate(R.layout.updates_dialog_title, null)
        summary = titleView.findViewById(R.id.updates_summary)
        updateSummary(ViewModelProvider(requireActivity())[ResultViewModel::class.java].availableUpdates.value.size)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.updates_dialog, null)
        setupList(view)
        return MaterialAlertDialogBuilder(requireContext())
            .setCustomTitle(titleView)
            .setView(view)
            .setPositiveButton(R.string.dismiss) { _, _ ->
                ViewModelProvider(requireActivity())[ResultViewModel::class.java].dismissAllUpdates()
            }
            .create()
    }

    private var summary: TextView? = null

    private fun updateSummary(count: Int) {
        summary?.text = resources.getQuantityString(R.plurals.updates_available_summary, count, count)
    }

    private fun setupList(view: View) {
        val resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]

        val adapter = UpdatesAdapter(
            onClick = { update ->
                dismiss()
                (parentFragment as? Listener)?.onUpdateSelected(update)
            },
            // closing the last one empties the list, which hides the toolbar button and this sheet
            onClose = { resultViewModel.dismissUpdate(it) }
        )
        view.findViewById<RecyclerView>(R.id.updates_recycler).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                resultViewModel.availableUpdates.collect { list ->
                    if (list.isEmpty()) {
                        dismiss()
                        return@collect
                    }
                    adapter.submitList(list)
                    updateSummary(list.size)
                }
            }
        }
    }

    private class UpdatesAdapter(
        private val onClick: (AvailableUpdate) -> Unit,
        private val onClose: (AvailableUpdate) -> Unit
    ) : ListAdapter<AvailableUpdate, UpdatesAdapter.ViewHolder>(DIFF) {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.update_card)
            val indicator: MaterialCardView = view.findViewById(R.id.update_indicator)
            val title: TextView = view.findViewById(R.id.update_title)
            val close: View = view.findViewById(R.id.btn_close_update)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.update_item, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            val context = holder.itemView.context

            // tonal indicator per type: primary for the app, tertiary for packages
            val (container, accent) = when (item) {
                is AvailableUpdate.App -> com.google.android.material.R.attr.colorPrimaryContainer to
                        com.google.android.material.R.attr.colorOnPrimaryContainer
                is AvailableUpdate.Package -> com.google.android.material.R.attr.colorTertiaryContainer to
                        com.google.android.material.R.attr.colorOnTertiaryContainer
            }
            holder.indicator.setCardBackgroundColor(MaterialColors.getColor(context, container, 0))

            holder.title.text = when (item) {
                is AvailableUpdate.App -> context.getString(R.string.version_ready_to_download, item.release.tag_name)
                is AvailableUpdate.Package ->
                    "${item.item.title} (${item.release.version})"
            }
            holder.card.setOnClickListener { onClick(item) }
            holder.close.setOnClickListener { onClose(item) }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<AvailableUpdate>() {
                override fun areItemsTheSame(oldItem: AvailableUpdate, newItem: AvailableUpdate) = when {
                    oldItem is AvailableUpdate.App && newItem is AvailableUpdate.App -> true
                    oldItem is AvailableUpdate.Package && newItem is AvailableUpdate.Package ->
                        oldItem.item.title == newItem.item.title
                    else -> false
                }
                override fun areContentsTheSame(oldItem: AvailableUpdate, newItem: AvailableUpdate) = oldItem == newItem
            }
        }
    }
}
