package com.deniscerri.ytdl.util

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

/**
 * Long press a card and keep dragging to select every card between it and the finger.
 * The adapter's own long click still toggles the first card, this only spreads that card's new state over the range.
 * Auto scrolls when the finger gets near the top or bottom edge
 */
class DragSelectTouchListener private constructor(private val recyclerView: RecyclerView) : RecyclerView.OnItemTouchListener {

    interface Selectable {
        fun isItemSelected(position: Int): Boolean
        fun setItemSelected(position: Int, selected: Boolean)
    }

    companion object {
        /** true while a drag selection runs, swipe callbacks check it so they don't swipe the card away */
        var isDragging = false
            private set

        /** the recycler view's adapter must implement [Selectable] */
        fun attach(recyclerView: RecyclerView) {
            recyclerView.addOnItemTouchListener(DragSelectTouchListener(recyclerView))
        }
    }

    private var active = false
    private var anchor = RecyclerView.NO_POSITION
    private var current = RecyclerView.NO_POSITION
    // read on the first move, the card's long click may run after our long press callback
    private var anchorSelected: Boolean? = null
    // state of each item before the drag touched it, so moving back restores it
    private val original = mutableMapOf<Int, Boolean>()
    private var lastX = 0f
    private var lastY = 0f
    private var scrollSpeed = 0

    private val density = recyclerView.resources.displayMetrics.density
    private val edgeSize = 64 * density
    private val maxScrollSpeed = 20 * density

    private val gestureDetector = GestureDetector(recyclerView.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (recyclerView.adapter !is Selectable) return
            val position = positionAt(e.x, e.y)
            if (position == RecyclerView.NO_POSITION) return
            anchor = position
            current = position
            anchorSelected = null
            original.clear()
            lastX = e.x
            lastY = e.y
            active = true
            isDragging = true
            // stop view pagers and bottom sheets from taking over the vertical drag
            recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
        }
    })

    private val autoScroll = object : Runnable {
        override fun run() {
            if (!active || scrollSpeed == 0) return
            recyclerView.scrollBy(0, scrollSpeed)
            updateSelection()
            recyclerView.postOnAnimation(this)
        }
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        // a gesture can end without us seeing it when another listener intercepted it
        if (e.actionMasked == MotionEvent.ACTION_DOWN) stop()
        if (!active) {
            gestureDetector.onTouchEvent(e)
            return false
        }
        return when (e.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stop()
                false
            }
            else -> true
        }
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                lastX = e.x
                lastY = e.y
                updateSelection()
                updateAutoScroll()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stop()
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // a child took the touch stream, we won't get the rest of the gesture
        if (disallowIntercept) stop()
    }

    private fun positionAt(x: Float, y: Float): Int {
        val child = recyclerView.findChildViewUnder(
            x.coerceIn(0f, recyclerView.width - 1f),
            y.coerceIn(0f, recyclerView.height - 1f)
        ) ?: return RecyclerView.NO_POSITION
        return recyclerView.getChildAdapterPosition(child)
    }

    private fun updateSelection() {
        if (!active) return
        val adapter = recyclerView.adapter as? Selectable ?: return
        val position = positionAt(lastX, lastY)
        if (position == RecyclerView.NO_POSITION || position == current) return
        val state = anchorSelected ?: adapter.isItemSelected(anchor).also { anchorSelected = it }

        val newRange = min(anchor, position)..max(anchor, position)
        for (p in min(anchor, current)..max(anchor, current)) {
            if (p !in newRange) original.remove(p)?.let { adapter.setItemSelected(p, it) }
        }
        for (p in newRange) {
            if (p == anchor || p in original) continue
            original[p] = adapter.isItemSelected(p)
            adapter.setItemSelected(p, state)
        }
        current = position
    }

    private fun updateAutoScroll() {
        val y = lastY.coerceIn(0f, recyclerView.height.toFloat())
        val speed = when {
            y < edgeSize -> -((edgeSize - y) / edgeSize * maxScrollSpeed)
            y > recyclerView.height - edgeSize -> (y - (recyclerView.height - edgeSize)) / edgeSize * maxScrollSpeed
            else -> 0f
        }.toInt()
        val wasIdle = scrollSpeed == 0
        scrollSpeed = speed
        if (wasIdle && speed != 0) {
            recyclerView.removeCallbacks(autoScroll)
            recyclerView.postOnAnimation(autoScroll)
        }
    }

    private fun stop() {
        active = false
        isDragging = false
        anchor = RecyclerView.NO_POSITION
        current = RecyclerView.NO_POSITION
        original.clear()
        scrollSpeed = 0
        recyclerView.removeCallbacks(autoScroll)
    }
}
