package com.deniscerri.ytdlnis.ui.downloadcard

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs


class BackgroundToForegroundPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, pos: Float) {
        val scale = if (pos < 0) pos + 1f else abs(1f - pos)
        page.scaleX = scale
        page.scaleY = scale
        page.pivotX = page.width * 0.5f
        page.pivotY = page.height * 0.5f
        page.alpha = if (pos < -1f || pos > 1f) 0f else 1f - (scale - 1f)

        page.post {
            val wMeasureSpec = View.MeasureSpec.makeMeasureSpec(page.width, View.MeasureSpec.EXACTLY)
            val hMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            page.measure(wMeasureSpec, hMeasureSpec)
            page.minimumHeight = hMeasureSpec
        }
    }
}