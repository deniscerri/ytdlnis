package com.deniscerri.ytdl.ui.downloads

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.FileUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ActiveQueuedDownloadsFragment : Fragment() {
    private lateinit var downloadViewModel: DownloadViewModel

    private lateinit var root: CoordinatorLayout
    private lateinit var activeContainer: View
    private lateinit var sheet: ViewGroup
    private lateinit var sheetSubtitle: TextView
    private lateinit var queuedContainer: View
    private lateinit var reorderButton: MaterialButton
    private lateinit var pauseResumeButton: MaterialButton
    private lateinit var emptyQueueFab: ExtendedFloatingActionButton
    private lateinit var behavior: BottomSheetBehavior<ViewGroup>

    // -1 until the first value arrives. Placing the sheet before everything is known is what made it jump around
    private var activeCount = -1
    private var queueCount = -1
    private var cardHeight = 0
    private var placed = false
    private var fabWanted = false
    private var retryScheduled = false
    private var startTime = 0L
    private var fileSizeText = ""
    private var pausedAllState = DownloadViewModel.PausedAllDownloadsState.HIDDEN

    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val sheetBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_downloads, container, false)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]

        root = view.findViewById(R.id.downloads_root)
        activeContainer = view.findViewById(R.id.active_container)
        sheet = view.findViewById(R.id.queue_sheet)
        sheetSubtitle = view.findViewById(R.id.sheet_subtitle)
        queuedContainer = view.findViewById(R.id.queued_container)
        reorderButton = view.findViewById(R.id.sheet_reorder)
        pauseResumeButton = view.findViewById(R.id.sheet_pause_resume)
        emptyQueueFab = view.findViewById(R.id.empty_queue_fab)

        // the tabs reuse this same fragment instance, so state from a previous view must not leak into the new one.
        // A leftover placed = true skipped the fade in and left the new sheet invisible
        activeCount = -1
        queueCount = -1
        cardHeight = 0
        placed = false
        fabWanted = false
        retryScheduled = false
        fileSizeText = ""
        pausedAllState = DownloadViewModel.PausedAllDownloadsState.HIDDEN
        sheetBackCallback.isEnabled = false
        startTime = SystemClock.elapsedRealtime()

        // stays invisible and off screen until the first real measurement, then fades in already in place
        sheet.alpha = 0f
        behavior = BottomSheetBehavior.from(sheet)
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                sheetBackCallback.isEnabled = newState == BottomSheetBehavior.STATE_EXPANDED
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, sheetBackCallback)

        // tapping the header toggles the sheet
        view.findViewById<View>(R.id.sheet_header).setOnClickListener {
            if (!behavior.isDraggable) return@setOnClickListener
            behavior.state = if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_EXPANDED
        }

        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSheet() }
        sheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateSheet() }

        // the first queue card tells how tall the peek has to be. Running cards changing also moves the sheet
        // attached views share one observer with the whole window, so this has to be removed with the view
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val height = sheet.findViewById<RecyclerView>(R.id.download_recyclerview)?.getChildAt(0)?.height ?: 0
            if (height > 0 && height != cardHeight) cardHeight = height
            updateSheet()
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

        reorderButton.setOnClickListener {
            (childFragmentManager.findFragmentById(R.id.queued_container) as? QueuedDownloadsFragment)?.toggleDragHandles()
        }

        val pauseResumeClick = View.OnClickListener {
            lifecycleScope.launch {
                if (pausedAllState == DownloadViewModel.PausedAllDownloadsState.RESUME) {
                    downloadViewModel.resumeAllDownloads()
                } else {
                    downloadViewModel.pauseAllDownloads()
                }
            }
        }
        pauseResumeButton.setOnClickListener(pauseResumeClick)
        emptyQueueFab.setOnClickListener(pauseResumeClick)

        downloadViewModel.pausedAllDownloads.observe(viewLifecycleOwner) { value ->
            pausedAllState = value
            applyPauseResumeState()
            updateSheet()
        }

        lifecycleScope.launch {
            downloadViewModel.activePausedDownloadsCount.collectLatest {
                activeCount = it
                updateSheet()
            }
        }

        lifecycleScope.launch {
            downloadViewModel.queuedDownloadsCount.collectLatest {
                queueCount = it
                reorderButton.isVisible = it > 1
                queuedContainer.isVisible = it > 0
                updateSubtitle()
                applyPauseResumeState()
                updateSheet()
                updateFileSize()
            }
        }
    }

    override fun onDestroyView() {
        globalLayoutListener?.let {
            val observer = root.viewTreeObserver
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(it)
        }
        globalLayoutListener = null
        super.onDestroyView()
    }

    /**
     * Pause/resume all lives in the queue sheet header. With nothing in the queue there is no sheet,
     * so the same action is a floating button over the running cards instead
     */
    private fun applyPauseResumeState() {
        val actionable = pausedAllState == DownloadViewModel.PausedAllDownloadsState.PAUSE ||
                pausedAllState == DownloadViewModel.PausedAllDownloadsState.RESUME
        val processing = pausedAllState == DownloadViewModel.PausedAllDownloadsState.PROCESSING

        if (actionable) {
            val resume = pausedAllState == DownloadViewModel.PausedAllDownloadsState.RESUME
            val label = if (resume) R.string.resume_all else R.string.pause_all
            val icon = if (resume) R.drawable.exomedia_ic_play_arrow_white else R.drawable.exomedia_ic_pause_white
            pauseResumeButton.setText(label)
            pauseResumeButton.setIconResource(icon)
            emptyQueueFab.setText(label)
            emptyQueueFab.setIconResource(icon)
        }
        if (actionable || processing) {
            pauseResumeButton.isEnabled = actionable
            emptyQueueFab.isEnabled = actionable
        }

        val wanted = actionable || processing
        pauseResumeButton.isVisible = wanted && queueCount > 0
        fabWanted = wanted && queueCount == 0
        if (fabWanted) emptyQueueFab.show() else emptyQueueFab.hide()
    }

    private fun updateSubtitle() {
        val count = queueCount.coerceAtLeast(0)
        val items = resources.getQuantityString(R.plurals.queue_items, count, count)
        sheetSubtitle.text = if (fileSizeText.isBlank() || count == 0) items else "$items - ~ $fileSizeText"
    }

    private suspend fun updateFileSize() {
        val size = withContext(Dispatchers.IO) {
            if (queueCount == 0) "?" else FileUtil.convertFileSize(downloadViewModel.getQueuedCollectedFileSize())
        }
        if (!isAdded) return
        fileSizeText = if (size == "?") "" else size
        updateSubtitle()
    }

    private fun updateSheet() {
        if (!::behavior.isInitialized || !isAdded || view == null) return
        val parentHeight = root.height
        if (parentHeight == 0 || activeCount < 0 || queueCount < 0) return

        // an empty queue has no sheet, the pause/resume button is a floating button then
        if (queueCount == 0) {
            if (placed) {
                behavior.isHideable = true
                behavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
            // keeps the last running card from sitting under the floating button
            setActiveInset(if (fabWanted) (FAB_CLEARANCE_DP * resources.displayMetrics.density).toInt() else 0)
            return
        }

        val sheetHeight = sheet.height
        if (sheetHeight == 0) return

        val settled = SystemClock.elapsedRealtime() - startTime >= SETTLE_MS
        // the queue cards and the running cards load on their own, wait for them so the peek is only worked out once
        val activeBottom = activeContentBottom(parentHeight, settled)
        if (activeBottom == null || (queueCount > 0 && cardHeight == 0 && !settled)) {
            scheduleRetry()
            return
        }

        val density = resources.displayMetrics.density
        val card = if (cardHeight > 0) cardHeight else (96 * density).toInt()
        // everything above the queue cards: header, reorder button
        val headerBlock = queuedContainer.top.takeIf { it > 0 } ?: (96 * density).toInt()
        val minimumPeek = (headerBlock + card * 1.5f).toInt()

        // the sheet starts under the last running card, ACTIVE_SHEET_GAP_DP below it. It only goes down to
        // one and a half queue cards once the running cards fill the screen
        val gap = (ACTIVE_SHEET_GAP_DP * density).toInt()
        val desired = (parentHeight - (activeBottom + gap)).coerceAtLeast(minimumPeek)
        // never taller than what the queue actually fills, otherwise there is empty space inside the sheet
        val peek = desired.coerceAtMost(sheetHeight)

        behavior.isHideable = false
        behavior.isDraggable = sheetHeight > peek
        if (!placed) {
            // first placement has no animation, the sheet just fades in where it belongs
            behavior.setPeekHeight(peek, false)
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            sheet.animate().alpha(1f).setDuration(180).start()
            placed = true
        } else {
            if (behavior.peekHeight != peek) behavior.setPeekHeight(peek, true)
            if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        setActiveInset(peek)
    }

    private fun scheduleRetry() {
        if (retryScheduled) return
        retryScheduled = true
        root.postDelayed({
            retryScheduled = false
            updateSheet()
        }, SETTLE_MS)
    }

    /**
     * Where the running cards end, measured from the top of the screen area. A long list counts as filling all of it.
     * Null while the list has not been filled and laid out yet, unless we have waited long enough
     */
    private fun activeContentBottom(parentHeight: Int, settled: Boolean): Int? {
        if (activeCount == 0) return (parentHeight * 0.38f).toInt() // room for the empty state
        val list = activeContainer.findViewById<RecyclerView>(R.id.download_recyclerview)
        val items = list?.adapter?.itemCount ?: 0
        if (list == null || items != activeCount || list.childCount == 0) {
            return if (settled) parentHeight else null
        }
        if (list.childCount < items) return parentHeight
        return (0 until list.childCount).maxOf { list.getChildAt(it).bottom }
    }

    /** Lets the last running cards scroll up above the collapsed sheet */
    private fun setActiveInset(bottom: Int) {
        val list = activeContainer.findViewById<RecyclerView>(R.id.download_recyclerview) ?: return
        if (list.paddingBottom != bottom) {
            list.clipToPadding = false
            list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, bottom)
        }
    }

    companion object {
        /** Space between the last running card and the top edge of the collapsed queue sheet */
        private const val ACTIVE_SHEET_GAP_DP = 16

        /** Room left under the running cards for the floating pause/resume button */
        private const val FAB_CLEARANCE_DP = 88

        /** How long to wait for the lists to load before placing the sheet with whatever is known */
        private const val SETTLE_MS = 400L
    }
}
