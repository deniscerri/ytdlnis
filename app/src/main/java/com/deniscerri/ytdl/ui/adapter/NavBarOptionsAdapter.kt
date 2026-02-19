package com.deniscerri.ytdl.ui.adapter

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.databinding.NavOptionsItemBinding

class NavBarOptionsAdapter(
    val items: MutableList<MenuItem>,
    var selectedHomeTabId: Int,
    onItemClickListener: OnItemClickListener,
) : RecyclerView.Adapter<NavBarOptionsAdapter.NavBarOptionsViewHolder>() {

    private val onItemClickListener: OnItemClickListener

    init {
        this.onItemClickListener = onItemClickListener
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavBarOptionsViewHolder {
        val binding = NavOptionsItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NavBarOptionsViewHolder(binding)
    }

    override fun getItemCount() = items.size

    class NavBarOptionsViewHolder(
        val binding: NavOptionsItemBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onBindViewHolder(holder: NavBarOptionsViewHolder, position: Int) {
        val item = items[position]
        val essential = listOf(R.id.homeFragment, R.id.moreFragment)
        val noHome = listOf(R.id.terminalActivity)
        holder.binding.apply {
            title.text = item.title
            title.contentDescription = item.title


            checkbox.isChecked = item.isVisible || essential.contains(item.itemId)
            checkbox.isEnabled = !essential.contains(item.itemId)
            home.setImageResource(
                if (item.itemId == selectedHomeTabId)
                    R.drawable.baseline_home_filled_24 else R.drawable.ic_home_outlined
            )
            home.isVisible = !noHome.contains(item.itemId)
            home.setOnClickListener {
                if (!checkbox.isChecked || selectedHomeTabId == item.itemId) {
                    return@setOnClickListener
                }
                selectedHomeTabId = item.itemId
                notifyDataSetChanged()
            }
            checkbox.setOnClickListener {
                item.isVisible = checkbox.isChecked
                if (!checkbox.isChecked){
                    onItemClickListener.onNavBarOptionDeselected(this)
                }
            }
        }
    }

    interface OnItemClickListener {
        fun onNavBarOptionDeselected(item: NavOptionsItemBinding)
    }
}