package com.deniscerri.ytdl.util

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.net.Uri
import android.text.Html
import android.text.Spanned
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.Interpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.App
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository.EveryCategory
import com.deniscerri.ytdl.util.Extensions.toTimePeriodsArray
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.neo.highlight.core.Highlight
import com.neo.highlight.util.listener.HighlightTextWatcher
import com.neo.highlight.util.scheme.ColorScheme
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import org.json.JSONObject
import java.io.File
import java.net.HttpCookie
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.min


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

    fun TabLayout.Tab.createBadge(nr: Int){
        removeBadge()
        if (nr > 0) {
            orCreateBadge.apply {
                number = nr
                verticalOffset = 3
                horizontalOffset =
                    if (nr < 10) dp(App.instance.resources,  7f)
                    else if (nr < 100) dp(App.instance.resources,  10f)
                    else dp(App.instance.resources,  20f)
            }
        }
    }

    fun String.appendLineToLog(line: String): String {
        val lines = this.split("\n")
        if (!lines.takeLast(3).contains(line)){
            //clean dublicate progress + add newline
            var newLine = line
            if (newLine.contains("[download")) {
                newLine = "[download]" + line.split("[download]").last()
            }

            return lines.dropLastWhile { it.contains("[download") }.joinToString("\n") + "\n${newLine}"
        }

        return this
    }

    fun ImageView.loadThumbnail(hideThumb: Boolean, imageURL: String){
        if(!hideThumb){
            if (imageURL.isNotEmpty()) {
                Picasso.get()
                    .load(imageURL)
                    .resize(1280, 0)
                    .onlyScaleDown()
                    .into(this)

            } else {
                Picasso.get().load(R.color.black).into(this)
            }
        }
    }


    fun List<DownloadRepository.Status>.toListString() : List<String>{
        return this.map { it.toString() }
    }

    fun List<String>.closestValue(value: String) = minBy { abs(value.toInt() - it.toInt()) }

    class CustomInterpolator : Interpolator {
        override fun getInterpolation(input: Float): Float {
            // Adjust this curve as needed for desired animation feel
            return (Math.pow((input - 1).toDouble(), 5.0) + 1).toFloat()
        }
    }

    enum class Period {
        HOUR, MINUTE, SECOND, MILLISECOND
    }

    fun Long.toTimePeriodsArray() : Map<Period, Int> {
        var tmp = this
        val millis = ((tmp % 1000) / 100).toInt()
        tmp /= 1000
        val hours = (tmp / 3600).toInt()
        tmp %= 3600
        val minutes = (tmp / 60).toInt()
        tmp %= 60
        val seconds = tmp.toInt()

        return mapOf(
            Period.HOUR to hours,
            Period.MINUTE to minutes,
            Period.SECOND to seconds,
            Period.MILLISECOND to millis
        )
    }

    fun Long.toStringTimeStamp() : String {
        var tmp = this
        val millis = ((tmp % 1000) / 100).toInt()
        tmp /= 1000
        val hours = (tmp / 3600).toInt()
        tmp %= 3600
        val minutes = (tmp / 60).toInt()
        tmp %= 60
        val seconds = tmp.toInt()

        var res = "${minutes.toString().padStart(if (hours > 0) 2 else 1, '0')}:${seconds.toString().padStart(2, '0')}"
        if (hours > 0){
            res = "${hours}:" + res
        }
        if (millis > 0){
            res += ".${millis}"
        }

        return res
    }

    fun String.convertToTimestamp() : Long {
        return try {
            val timeArray = this.split(":")
            val secondsMillis = timeArray[timeArray.lastIndex]
            var timeSeconds = secondsMillis.split(".")[0].toLong()
            val millis = kotlin.runCatching { secondsMillis.split(".")[1].toInt() }.getOrElse { 0 }

            var times = 60
            for (i in timeArray.lastIndex - 1 downTo 0) {
                timeSeconds += timeArray[i].toInt() * times
                times *= 60
            }

            (timeSeconds * 1000) + millis * 100
        }catch (e: Exception){
            e.printStackTrace()
            0L
        }
    }

    fun String.convertNetscapeToSetCookie(): String {
        // Split the Netscape cookie string
        val parts =
            this.split("\t").dropLastWhile { it.isEmpty() }
                .toTypedArray()

        if (parts.isEmpty()) return ""

        // Extract the individual components
        val domain = parts[0].trim { it <= ' ' }
        val isSecure =
            java.lang.Boolean.parseBoolean(parts[3].trim { it <= ' ' })
        val expiry = parts[4].trim { it <= ' ' }.toLong()
        val name = parts[5].trim { it <= ' ' }
        val value = parts[6].trim { it <= ' ' }

        // Create the BasicClientCookie
        val cookie = HttpCookie(name, value)
        cookie.domain = domain
        cookie.path = "/"
        cookie.secure = isSecure

        // Set expiry
        if (expiry != 0L) {
            cookie.maxAge = expiry
        } else {
            // For session cookies, set to null
            cookie.maxAge = -1L
        }

        // Get the Set-Cookie header format
        return cookie.toString()
    }
    
    fun ObserveSourcesItem.calculateNextTimeForObserving() : Long {
        val item = this
        val now = System.currentTimeMillis()
        Calendar.getInstance().apply {
            timeInMillis = item.startsTime

            if (item.everyCategory != EveryCategory.HOUR){
                val hourMin = Calendar.getInstance()
                hourMin.timeInMillis = item.everyTime

                set(Calendar.HOUR_OF_DAY, hourMin.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, hourMin.get(Calendar.MINUTE))
            }

            while (timeInMillis < now){
                when(item.everyCategory){
                    EveryCategory.HOUR -> {
                        add(Calendar.HOUR, item.everyNr)
                    }
                    EveryCategory.DAY -> {
                        add(Calendar.DAY_OF_MONTH, item.everyNr)
                    }
                    EveryCategory.WEEK -> {
                        item.weeklyConfig?.apply {
                            if (this.weekDays.isEmpty()){
                                add(Calendar.DAY_OF_MONTH, 7 * item.everyNr)
                            }else{
                                var weekDayNr = get(Calendar.DAY_OF_WEEK) - 1
                                if (weekDayNr == 0) weekDayNr = 7
                                val followingWeekDay = this.weekDays.firstOrNull { it > weekDayNr }
                                if (followingWeekDay == null){
                                    add(Calendar.DAY_OF_MONTH, this.weekDays.minBy { it } + (7 - weekDayNr))
                                    item.everyNr--
                                }else{
                                    add(Calendar.DAY_OF_MONTH, followingWeekDay.toInt() - weekDayNr)
                                }

                                if (item.everyNr > 1){
                                    add(Calendar.DAY_OF_MONTH, 7 * item.everyNr)
                                }
                            }
                        }
                    }
                    EveryCategory.MONTH -> {
                        add(Calendar.MONTH, item.everyNr)
                        item.monthlyConfig?.apply {
                            set(Calendar.DAY_OF_MONTH, this.everyMonthDay)
                        }
                    }
                }
            }

            return timeInMillis
        }
    }

    fun Int.toBitmap(context: Context): Bitmap{
        val drawable = ContextCompat.getDrawable(context, this)
        val bitmap = Bitmap.createBitmap(
            drawable!!.intrinsicWidth,
            drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )

        val paint = Paint()
        val colorValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorActivatedHighlight, colorValue, true)
        paint.setColorFilter(PorterDuffColorFilter(colorValue.data, PorterDuff.Mode.SRC_IN))

        val canvas = Canvas(bitmap)
        DrawableCompat.setTint(drawable, ContextCompat.getColor(context, R.color.icon_fg))
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun DownloadItem.setAsScheduling(timeInMillis: Long) {
        status = DownloadRepository.Status.Scheduled.toString()
        downloadStartTime = timeInMillis
    }

    fun TextWithSubtitle(title: String, subtitle: String) : Spanned {
        return HtmlCompat.fromHtml("<b><big>" + title + "</big></b>" +  "<br />" +
                "<small>" + subtitle + "</small>" + "<br />", HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

}