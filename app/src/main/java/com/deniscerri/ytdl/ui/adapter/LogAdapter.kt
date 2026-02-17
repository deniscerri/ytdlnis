package com.deniscerri.ytdl.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.Extensions.enableTextHighlight

class LogAdapter : ListAdapter<String, LogAdapter.LogViewHolder>(DiffCallback()) {

    var isWrapped: Boolean = true
    var textSize: Float = 15f
    var highlight: Boolean = false

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLine: TextView = view.findViewById(R.id.content)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.log_textview, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.tvLine.apply {
            text = getItem(position)

            if (textSize != this@LogAdapter.textSize) {
                textSize = this@LogAdapter.textSize
            }

            if (isWrapped) {
                // --- WRAP MODE ---
                layoutParams = RecyclerView.LayoutParams(
                    // Forces the line to fit the screen width
                    resources.displayMetrics.widthPixels,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setHorizontallyScrolling(false)
                isSingleLine = false
                ellipsize = null
            } else {
                // --- NO-WRAP (BLEED) MODE ---
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                // This is the magic combo for "Bleeding" text
                setHorizontallyScrolling(true)
                isSingleLine = true
                ellipsize = null // Ensure text isn't cut off with dots
            }

            if (highlight) {
                enableTextHighlight()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(old: String, new: String) = false
        override fun areContentsTheSame(old: String, new: String) = false
    }
}