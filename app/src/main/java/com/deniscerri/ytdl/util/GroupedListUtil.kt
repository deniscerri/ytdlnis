package com.deniscerri.ytdl.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/** Material 3 Expressive segmented lists: rows of a group share a tonal container with large outer and small inner corners. */
object GroupedListUtil {
    private const val OUTER_CORNER_DP = 20f
    private const val INNER_CORNER_DP = 4f
    private const val SIDE_MARGIN_DP = 16
    private const val GAP_DP = 2

    fun background(context: Context, first: Boolean, last: Boolean): Drawable {
        val d = context.resources.displayMetrics.density
        val top = (if (first) OUTER_CORNER_DP else INNER_CORNER_DP) * d
        val bottom = (if (last) OUTER_CORNER_DP else INNER_CORNER_DP) * d
        val shape = ShapeAppearanceModel.builder()
            .setTopLeftCornerSize(top).setTopRightCornerSize(top)
            .setBottomLeftCornerSize(bottom).setBottomRightCornerSize(bottom)
            .build()
        val fill = MaterialShapeDrawable(shape).apply {
            fillColor = ColorStateList.valueOf(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0))
        }
        val ripple = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorControlHighlight, 0)
        return RippleDrawable(ColorStateList.valueOf(ripple), fill, MaterialShapeDrawable(shape))
    }

    /** Styles the currently visible [views] (siblings in a vertical LinearLayout) as one group. */
    fun applyToViews(views: List<View>) {
        val visible = views.filter { it.visibility == View.VISIBLE }
        visible.forEachIndexed { i, v ->
            val d = v.resources.displayMetrics.density
            v.background = background(v.context, i == 0, i == visible.lastIndex)
            (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.marginStart = (SIDE_MARGIN_DP * d).toInt()
                it.marginEnd = (SIDE_MARGIN_DP * d).toInt()
                it.topMargin = if (i == 0) 0 else (GAP_DP * d).toInt()
                v.layoutParams = it
            }
        }
    }

    /** Groups the preferences between categories. Custom card/button preferences stay as they are. */
    class PreferenceDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val density = context.resources.displayMetrics.density
        private val side = (SIDE_MARGIN_DP * density).toInt()
        private val halfGap = (GAP_DP * density / 2).toInt()
        private val ungroupedLayouts = setOf(
            R.layout.preference_danger_button,
            R.layout.preference_temporary_files_summary,
            R.layout.preference_update_channel,
            R.layout.preference_update_status_card
        )

        private fun isGrouped(adapter: PreferenceGroupAdapter, pos: Int): Boolean {
            if (pos !in 0 until adapter.itemCount) return false
            val p = adapter.getItem(pos) ?: return false
            return p !is PreferenceCategory && p.layoutResource !in ungroupedLayouts
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
            val pos = parent.getChildAdapterPosition(view)
            if (pos == RecyclerView.NO_POSITION) return
            when {
                isGrouped(adapter, pos) -> outRect.set(side, halfGap, side, halfGap)
                adapter.getItem(pos) is PreferenceCategory -> outRect.set(side, 0, side, 0) // keep titles aligned with the rows
            }
        }

        // Backgrounds are (re)applied while drawing: PreferenceViewHolder restores its original background on every
        // bind, and a row's first/last state changes when a neighbour is shown or hidden.
        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val pos = parent.getChildAdapterPosition(child)
                if (pos == RecyclerView.NO_POSITION || !isGrouped(adapter, pos)) continue
                val key = (if (!isGrouped(adapter, pos - 1)) 1 else 0) or (if (!isGrouped(adapter, pos + 1)) 2 else 0)
                val applied = child.getTag(R.id.grouped_list_background) as? Pair<*, *>
                if (applied?.first == key && applied.second === child.background) continue
                val bg = background(child.context, key and 1 != 0, key and 2 != 0)
                child.background = bg
                child.setTag(R.id.grouped_list_background, key to bg)
            }
        }
    }
}
