package com.deniscerri.ytdlnis.ui.downloadcard

import android.view.View
import androidx.viewpager2.widget.ViewPager2


class BackgroundToForegroundPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, pos: Float) {

        page.post {
            val wMeasureSpec = View.MeasureSpec.makeMeasureSpec(page.width, View.MeasureSpec.EXACTLY)
            val hMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            page.measure(wMeasureSpec, hMeasureSpec)
            page.minimumHeight = hMeasureSpec
        }
    }
}