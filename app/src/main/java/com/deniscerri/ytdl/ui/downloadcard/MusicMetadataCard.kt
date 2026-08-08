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
import com.deniscerri.ytdl.util.extractors.music.MusicMetadataUtil
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.squareup.picasso.Picasso

/**
 * Binds the music metadata section of the audio download card: the mode switch, the editable
 * song fields with cover art, the alternative matches picker and the manual search dialog.
 *
 * The song, the artist, the album and the year are what identifies a track, so they stay on
 * screen. Everything else a catalogue resolves is a tag the user rarely corrects, and lives
 * behind the details chip.
 *
 * The card never fetches by itself, it only reports intent through its callbacks and renders
 * whatever state it is given.
 */
class MusicMetadataCard(
    private val root: View,
    private val onModeChanged: (enabled: Boolean) -> Unit,
    private val onMetadataChanged: (metadata: MusicMetadata, byUser: Boolean) -> Unit,
    private val onMatchSelected: (index: Int) -> Unit,
    private val onSearchRequested: (artist: String, song: String, providerId: String?) -> Unit
) {
    private val context: Context = root.context

    private val header: MaterialCardView = root.findViewById(R.id.music_header)
    private val switch: MaterialSwitch = root.findViewById(R.id.music_switch)
    private val status: TextView = root.findViewById(R.id.music_status)
    private val progress: CircularProgressIndicator = root.findViewById(R.id.music_progress)
    private val content: View = root.findViewById(R.id.music_content)
    private val cover: ShapeableImageView = root.findViewById(R.id.music_cover)
    private val extra: View = root.findViewById(R.id.music_extra)
    private val matchesChip: Chip = root.findViewById(R.id.music_matches)
    private val searchChip: Chip = root.findViewById(R.id.music_manual_search)
    private val detailsChip: Chip = root.findViewById(R.id.music_details)

    private var matches: List<MusicMetadata> = emptyList()
    private var selectedMatch = 0
    private var current = MusicMetadata()
    private var extraShown = false

    /** Set while the card writes into its own views, so echoed edits are not read back as the users. */
    private var binding = false

    /**
     * Every editable tag, in layout order. One entry is all it takes to add a tag: the pair of
     * accessors keeps the view and [current] in sync in both directions.
     */
    private val fields = listOf(
        Field(R.id.music_song_textinput, { it.title }) { current.title = it },
        Field(R.id.music_artist_textinput, { it.artist }) { current.artist = it },
        Field(R.id.music_album_textinput, { it.album }) { current.album = it },
        Field(R.id.music_year_textinput, { it.year }) { current.year = it },
        Field(R.id.music_album_artist_textinput, { it.albumArtist }) { current.albumArtist = it },
        Field(R.id.music_genre_textinput, { it.genre }) { current.genre = it },
        Field(R.id.music_label_textinput, { it.label }) { current.label = it },
        Field(R.id.music_track_textinput, { it.trackNumber }) { current.trackNumber = it },
        Field(R.id.music_track_total_textinput, { it.trackTotal }) { current.trackTotal = it },
        Field(R.id.music_disc_textinput, { it.discNumber }) { current.discNumber = it },
        Field(R.id.music_isrc_textinput, { it.isrc }) { current.isrc = it }
    )

    val isEnabled: Boolean get() = switch.isChecked

    init {
        header.setOnClickListener { switch.isChecked = !switch.isChecked }
        switch.setOnCheckedChangeListener { _, checked ->
            content.isVisible(checked)
            if (binding) return@setOnCheckedChangeListener
            if (!checked) showIdle()
            onModeChanged(checked)
        }

        matchesChip.setOnClickListener { showMatchPicker() }
        searchChip.setOnClickListener { showSearchDialog() }
        detailsChip.setOnClickListener { showExtra(!extraShown) }
        showExtra(false)
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

    /** The video info is still being fetched, the lookup runs as soon as it lands. */
    fun showWaiting() {
        progress.isVisible(true)
        status.text = context.getString(R.string.waiting_for_video_info)
        matchesChip.isEnabled = false
    }

    /** Shows [selected] out of the resolved [all], which the picker offers as alternatives. */
    fun showMetadata(all: List<MusicMetadata>, selected: Int) {
        val metadata = all.getOrNull(selected) ?: return
        matches = all
        selectedMatch = selected
        progress.isVisible(false)
        matchesChip.isVisible(all.size > 1)
        matchesChip.isEnabled = true
        status.text = metadata.details().ifBlank { context.getString(R.string.music_mode_summary) }
        bindFields(metadata)
    }

    /** A settled song with no alternatives, restored from the download item. */
    fun showMetadata(metadata: MusicMetadata) = showMetadata(listOf(metadata), 0)

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

    private fun showExtra(show: Boolean) {
        extraShown = show
        extra.isVisible(show)
        detailsChip.setText(if (show) R.string.fewer_details else R.string.more_details)
        detailsChip.setChipIconResource(
            if (show) R.drawable.baseline_expand_less_24 else R.drawable.baseline_expand_more_24
        )
    }

    private fun bindFields(metadata: MusicMetadata) {
        binding = true
        current = metadata.copy()
        fields.forEach { it.show(metadata) }
        loadCover(metadata.coverUrl)
        binding = false
        //rendered state is never the users own, only typing in a field is
        onMetadataChanged(current.copy(), false)
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

    // ── Dialogs ────────────────────────────────────────────────────────────────

    private fun showMatchPicker() {
        if (matches.size <= 1) return
        val labels = matches.map { match ->
            listOfNotNull(match.displayName(), match.details().ifBlank { null }).joinToString("\n")
        }.toTypedArray()

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.other_matches)
            .setSingleChoiceItems(labels, selectedMatch) { dialog, index ->
                onMatchSelected(index)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSearchDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_music_search, null)
        val artistInput = input(view, R.id.music_search_artist)
        val songInput = input(view, R.id.music_search_song)
        artistInput.setText(current.artist)
        songInput.setText(current.title)

        //the catalogues, preceded by the default of consulting them in order
        val providerIds = listOf(null) + MusicMetadataUtil.catalogues.map { (id, _) -> id }
        val providerInput = input(view, R.id.music_search_provider) as MaterialAutoCompleteTextView
        providerInput.setSimpleItems(
            (listOf(context.getString(R.string.all_sources)) + MusicMetadataUtil.catalogues.map { (_, name) -> name })
                .toTypedArray()
        )
        var provider = 0
        providerInput.setText(context.getString(R.string.all_sources), false)
        providerInput.setOnItemClickListener { _, _, index, _ -> provider = index }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.search_song)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.search) { _, _ ->
                onSearchRequested(
                    artistInput.text.toString().trim(),
                    songInput.text.toString().trim(),
                    providerIds[provider]
                )
            }
            .show()
    }

    // ── View helpers ───────────────────────────────────────────────────────────

    /** One editable tag: renders it from a metadata and writes edits straight back into [current]. */
    private inner class Field(
        id: Int,
        private val read: (MusicMetadata) -> String,
        private val write: (String) -> Unit
    ) {
        private val view: EditText = input(root, id)

        init {
            view.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (binding) return
                    write(s.toString().trim())
                    onMetadataChanged(current.copy(), true)
                }
            })
        }

        fun show(metadata: MusicMetadata) = view.setText(read(metadata))
    }

    private fun input(root: View, id: Int): EditText =
        root.findViewById<TextInputLayout>(id).editText!!

    private fun View.isVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    companion object {
        private const val COVER_PLACEHOLDER_PADDING = 30
    }
}
