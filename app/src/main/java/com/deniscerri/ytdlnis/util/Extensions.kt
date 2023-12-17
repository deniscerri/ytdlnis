package com.deniscerri.ytdlnis.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.net.Uri
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.neo.highlight.core.Highlight
import com.neo.highlight.util.listener.HighlightTextWatcher
import com.neo.highlight.util.scheme.ColorScheme
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import java.io.File
import java.util.regex.Pattern


object Extensions {
    fun dp(resources: Resources, px: Float) : Int {
        val metrics: DisplayMetrics = resources.displayMetrics
        return (metrics.density * px).toInt()
    }

    private var textHighLightSchemes = listOf(
        ColorScheme(Pattern.compile("([\"'])(?:\\\\1|.)*?\\1"), Color.parseColor("#FC8500")),
        ColorScheme(Pattern.compile("yt-dlp"), Color.parseColor("#77eb09")),
        ColorScheme(Pattern.compile("(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})"), Color.parseColor("#b5942f")),
        ColorScheme(Pattern.compile("\\d+(\\.\\d)?%"), Color.parseColor("#43a564"))
    )

    fun View.enableTextHighlight(){
        if (this is EditText || this is TextView){
            //init syntax highlighter
            val highlight = Highlight()
            val highlightWatcher = HighlightTextWatcher()

            highlight.addScheme(
                *textHighLightSchemes.map { it }.toTypedArray()
            )
            highlightWatcher.addScheme(
                *textHighLightSchemes.map { it }.toTypedArray()
            )

            highlight.setSpan(this as TextView)
            this.addTextChangedListener(highlightWatcher)
        }
    }

    fun EditText.setTextAndRecalculateWidth(t : String){
        val scale = context.resources.displayMetrics.density
        this.setText(t)
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        this.measure(widthMeasureSpec, heightMeasureSpec)
        val requiredWidth: Int = this.measuredWidth
        if (t.length < 5){
            this.layoutParams.width = (70 * scale + 0.5F).toInt()
        }else{
            this.layoutParams.width = requiredWidth
        }
        this.requestLayout()
    }



    @SuppressLint("ClickableViewAccessibility")
    fun RecyclerView.forceFastScrollMode()
    {
        overScrollMode = View.OVER_SCROLL_ALWAYS
        scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
        isVerticalScrollBarEnabled = true
        setOnTouchListener { view, event ->
            if (event.x >= this.width - 30) {
                view.parent.requestDisallowInterceptTouchEvent(true)
                when (event.action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_UP -> view.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    fun RecyclerView.enableFastScroll(){
        val drawable = ShapeDrawable(OvalShape())
        drawable.paint.color = context.getColor(android.R.color.transparent)

        FastScrollerBuilder(this)
            .setTrackDrawable(drawable)
            .build()
    }

    fun File.getMediaDuration(context: Context): Int {
        return kotlin.runCatching {
            if (!exists()) return 0
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(absolutePath))
            val duration = retriever.extractMetadata(METADATA_KEY_DURATION)
            retriever.release()

            duration?.toIntOrNull()?.div(1000) ?: 0
        }.getOrElse { 0 }
    }
}