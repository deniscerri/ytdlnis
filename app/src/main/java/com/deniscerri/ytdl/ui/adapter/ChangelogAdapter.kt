package com.deniscerri.ytdl.ui.adapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.util.Extensions.popup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import java.text.SimpleDateFormat
import java.util.Locale

class ChangelogAdapter(activity: Activity) : ListAdapter<GithubRelease?, ChangelogAdapter.ViewHolder>(AsyncDifferConfig.Builder(
    DIFF_CALLBACK
).build()) {
    private val activity: Activity

    init {
        this.activity = activity
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var layoutParams: LinearLayout.LayoutParams
        init {
            layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(10, 10, 10, 0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val cardView = LayoutInflater.from(parent.context)
            .inflate(R.layout.changelog_item, parent, false)
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val it = getItem(position) ?: return
        val card = holder.itemView

        val layoutParams = holder.layoutParams

        card.findViewById<TextView>(R.id.version).text = it.tag_name
        card.findViewById<TextView>(R.id.date).text =  SimpleDateFormat(
            DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(it.published_at.time)

        val mdText = card.findViewById<TextView>(R.id.content)
        val mw = Markwon.builder(activity).usePlugin(object: AbstractMarkwonPlugin() {
            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    ContextCompat.startActivity(activity, browserIntent, Bundle())
                }
            }
        }).build()
        mw.setMarkdown(mdText, it.body)

        val assetGroup = card.findViewById<ChipGroup>(R.id.assets)
        assetGroup.removeAllViews()
        it.assets.forEachIndexed { idx, c ->
            val tmp = activity.layoutInflater.inflate(R.layout.filter_chip, assetGroup, false) as Chip
            tmp.isCheckable = false
            tmp.layoutParams = layoutParams
            tmp.text = c.name
            tmp.id = idx
            tmp.setOnClickListener {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(c.browser_download_url))
                ContextCompat.startActivity(activity, browserIntent, Bundle())
            }
            assetGroup!!.addView(tmp)
        }
    }

    companion object {
        private val DIFF_CALLBACK: DiffUtil.ItemCallback<GithubRelease> = object : DiffUtil.ItemCallback<GithubRelease>() {
            override fun areItemsTheSame(oldItem: GithubRelease, newItem: GithubRelease): Boolean {
                return oldItem.html_url == newItem.html_url
            }

            override fun areContentsTheSame(oldItem: GithubRelease, newItem: GithubRelease): Boolean {
                return oldItem.tag_name == newItem.tag_name
            }
        }
    }
}