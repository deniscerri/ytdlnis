package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.JsonParser
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.CropCircleTransformation
import java.util.Locale

/**
 * Shows the Weblate translators bundled in assets/translators.json
 * (generated in CI by scripts/fetch_translators.py, may be missing in other builds).
 */
object TranslatorsDialog {
    private const val ASSET = "translators.json"
    private const val WEBLATE_URL = "https://hosted.weblate.org/projects/ytdlnis/"

    private sealed class Row {
        data class Header(val language: String) : Row()
        data class User(val username: String, val avatar: String) : Row()
    }

    fun show(context: Context) {
        val view = LayoutInflater.from(context).inflate(R.layout.translators_dialog, null)
        val list = view.findViewById<RecyclerView>(R.id.translators_list)
        val empty = view.findViewById<View>(R.id.translators_empty)

        val rows = load(context)
        if (rows.isEmpty()) {
            list.visibility = View.GONE
            empty.visibility = View.VISIBLE
        } else {
            list.layoutManager = LinearLayoutManager(context)
            list.adapter = Adapter(rows)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.translators)
            .setView(view)
            .setPositiveButton(R.string.help_translate) { _, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBLATE_URL)))
            }
            .setNegativeButton(R.string.dismiss, null)
            .show()
    }

    private fun load(context: Context): List<Row> = runCatching {
        val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val rows = mutableListOf<Row>()
        JsonParser.parseString(json).asJsonObject.entrySet()
            .map { (code, users) -> languageName(code) to users.asJsonArray }
            .sortedBy { it.first }
            .forEach { (language, users) ->
                if (users.size() == 0) return@forEach
                rows.add(Row.Header(language))
                users.forEach {
                    val user = it.asJsonObject
                    rows.add(Row.User(user["username"].asString, user["avatar"]?.asString ?: ""))
                }
            }
        rows
    }.getOrDefault(emptyList())

    // Weblate codes look like pt_BR or zh_Hans
    private fun languageName(code: String): String {
        val tag = code.replace('_', '-')
        val name = Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault())
        return if (name.isBlank() || name == tag) code else name.replaceFirstChar { it.uppercase() }
    }

    private class Adapter(private val rows: List<Row>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        class HeaderHolder(val text: TextView) : RecyclerView.ViewHolder(text)
        class UserHolder(view: View) : RecyclerView.ViewHolder(view) {
            val initials: TextView = view.findViewById(R.id.initials)
            val avatar: ImageView = view.findViewById(R.id.avatar)
            val username: TextView = view.findViewById(R.id.username)
        }

        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                HeaderHolder(inflater.inflate(R.layout.translator_header_item, parent, false) as TextView)
            } else {
                UserHolder(inflater.inflate(R.layout.translator_item, parent, false))
            }
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).text.text = row.language
                is Row.User -> {
                    holder as UserHolder
                    holder.username.text = row.username
                    holder.initials.text = row.username.take(1).uppercase()
                    // the avatar only becomes visible once it loads, otherwise the initials stay
                    holder.avatar.setImageDrawable(null)
                    holder.avatar.visibility = View.INVISIBLE
                    if (row.avatar.isNotBlank()) {
                        Picasso.get()
                            .load(row.avatar)
                            .transform(CropCircleTransformation())
                            .into(holder.avatar, object : Callback {
                                override fun onSuccess() {
                                    holder.initials.isVisible = false
                                    holder.avatar.isVisible = true
                                }

                                override fun onError(e: Exception?) {}
                            })
                    }
                }
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            if (holder is UserHolder) Picasso.get().cancelRequest(holder.avatar)
        }
    }
}
