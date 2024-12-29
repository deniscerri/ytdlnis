package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.adapter.SortableTextItemAdapter
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class ProcessingSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.processing
    @SuppressLint("RestrictedApi")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.processing_preferences, rootKey)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        val editor = prefs.edit()

        val preferredFormatID : EditTextPreference? = findPreference("format_id")
        val preferredFormatIDAudio : EditTextPreference? = findPreference("format_id_audio")
        val subtitleLanguages : Preference? = findPreference("subs_lang")
        val formatImportanceAudio: Preference? = findPreference("format_importance_audio")
        val formatImportanceVideo: Preference? = findPreference("format_importance_video")

        preferredFormatID?.title = "${getString(R.string.preferred_format_id)} [${getString(R.string.video)}]"
        preferredFormatID?.dialogTitle = "${getString(R.string.file_name_template)} [${getString(R.string.video)}]"

        preferredFormatIDAudio?.title = "${getString(R.string.preferred_format_id)} [${getString(R.string.audio)}]"
        preferredFormatIDAudio?.dialogTitle = "${getString(R.string.file_name_template)} [${getString(R.string.audio)}]"

        subtitleLanguages?.summary = prefs.getString("subs_lang", "en.*,.*-orig")!!
        subtitleLanguages?.setOnPreferenceClickListener {
            UiUtil.showSubtitleLanguagesDialog(requireActivity(), prefs.getString("subs_lang", "en.*,.*-orig")!!){
                editor.putString("subs_lang", it)
                editor.apply()
                subtitleLanguages.summary = it
            }
            true
        }


        formatImportanceAudio?.apply {
            title = "${getString(R.string.format_importance)} [${getString(R.string.audio)}]"
            val items = requireContext().getStringArray(R.array.format_importance_audio)
            val itemValues = requireContext().getStringArray(R.array.format_importance_audio_values).toSet()
            val prefVideo = prefs.getString("format_importance_audio", itemValues.joinToString(","))!!
            summary = prefVideo.split(",").mapIndexed { index, s -> "${index + 1}. ${items[itemValues.indexOf(s)]}" }.joinToString("\n")

            setOnPreferenceClickListener {
                val pref = prefs.getString("format_importance_audio", itemValues.joinToString(","))!!
                val prefArr = pref.split(",")
                val itms = itemValues.sortedBy { prefArr.indexOf(it) }.map {
                    Pair(it, items[itemValues.indexOf(it)])
                }.toMutableList()

                showFormatImportanceDialog(title.toString(), itms) { new ->
                    editor.putString("format_importance_audio", new.joinToString(",") { it.first }).apply()
                    formatImportanceAudio.summary = new.map { it.second }.mapIndexed { index, s -> "${index + 1}. $s" }.joinToString("\n")
                }
                true
            }
        }

        formatImportanceVideo?.apply {
            title = "${getString(R.string.format_importance)} [${getString(R.string.video)}]"
            val items = requireContext().getStringArray(R.array.format_importance_video)
            val itemValues = requireContext().getStringArray(R.array.format_importance_video_values).toSet()
            val prefVideo = prefs.getString("format_importance_video", itemValues.joinToString(","))!!
            summary = prefVideo.split(",").mapIndexed { index, s -> "${index + 1}. ${items[itemValues.indexOf(s)]}" }.joinToString("\n")

            setOnPreferenceClickListener {
                val pref = prefs.getString("format_importance_video", itemValues.joinToString(","))!!
                val prefArr = pref.split(",")
                val itms = itemValues.sortedBy { prefArr.indexOf(it) }.map {
                    Pair(it, items[itemValues.indexOf(it)])
                }.toMutableList()

                showFormatImportanceDialog(title.toString(), itms) {new ->
                    editor.putString("format_importance_video", new.joinToString(",") { it.first }).apply()
                    formatImportanceVideo.summary = new.map { it.second }.mapIndexed { index, s -> "${index + 1}. $s" }.joinToString("\n")
                }
                true
            }
        }

        findPreference<EditTextPreference>("format_id")?.apply {
            val s = getString(R.string.preferred_format_id_summary)
            summary = if (text.isNullOrBlank()) {
                s
            }else {
                "${s}\n[${text}]"
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) {
                    s
                }else {
                    "${s}\n[${newValue}]"
                }
                true
            }
        }

        findPreference<EditTextPreference>("format_id_audio")?.apply {
            val s = getString(R.string.preferred_format_id_summary)
            summary = if (text.isNullOrBlank()) {
                s
            }else {
                "${s}\n[${text}]"
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String?).isNullOrBlank()) {
                    s
                }else {
                    "${s}\n[${newValue}]"
                }
                true
            }
        }
    }


    private fun showFormatImportanceDialog(t: String, items: MutableList<Pair<String, String>>, onChange: (items: List<Pair<String, String>>) -> Unit){
        val builder = MaterialAlertDialogBuilder(requireContext())
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

        val linear = LinearLayout(requireActivity())
        linear.orientation = LinearLayout.VERTICAL

        val note = TextView(requireActivity())
        note.text = getString(R.string.format_importance_note)
        note.textSize = 16f
        note.setTypeface(note.typeface, Typeface.BOLD)
        note.setPadding(20,20,20,20)
        linear.addView(note)

        val recycler = RecyclerView(requireContext())
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        linear.addView(recycler)

        val itemTouchHelper = ItemTouchHelper(itemTouchCallback)
        itemTouchHelper.attachToRecyclerView(recycler)


        builder.setView(linear)
        builder.setPositiveButton(
            getString(android.R.string.ok)
        ) { _: DialogInterface?, _: Int ->
            onChange(adapter.items)
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            getString(R.string.cancel)
        ) { _: DialogInterface?, _: Int -> }

        val dialog = builder.create()
        dialog.show()
    }


}