package com.deniscerri.ytdl.ui.downloadcard

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.setPadding
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.MusicMetadata
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import com.squareup.picasso.Picasso

/**
 * Binds the music metadata section of the audio download card: the mode switch, the editable
 * song fields with cover art, the alternative matches picker and the manual search dialog.
 *
 * The card never fetches by itself, it only reports intent through its callbacks and renders
 * whatever state it is given.
 */
class MusicMetadataCard(
    root: View,
    private val onModeChanged: (enabled: Boolean) -> Unit,
    private val onMetadataChanged: (MusicMetadata) -> Unit,
    private val onSearchRequested: (artist: String, song: String) -> Unit
) {
    private val context: Context = root.context

    private val header: MaterialCardView = root.findViewById(R.id.music_header)
    private val switch: MaterialSwitch = root.findViewById(R.id.music_switch)
    private val status: TextView = root.findViewById(R.id.music_status)
    private val progress: CircularProgressIndicator = root.findViewById(R.id.music_progress)
    private val content: View = root.findViewById(R.id.music_content)
    private val cover: ShapeableImageView = root.findViewById(R.id.music_cover)
    private val matchesChip: Chip = root.findViewById(R.id.music_matches)
    private val searchChip: Chip = root.findViewById(R.id.music_manual_search)

    private val songField: EditText = field(root, R.id.music_song_textinput)
    private val artistField: EditText = field(root, R.id.music_artist_textinput)
    private val albumField: EditText = field(root, R.id.music_album_textinput)
    private val yearField: EditText = field(root, R.id.music_year_textinput)

    private var matches: List<MusicMetadata> = emptyList()
    private var selectedMatch = 0
    private var current = MusicMetadata()
    private var binding = false

    val isEnabled: Boolean get() = switch.isChecked

    init {
        header.setOnClickListener { switch.isChecked = !switch.isChecked }
        switch.setOnCheckedChangeListener { _, checked ->
            content.isVisible(checked)
            if (binding) return@setOnCheckedChangeListener
            if (!checked) showIdle()
            onModeChanged(checked)
        }

        songField.onTextChanged { current.title = it; publish() }
        artistField.onTextChanged { current.artist = it; publish() }
        albumField.onTextChanged { current.album = it; publish() }
        yearField.onTextChanged { current.year = it; publish() }

        matchesChip.setOnClickListener { showMatchPicker() }
        searchChip.setOnClickListener { showSearchDialog() }
    }

    // ── State rendering ────────────────────────────────────────────────────────

    /** Restores the switch without emitting a mode change. */
    fun setChecked(checked: Boolean) {
        if (switch.isChecked == checked) return
        binding = true
        switch.isChecked = checked
        content.isVisible(checked)
        binding = false
    }

    fun showLoading() {
        progress.isVisible(true)
        status.text = context.getString(R.string.searching_song)
        matchesChip.isEnabled = false
    }

    /** Shows the resolved song, with [all] offered as alternative matches. */
    fun showMetadata(metadata: MusicMetadata, all: List<MusicMetadata> = emptyList()) {
        matches = all
        selectedMatch = all.indexOfFirst { it.displayName() == metadata.displayName() }.coerceAtLeast(0)
        progress.isVisible(false)
        matchesChip.isVisible(all.size > 1)
        matchesChip.isEnabled = true
        status.text = metadata.details().ifBlank { context.getString(R.string.music_mode_summary) }
        bindFields(metadata)
    }

    /** No API match: keeps the parsed guess editable so the user can correct it. */
    fun showNotFound(guess: MusicMetadata) {
        matches = emptyList()
        progress.isVisible(false)
        matchesChip.isVisible(false)
        status.text = context.getString(R.string.song_not_found)
        bindFields(guess)
    }

    private fun showIdle() {
        matches = emptyList()
        progress.isVisible(false)
        status.text = context.getString(R.string.music_mode_summary)
    }

    private fun bindFields(metadata: MusicMetadata) {
        binding = true
        current = metadata.copy()
        songField.setText(metadata.title)
        artistField.setText(metadata.artist)
        albumField.setText(metadata.album)
        yearField.setText(metadata.year)
        loadCover(metadata.coverUrl)
        binding = false
        publish()
    }

    private fun loadCover(url: String) {
        if (url.isBlank()) {
            cover.setImageResource(R.drawable.ic_music)
            cover.setPadding(COVER_PLACEHOLDER_PADDING)
        } else {
            cover.setPadding(0)
            Picasso.get().load(url).placeholder(R.drawable.ic_music).into(cover)
        }
    }

    private fun publish() {
        if (!binding) onMetadataChanged(current.copy())
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────

    private fun showMatchPicker() {
        if (matches.size <= 1) return
        val labels = matches.map { match ->
            listOfNotNull(match.displayName(), match.details().ifBlank { null }).joinToString("\n")
        }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.other_matches)
            .setSingleChoiceItems(labels, selectedMatch) { dialog, index ->
                selectedMatch = index
                showMetadata(matches[index], matches)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSearchDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_music_search, null)
        val artistInput = field(view, R.id.music_search_artist)
        val songInput = field(view, R.id.music_search_song)
        artistInput.setText(current.artist)
        songInput.setText(current.title)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.search_song)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.search) { _, _ ->
                onSearchRequested(artistInput.text.toString().trim(), songInput.text.toString().trim())
            }
            .show()
    }

    // ── View helpers ───────────────────────────────────────────────────────────

    private fun field(root: View, id: Int): EditText =
        root.findViewById<TextInputLayout>(id).editText!!

    private fun View.isVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun EditText.onTextChanged(action: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!binding) action(s.toString().trim())
            }
        })
    }

    companion object {
        private const val COVER_PLACEHOLDER_PADDING = 30
    }
}
