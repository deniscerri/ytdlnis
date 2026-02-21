package com.deniscerri.ytdl.ui.more.settings.advanced

import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.adapter.SortableTextItemAdapter
import com.deniscerri.ytdl.ui.more.settings.SettingModule
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.collections.indexOf

object AdvancedSettingsModule : SettingModule {
    override fun bindLogic(pref: Preference, host: SettingHost) {
        val context = pref.context
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        when(pref.key) {
            "yt_player_client" -> {
                pref.setOnPreferenceClickListener {
                    host.requestNavigate(R.id.youtubePlayerClientFragment)
                    false
                }
            }
            "generate_po_tokens" -> {
                pref.setOnPreferenceClickListener {
                    host.requestNavigate(R.id.generateYoutubePoTokensFragment)
                    false
                }
            }
            "format_importance_audio" -> {
                pref.apply {
                    title = "${context.getString(R.string.format_importance)} [${context.getString(R.string.audio)}]"
                    val items = context.resources.getStringArray(R.array.format_importance_audio)
                    val itemValues = context.resources.getStringArray(R.array.format_importance_audio_values).toSet()
                    val prefVideo = prefs.getString("format_importance_audio", itemValues.joinToString(","))!!
                    summary = prefVideo.split(",").mapIndexed { index, s -> "${index + 1}. ${items[itemValues.indexOf(s)]}" }.joinToString("\n")

                    setOnPreferenceClickListener {
                        val prefValue = prefs.getString("format_importance_audio", itemValues.joinToString(","))!!
                        val prefArr = prefValue.split(",")
                        val itms = itemValues.sortedBy { prefArr.indexOf(it) }.map {
                            Pair(it, items[itemValues.indexOf(it)])
                        }.toMutableList()

                        showFormatImportanceDialog(context,title.toString(), itms) { new ->
                            prefs.edit(commit = true) {
                                putString("format_importance_audio", new.joinToString(",") { it.first })
                            }
                            pref.summary = new.map { it.second }.mapIndexed { index, s -> "${index + 1}. $s" }.joinToString("\n")
                            host.refreshUI()
                        }
                        true
                    }
                }
            }
            "format_importance_video" -> {
                pref.apply {
                    title = "${context.getString(R.string.format_importance)} [${context.getString(R.string.video)}]"
                    val items = context.resources.getStringArray(R.array.format_importance_video)
                    val itemValues = context.resources.getStringArray(R.array.format_importance_video_values).toSet()
                    val prefVideo = prefs.getString("format_importance_video", itemValues.joinToString(","))!!
                    summary = prefVideo.split(",").mapIndexed { index, s -> "${index + 1}. ${items[itemValues.indexOf(s)]}" }.joinToString("\n")

                    setOnPreferenceClickListener {
                        val prefValue = prefs.getString("format_importance_video", itemValues.joinToString(","))!!
                        val prefArr = prefValue.split(",")
                        val itms = itemValues.sortedBy { prefArr.indexOf(it) }.map {
                            Pair(it, items[itemValues.indexOf(it)])
                        }.toMutableList()

                        showFormatImportanceDialog(context,title.toString(), itms) {new ->
                            prefs.edit(commit = true) {
                                putString("format_importance_video", new.joinToString(",") { it.first })
                            }
                            pref.summary = new.map { it.second }.mapIndexed { index, s -> "${index + 1}. $s" }.joinToString("\n")
                            host.refreshUI()
                        }
                        true
                    }
                }
            }
        }
    }

    private fun showFormatImportanceDialog(context: Context, t: String, items: MutableList<Pair<String, String>>, onChange: (items: List<Pair<String, String>>) -> Unit){
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(t)
        val adapter = SortableTextItemAdapter(items)
        val itemTouchCallback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val itemToMove = adapter.items[viewHolder.absoluteAdapterPosition]
                adapter.items.remove(itemToMove)
                adapter.items.add(target.absoluteAdapterPosition, itemToMove)

                adapter.notifyItemMoved(
                    viewHolder.absoluteAdapterPosition,
                    target.absoluteAdapterPosition
                )
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // do nothing
            }
        }

        val linear = LinearLayout(context)
        linear.orientation = LinearLayout.VERTICAL

        val note = TextView(context)
        note.text = context.getString(R.string.format_importance_note)
        note.textSize = 16f
        note.setTypeface(note.typeface, Typeface.BOLD)
        note.setPadding(20,20,20,20)
        linear.addView(note)

        val recycler = RecyclerView(context)
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter

        linear.addView(recycler)

        val itemTouchHelper = ItemTouchHelper(itemTouchCallback)
        itemTouchHelper.attachToRecyclerView(recycler)


        builder.setView(linear)
        builder.setPositiveButton(
            context.getString(android.R.string.ok)
        ) { _: DialogInterface?, _: Int ->
            onChange(adapter.items)
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            context.getString(R.string.cancel)
        ) { _: DialogInterface?, _: Int -> }

        val dialog = builder.create()
        dialog.show()
    }
}