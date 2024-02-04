package com.deniscerri.ytdlnis.ui.adapter

import android.app.Activity
import android.content.SharedPreferences
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.SearchSuggestionItem
import com.deniscerri.ytdlnis.database.models.SearchSuggestionType


class SearchSuggestionsAdapter(onItemClickListener: OnItemClickListener, activity: Activity) : ListAdapter<SearchSuggestionItem, SearchSuggestionsAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val onItemClickListener: OnItemClickListener
    private val activity: Activity
    private val sharedPreferences: SharedPreferences

    init {
        this.onItemClickListener = onItemClickListener
        this.activity = activity
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    }

    class ViewHolder(itemView: View, onItemClickListener: OnItemClickListener?) : RecyclerView.ViewHolder(itemView) {
        val linear: LinearLayout

        init {
            linear = itemView.findViewById(R.id.linear)
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchSuggestionsAdapter.ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_suggestion_item, parent, false)
        return ViewHolder(cardView, onItemClickListener)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val linear = holder.linear
        when (item.type){
            SearchSuggestionType.SUGGESTION -> {
                val textView = linear.findViewById<TextView>(R.id.suggestion_text)
                textView.text = item.text
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)

                textView.setOnClickListener {
                    onItemClickListener.onSearchSuggestionClick(item.text)
                }
                textView.setOnLongClickListener { true }
                val mb = linear.findViewById<ImageButton>(R.id.set_search_query_button)
                mb.setImageResource(R.drawable.ic_arrow_outward)
                mb.setOnClickListener {
                    onItemClickListener.onSearchSuggestionAddToSearchBar(item.text)
                }
            }
            SearchSuggestionType.HISTORY -> {
                val textView = linear.findViewById<TextView>(R.id.suggestion_text)
                textView.text = item.text
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_restore, 0, 0, 0)
                textView.setOnClickListener {
                    onItemClickListener.onSearchSuggestionClick(item.text)
                }
                textView.setOnLongClickListener {
                    onItemClickListener.onSearchSuggestionLongClick(item.text, position)
                    true
                }

                val mb = linear.findViewById<ImageButton>(R.id.set_search_query_button)
                mb.setImageResource(R.drawable.ic_arrow_outward)
                mb.setOnClickListener {
                    onItemClickListener.onSearchSuggestionAddToSearchBar(item.text)
                }
            }
            SearchSuggestionType.CLIPBOARD -> {
                val textView = linear.findViewById<TextView>(R.id.suggestion_text)
                textView.text = activity.getString(R.string.link_you_copied)
                textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_language, 0, 0, 0)
                val mb = linear.findViewById<ImageButton>(R.id.set_search_query_button)
                mb.setImageResource(R.drawable.ic_plus)

                mb.setOnClickListener {
                    onItemClickListener.onSearchSuggestionAdd(item.text)
                }

                textView.setOnClickListener {
                    onItemClickListener.onSearchSuggestionClick(item.text)
                }
                textView.setOnLongClickListener { true }
            }
        }
    }

    interface OnItemClickListener {
        fun onSearchSuggestionClick(text: String)
        fun onSearchSuggestionAdd(text: String)

        fun onSearchSuggestionLongClick(text: String, position: Int)

        fun onSearchSuggestionAddToSearchBar(text: String)

    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<SearchSuggestionItem> = object : DiffUtil.ItemCallback<SearchSuggestionItem>() {
            override fun areItemsTheSame(oldItem: SearchSuggestionItem, newItem: SearchSuggestionItem): Boolean {
                return oldItem.text == newItem.text && oldItem.type == newItem.type
            }

            override fun areContentsTheSame(oldItem: SearchSuggestionItem, newItem: SearchSuggestionItem): Boolean {
                return oldItem.text == newItem.text && oldItem.type == newItem.type
            }
        }
    }
}