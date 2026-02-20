package com.deniscerri.ytdl.ui.more.settings.advanced

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.adapter.SortableTextItemAdapter
import com.deniscerri.ytdl.ui.more.settings.SearchableSettingsFragment
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Fragment for advanced settings (YouTube player client, PO tokens, format importance order)
class AdvancedSettingsFragment : SearchableSettingsFragment() {
    override val title: Int = R.string.advanced

    @SuppressLint("RestrictedApi")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.advanced_preferences, rootKey)
        buildPreferenceList(preferenceScreen)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        val editor = prefs.edit()

        // Navigate to YouTube player client sub‑fragment
        findPreference<Preference>("yt_player_client")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.youtubePlayerClientFragment)
            false
        }

        // Navigate to generate PO tokens sub‑fragment
        findPreference<Preference>("generate_po_tokens")?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.generateYoutubePoTokensFragment)
            false
        }

        // Format importance for audio – allows reordering of format preferences.
        val formatImportanceAudio: Preference? = findPreference("format_importance_audio")
        val formatImportanceVideo: Preference? = findPreference("format_importance_video")

        formatImportanceAudio?.apply {
            title = "${getString(R.string.format_importance)} [${getString(R.string.audio)}]"
            val items = requireContext().resources.getStringArray(R.array.format_importance_audio)
            val itemValues = requireContext().resources.getStringArray(R.array.format_importance_audio_values).toSet()
            val prefAudio = prefs.getString("format_importance_audio", itemValues.joinToString(","))!!
            summary = prefAudio.split(",").mapIndexed { index, s ->
                "${index + 1}. ${items[itemValues.indexOf(s)]}"
            }.joinToString("\n")

            setOnPreferenceClickListener {
                val pref = prefs.getString("format_importance_audio", itemValues.joinToString(","))!!
                val prefArr = pref.split(",")
                val itms = itemValues.sortedBy { prefArr.indexOf(it) }.map {
                    Pair(it, items[itemValues.indexOf(it)])
                }.toMutableList()

                showFormatImportanceDialog(title.toString(), itms) { newList ->
                    editor.putString("format_importance_audio", newList.joinToString(",") { it.first }).apply()
                    summary = newList.map { it.second }.mapIndexed { index, s -> "${index + 1}. $s" }.joinToString("\n")
                }
                true
            }
        }

        // Format importance for video – same as audio.
        formatImportanceVideo?.apply {
            title = "${getString(R.string.format_importance)} [${getString(R.string.video)}]"
            val items = requireContext().resources.getStringArray(R.array.format_importance_video)
            val itemValues = requireContext().resources.getStringArray(R.array.format_importance_video_values).toSet()
            val prefVideo = prefs.getString("format_importance_video", itemValues.joinToString(","))!!
            summary = prefVideo.split(",").mapIndexed { index, s ->
                "${index + 1}. ${items[itemValues.indexOf(s)]}"
            }.joinToString("\n")

            setOnPreferenceClickListener {
                val pref = prefs.getString("format_importance_video", itemValues.joinToString(","))!!
                val prefArr = pref.split(",")
                val itms = itemValues.sortedBy { prefArr.indexOf(it) }.map {
                    Pair(it, items[itemValues.indexOf(it)])
                }.toMutableList()

                showFormatImportanceDialog(title.toString(), itms) { newList ->
                    editor.putString("format_importance_video", newList.joinToString(",") { it.first }).apply()
                    summary = newList.map { it.second }.mapIndexed { index, s -> "${index + 1}. $s" }.joinToString("\n")
                }
                true
            }
        }

        // Reset all preferences in this screen.
        findPreference<Preference>("reset_preferences")?.setOnPreferenceClickListener {
            UiUtil.showGenericConfirmDialog(requireContext(), getString(R.string.reset), getString(R.string.reset_preferences_in_screen)) {
                resetPreferences(editor, R.xml.advanced_preferences)
                requireActivity().recreate()
                val fragmentId = findNavController().currentDestination?.id
                findNavController().popBackStack(fragmentId!!, true)
                findNavController().navigate(fragmentId)
            }
            true
        }
    }

    // Shows a drag‑and‑drop dialog to reorder format importance items.
    private fun showFormatImportanceDialog(
        title: String,
        items: MutableList<Pair<String, String>>,
        onChange: (List<Pair<String, String>>) -> Unit
    ) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(title)

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

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }

        val linear = LinearLayout(requireActivity())
        linear.orientation = LinearLayout.VERTICAL

        val note = TextView(requireActivity())
        note.text = getString(R.string.format_importance_note)
        note.textSize = 16f
        note.setTypeface(note.typeface, Typeface.BOLD)
        note.setPadding(20, 20, 20, 20)
        linear.addView(note)

        val recycler = RecyclerView(requireContext())
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        linear.addView(recycler)

        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(recycler)

        builder.setView(linear)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            onChange(adapter.items)
        }
        builder.setNegativeButton(R.string.cancel, null)

        builder.create().show()
    }
}