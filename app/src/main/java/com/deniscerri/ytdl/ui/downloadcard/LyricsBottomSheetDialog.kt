package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem.fromUri
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.Extensions.convertToTimestamp
import com.deniscerri.ytdl.util.Extensions.toStringTimeStamp
import com.deniscerri.ytdl.util.extractors.LrcLibUtil
import com.deniscerri.ytdl.util.VideoPlayerUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class LyricsBottomSheetDialog(
    private val _item: DownloadItem? = null,
    private val urls: String? = null,
    private val onChanged: (() -> Unit)? = null
) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var player: ExoPlayer
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var item: DownloadItem

    private lateinit var lyricsList: RecyclerView
    private lateinit var lyricsProgress: ProgressBar
    private lateinit var lyricsMessage: TextView
    private lateinit var matchText: TextView
    private lateinit var seek: Slider
    private lateinit var positionText: TextView
    private lateinit var durationText: TextView
    private lateinit var playBtn: MaterialButton
    private lateinit var audioLoading: View
    private var streamFetching = true
    private lateinit var offsetSection: View
    private lateinit var offsetValue: TextView
    private lateinit var embedSwitch: MaterialSwitch
    private lateinit var saveLrcSwitch: MaterialSwitch

    private val adapter = LyricsAdapter { line -> seekToLine(line) }
    private var lyrics: LrcLibUtil.Lyrics? = null
    private var offset = 0L
    private var lastQuery: String? = null
    private var seeking = false
    private var lastUserScroll = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        super.onCreate(savedInstanceState)
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.lyrics_sheet, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())

        if (_item == null) {
            dismiss()
            return
        }
        item = _item
        offset = item.audioPreferences.lyricsOffset

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels
        }

        player = VideoPlayerUtil.buildPlayer(requireContext())
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        lyricsList = view.findViewById(R.id.lyrics_list)
        lyricsProgress = view.findViewById(R.id.lyrics_progress)
        lyricsMessage = view.findViewById(R.id.lyrics_message)
        matchText = view.findViewById(R.id.lyrics_match)
        seek = view.findViewById(R.id.lyrics_seek)
        positionText = view.findViewById(R.id.lyrics_position)
        durationText = view.findViewById(R.id.lyrics_duration)
        playBtn = view.findViewById(R.id.lyrics_play)
        audioLoading = view.findViewById(R.id.lyrics_audio_loading)
        offsetSection = view.findViewById(R.id.lyrics_offset_section)
        offsetValue = view.findViewById(R.id.offset_value)
        embedSwitch = view.findViewById(R.id.embed_lyrics)
        saveLrcSwitch = view.findViewById(R.id.save_lrc)

        setupList()
        setupPlayer(view)
        setupOffset(view)
        setupSwitches(view)

        view.findViewById<Button>(R.id.lyrics_search).setOnClickListener { showSearchDialog() }
        view.findViewById<Button>(R.id.lyrics_done).setOnClickListener { dismiss() }

        loadLyrics()
        loadStream()
    }

    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    private fun setupList() {
        lyricsList.layoutManager = LinearLayoutManager(requireContext())
        lyricsList.adapter = adapter
        lyricsList.itemAnimator = null
        //padding so the first and last lines can reach the middle of the card
        val pad = (130 * resources.displayMetrics.density).toInt()
        lyricsList.setPadding(0, pad, 0, pad)
        lyricsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) lastUserScroll = System.currentTimeMillis()
            }
        })
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun setupPlayer(view: View) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playBtn.setIconResource(if (isPlaying) R.drawable.ic_lyrics_pause else R.drawable.ic_lyrics_play)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateAudioLoading(playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY && player.duration > 0) {
                    seek.valueTo = player.duration.toFloat()
                    seek.isEnabled = true
                    durationText.text = player.duration.toStringTimeStamp()
                }
            }
        })

        playBtn.setOnClickListener { if (player.isPlaying) player.pause() else player.play() }
        view.findViewById<View>(R.id.lyrics_rewind).setOnClickListener {
            player.seekTo(max(0, player.currentPosition - 10_000))
        }
        view.findViewById<View>(R.id.lyrics_forward).setOnClickListener {
            val end = if (player.duration > 0) player.duration else Long.MAX_VALUE
            player.seekTo(min(end, player.currentPosition + 10_000))
        }

        seek.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                seeking = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                player.seekTo(slider.value.toLong())
                seeking = false
            }
        })
        seek.addOnChangeListener { _, value, fromUser ->
            if (fromUser) positionText.text = value.toLong().toStringTimeStamp()
        }

        durationText.text = item.duration
        seek.valueTo = max(1L, item.duration.convertToTimestamp()).toFloat()

        lifecycleScope.launch {
            while (isActive) {
                val position = player.currentPosition
                if (!seeking) {
                    positionText.text = position.toStringTimeStamp()
                    if (seek.isEnabled) seek.value = position.coerceIn(0, seek.valueTo.toLong()).toFloat()
                }
                updateCurrentLine(position)
                delay(100)
            }
        }
    }

    private fun updateAudioLoading(buffering: Boolean = false) {
        val loading = streamFetching || buffering
        audioLoading.isVisible = loading
        playBtn.visibility = if (loading) View.INVISIBLE else View.VISIBLE
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun loadStream() {
        updateAudioLoading()
        lifecycleScope.launch {
            try {
                val streams = withContext(Dispatchers.IO) {
                    if (urls.isNullOrEmpty()) resultViewModel.getStreamingUrlAndChapters(item.url).first
                    else urls.split("\n")
                }
                if (streams.isEmpty()) throw Exception("No Data found!")

                if (streams.size == 2) {
                    val factory = DefaultMediaSourceFactory(requireContext())
                    val sources: List<MediaSource> = streams.map { factory.createMediaSource(fromUri(Uri.parse(it))) }
                    player.setMediaSource(MergingMediaSource(sources[0], sources[1]))
                } else {
                    player.addMediaItem(fromUri(Uri.parse(streams[0])))
                }
                streamFetching = false
                player.prepare()
                player.play()
                updateAudioLoading(player.playbackState == Player.STATE_BUFFERING)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                streamFetching = false
                if (isAdded) {
                    updateAudioLoading()
                    Toast.makeText(requireContext(), R.string.lyrics_no_preview, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupOffset(view: View) {
        fun change(delta: Long) {
            offset = (offset + delta).coerceIn(-30_000L, 30_000L)
            item.audioPreferences.lyricsOffset = offset
            onChanged?.invoke()
            updateOffsetText()
            //force the highlighted line to be recalculated with the new offset
            adapter.setCurrent(-1)
            updateCurrentLine(player.currentPosition)
        }
        view.findViewById<View>(R.id.offset_minus_big).setOnClickListener { change(-500) }
        view.findViewById<View>(R.id.offset_minus).setOnClickListener { change(-100) }
        view.findViewById<View>(R.id.offset_plus).setOnClickListener { change(100) }
        view.findViewById<View>(R.id.offset_plus_big).setOnClickListener { change(500) }
        offsetValue.setOnClickListener { change(-offset) }
        updateOffsetText()
    }

    private fun updateOffsetText() {
        offsetValue.text = String.format(Locale.US, if (offset == 0L) "%.1fs" else "%+.1fs", offset / 1000.0)
    }

    private fun setupSwitches(view: View) {
        val prefs = item.audioPreferences
        embedSwitch.isChecked = prefs.embedLyrics
        saveLrcSwitch.isChecked = prefs.saveLrc
        embedSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.embedLyrics = checked
            onChanged?.invoke()
        }
        saveLrcSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.saveLrc = checked
            onChanged?.invoke()
        }
    }

    private fun loadLyrics(search: suspend () -> LrcLibUtil.Lyrics? = { findAutomatically() }) {
        lyricsProgress.isVisible = true
        lyricsMessage.isVisible = false
        lifecycleScope.launch {
            val found = try {
                withContext(Dispatchers.IO) { search() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            lyricsProgress.isVisible = false
            if (isAdded) showLyrics(found)
        }
    }

    private fun findAutomatically(): LrcLibUtil.Lyrics? {
        val chosen = item.audioPreferences.lyricsId
        if (chosen > 0) LrcLibUtil.byId(chosen)?.let { return it }
        return LrcLibUtil.find(
            item.title,
            item.author,
            "",
            (item.duration.convertToTimestamp() / 1000).toInt()
        )
    }

    private fun showLyrics(found: LrcLibUtil.Lyrics?) {
        lyrics = found
        val prefs = item.audioPreferences
        if (found == null) {
            prefs.lyricsId = 0
            adapter.submit(emptyList(), false)
            lyricsMessage.isVisible = true
            offsetSection.isVisible = false
            matchText.text = getString(R.string.lyrics_source)
            return
        }

        lyricsMessage.isVisible = false
        prefs.lyricsId = found.id
        //opening the sheet and finding lyrics means the user wants them
        if (!prefs.embedLyrics && !prefs.saveLrc) {
            embedSwitch.isChecked = true
        }
        onChanged?.invoke()

        matchText.text = "${found.artistName} - ${found.trackName}"
        offsetSection.isVisible = found.isSynced
        saveLrcSwitch.isEnabled = found.isSynced
        if (found.isSynced) {
            adapter.submit(found.synced.map { it.text.ifBlank { "♪" } }, true)
        } else {
            adapter.submit(found.plain.lines(), false)
            Toast.makeText(requireContext(), R.string.lyrics_unsynced, Toast.LENGTH_SHORT).show()
        }
        updateCurrentLine(player.currentPosition)
    }

    private fun updateCurrentLine(position: Long) {
        val synced = lyrics?.synced ?: return
        if (synced.isEmpty()) return
        //line is active once position reaches its timestamp + offset
        val target = position - offset
        var lo = 0
        var hi = synced.size - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (synced[mid].timeMs <= target) {
                idx = mid
                lo = mid + 1
            } else hi = mid - 1
        }
        if (idx == adapter.current) return
        adapter.setCurrent(idx)
        if (idx >= 0 && System.currentTimeMillis() - lastUserScroll > 3000) scrollToCenter(idx)
    }

    private fun scrollToCenter(position: Int) {
        val scroller = object : LinearSmoothScroller(requireContext()) {
            override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                return 80f / displayMetrics.densityDpi
            }
        }
        scroller.targetPosition = position
        lyricsList.layoutManager?.startSmoothScroll(scroller)
    }

    private fun seekToLine(index: Int) {
        val synced = lyrics?.synced ?: return
        if (index !in synced.indices) return
        player.seekTo(max(0, synced[index].timeMs + offset))
        player.play()
        lastUserScroll = 0
    }

    private fun showSearchDialog() {
        val input = EditText(requireContext()).apply {
            setText(lastQuery ?: "${item.author} ${item.title}".trim())
            setSelectAllOnFocus(true)
            maxLines = 1
        }
        val container = FrameLayout(requireContext()).apply {
            val m = (20 * resources.displayMetrics.density).toInt()
            setPadding(m, m / 2, m, 0)
            addView(input, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search)
            .setView(container)
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .setPositiveButton(R.string.search) { _, _ ->
                val query = input.text.toString().trim()
                if (query.isBlank()) return@setPositiveButton
                lastQuery = query
                lyricsProgress.isVisible = true
                lifecycleScope.launch {
                    val results = try {
                        withContext(Dispatchers.IO) { LrcLibUtil.search(query) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                    lyricsProgress.isVisible = false
                    if (!isAdded) return@launch
                    if (results.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.lyrics_not_found, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    showResultsDialog(results)
                }
            }
            .show()
    }

    private fun showResultsDialog(results: List<LrcLibUtil.Lyrics>) {
        val itemSeconds = (item.duration.convertToTimestamp() / 1000).toInt()
        val adapter = object : ArrayAdapter<LrcLibUtil.Lyrics>(requireContext(), 0, results) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LayoutInflater.from(context).inflate(R.layout.lyrics_search_result_item, parent, false)
                val r = getItem(position)!!
                row.findViewById<TextView>(R.id.result_title).text = r.trackName
                row.findViewById<TextView>(R.id.result_subtitle).text =
                    listOf(r.artistName, r.albumName).filter { it.isNotBlank() }.joinToString(" • ")

                val info = mutableListOf(getString(if (r.isSynced) R.string.lyrics_synced else R.string.lyrics_plain))
                if (r.durationSec > 0) {
                    var runtime = (r.durationSec * 1000L).toStringTimeStamp()
                    if (itemSeconds > 0) {
                        val diff = r.durationSec - itemSeconds
                        runtime += if (diff == 0) " (=)" else " (${if (diff > 0) "+" else ""}${diff}s)"
                    }
                    info.add(runtime)
                }
                row.findViewById<TextView>(R.id.result_info).text = info.joinToString(" • ")

                row.findViewById<TextView>(R.id.result_preview).apply {
                    val preview = (if (r.isSynced) r.synced.map { it.text } else r.plain.lines())
                        .filter { it.isNotBlank() }.take(2).joinToString("\n")
                    text = preview
                    isVisible = preview.isNotBlank()
                }
                return row
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lyrics)
            .setAdapter(adapter) { _, which -> showLyrics(results[which]) }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private class LyricsAdapter(
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<LyricsAdapter.Holder>() {
        private var lines: List<String> = emptyList()
        private var highlight = false
        var current = -1
            private set

        class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newLines: List<String>, highlightCurrent: Boolean) {
            lines = newLines
            highlight = highlightCurrent
            current = -1
            notifyDataSetChanged()
        }

        fun setCurrent(index: Int) {
            val old = current
            current = index
            if (old >= 0) notifyItemChanged(old, ANIMATE)
            if (index >= 0) notifyItemChanged(index, ANIMATE)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val tv = LayoutInflater.from(parent.context).inflate(R.layout.lyrics_line_item, parent, false) as TextView
            return Holder(tv).also { h ->
                tv.setOnClickListener {
                    val pos = h.bindingAdapterPosition
                    if (highlight && pos != RecyclerView.NO_POSITION) onClick(pos)
                }
            }
        }

        override fun onBindViewHolder(holder: Holder, position: Int) = bind(holder, position, false)

        override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) =
            bind(holder, position, payloads.isNotEmpty())

        private fun bind(holder: Holder, position: Int, animate: Boolean) {
            val tv = holder.text
            tv.text = lines[position]
            val active = !highlight || position == current
            val alpha = if (active) 1f else 0.4f
            val scale = if (highlight && active) 1.1f else 1f
            tv.animate().cancel()
            if (animate) {
                tv.animate().alpha(alpha).scaleX(scale).scaleY(scale).setDuration(250).start()
            } else {
                tv.alpha = alpha
                tv.scaleX = scale
                tv.scaleY = scale
            }
        }

        override fun getItemCount() = lines.size

        companion object {
            private const val ANIMATE = "animate"
        }
    }
}
