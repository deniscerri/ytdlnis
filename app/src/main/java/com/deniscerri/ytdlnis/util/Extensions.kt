package com.deniscerri.ytdlnis.util

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.net.Uri
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.Interpolator
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.Px
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.util.Extensions.popup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.neo.highlight.core.Highlight
import com.neo.highlight.util.listener.HighlightTextWatcher
import com.neo.highlight.util.scheme.ColorScheme
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs


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
            .useMd2Style()
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


    fun Dialog.setFullScreen(
        @Px cornerRadius: Int = 0,
    skipCollapsed: Boolean = true
    ) {
        check(this is BottomSheetDialog) {
            "Dialog must be a BottomSheetBottomSheetDialog."
        }

        lifecycleScope.launch {
            withStarted {
                val bottomSheetLayout = findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet)  ?: return@withStarted
                with(bottomSheetLayout) {
                    updateLayoutParams {
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(
                                0,
                                0,
                                view.width,
                                view.height + cornerRadius,
                                cornerRadius.toFloat()
                            )
                        }
                    }
                }
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.maxWidth = ViewGroup.LayoutParams.MATCH_PARENT
                behavior.skipCollapsed = skipCollapsed
            }
        }
    }

    fun JSONObject.getIntByAny(vararg tags:String): Int {
        tags.forEach {
            runCatching {
                return this.getInt(it)
            }
        }
        return -1
    }

    fun JSONObject.getStringByAny(vararg tags:String): String {
        tags.forEach {
            runCatching {
                val tmp = this.getString(it)
                if (tmp != "null") return tmp
            }
        }

        return ""
    }

    fun TextView.setCustomTextSize(newSize: Float){
        this.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSize)
    }

    fun Int.toStringDuration(locale: Locale): String {
        var format = String.format(
            locale,
            "%02d:%02d:%02d",
            this / 3600,
            this % 3600 / 60,
            this % 60
        )
        // 00:00:00
        if (this < 600) format = format.substring(4) else if (this < 3600) format =
            format.substring(3) else if (this < 36000) format = format.substring(1)
        return format
    }


    fun View.popup(){
        val animator = ValueAnimator.ofFloat( 0.75f, 1f)
        animator.addUpdateListener { animation: ValueAnimator ->
            val value = animation.animatedValue as Float
            this.scaleX = value
            this.scaleY = value
        }
        animator.interpolator = Extensions.CustomInterpolator()
        animator.setDuration(300)
        animator.start()
    }

    fun List<String>.closestValue(value: String) = minBy { abs(value.toInt() - it.toInt()) }

    class CustomInterpolator : Interpolator {
        override fun getInterpolation(input: Float): Float {
            // Adjust this curve as needed for desired animation feel
            return (Math.pow((input - 1).toDouble(), 5.0) + 1).toFloat()
        }
    }


}