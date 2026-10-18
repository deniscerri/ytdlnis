package com.deniscerri.ytdl.ui.adapter

import android.content.Context
import android.util.Log
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * A list fed by paging that shrinks while a wrap_content RecyclerView is being measured can throw
 * "Inconsistency detected. Invalid view holder adapter position" from inside the layout pass.
 * The data is already settled by the time the next layout runs, so skipping the broken pass is safe
 */
class SafeGridLayoutManager(context: Context, spanCount: Int) : GridLayoutManager(context, spanCount) {

    override fun supportsPredictiveItemAnimations(): Boolean = false

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            Log.e("SafeGridLayoutManager", "Skipped a layout pass with stale adapter positions", e)
        }
    }
}
