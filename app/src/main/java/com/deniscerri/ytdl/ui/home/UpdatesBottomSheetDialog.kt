package com.deniscerri.ytdl.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

/**
 * Lists the app and package updates found at startup. Picking one hands it to the parent fragment.
 */
class UpdatesBottomSheetDialog : BottomSheetDialogFragment() {

    interface Listener {
        fun onUpdateSelected(update: AvailableUpdate)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.updates_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                resultViewModel.availableUpdates.collect { list ->
                    if (list.isEmpty()) {
                        dismiss()
                        return@collect
                    }
                    adapter.submitList(list)
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
            val indicatorIcon: ImageView = view.findViewById(R.id.update_indicator_icon)
            val title: TextView = view.findViewById(R.id.update_title)
            val close: View = view.findViewById(R.id.btn_close_update)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.update_item, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            val context = holder.itemView.context

            // same palette as the old pop-ups: primary for the app, tertiary for packages
            val (container, accent) = when (item) {
                is AvailableUpdate.App -> com.google.android.material.R.attr.colorPrimaryContainer to
                        com.google.android.material.R.attr.colorSecondary
                is AvailableUpdate.Package -> com.google.android.material.R.attr.colorTertiaryContainer to
                        com.google.android.material.R.attr.colorTertiary
            }
            val containerColor = MaterialColors.getColor(context, container, 0)
            holder.card.setCardBackgroundColor(containerColor)
            holder.card.strokeColor = containerColor
            holder.indicator.setCardBackgroundColor(MaterialColors.getColor(context, accent, 0))
            holder.indicatorIcon.imageTintList = ColorStateList.valueOf(containerColor)

            holder.title.text = when (item) {
                is AvailableUpdate.App -> context.getString(R.string.version_ready_to_download, item.release.tag_name)
                is AvailableUpdate.Package ->
                    context.getString(R.string.package_version_ready_to_download, item.item.title, item.release.tag_name)
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
