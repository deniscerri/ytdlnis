package com.deniscerri.ytdl.util

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.net.Uri
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
import android.widget.ScrollView
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.neoutils.highlight.core.Highlight
import com.neoutils.highlight.core.scheme.TextColorScheme
import com.neoutils.highlight.core.util.Match
import com.neoutils.highlight.core.util.UiColor
import com.neoutils.highlight.view.extension.toSpannedString
import com.neoutils.highlight.view.text.HighlightTextWatcher
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.BlurTransformation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import org.json.JSONObject
import java.io.File
import java.net.HttpCookie
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


object Extensions {
    fun dp(resources: Resources, px: Float) : Int {
        val metrics: DisplayMetrics = resources.displayMetrics
        return (metrics.density * px).toInt()
    }

    private var textHighlightSchemes = listOf(
        TextColorScheme(regex = "([\"'])(?:\\\\1|.)*?\\1".toRegex(), match = Match.fully(UiColor.Hex("#FC8500"))),
        TextColorScheme(regex = "yt-dlp".toRegex(), match = Match.fully(UiColor.Hex("#77eb09"))),
        TextColorScheme(regex = "(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})".toRegex(), match = Match.fully(UiColor.Hex("#b5942f"))),
        TextColorScheme(regex = "\\d+(\\.\\d)?%".toRegex(), match = Match.fully(UiColor.Hex("#43a564"))),
    )


    fun View.enableTextHighlight(){
        if (this is EditText || this is TextView){
            //init syntax highlighter
            val highlight = Highlight(textHighlightSchemes)
            val highlightWatcher = HighlightTextWatcher(highlight)

            if (this is EditText) {
                this.addTextChangedListener(highlightWatcher)
                this.setText(highlight.toSpannedString(this.text.toString()))
            }else if (this is TextView) {
                this.addTextChangedListener(highlightWatcher)
                this.text = highlight.toSpannedString(this.text.toString())
            }
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

    fun ScrollView.enableFastScroll() {
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
                if (tmp != "null") {
                    return if (tmp.startsWith("[") && tmp.endsWith("]")) {
                        Json.decodeFromString<List<String>>(tmp).joinToString(", ")
                    }else{
                        tmp
                    }
                }
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

    fun String.appendLineToLog(line: String = ""): String {
        val lines = this.lines().toMutableList()
        val finishingProgressLinesRegex = Pattern.compile("\\[download]\\h+(100%|[a-zA-Z])")

        if (line.isNotBlank()) {
            var newline = ""
            val newLines = line.lines().filter { !lines.contains(it) }
            lines.addAll(newLines)
            if (newLines.isNotEmpty()) {
                newLines.last().apply {
                    if (this.contains("[download")) {
                        newline = "\n${this}"
                    }
                }
            }


            return lines.distinct().filterNot {
                it.contains("[download") && !finishingProgressLinesRegex.matcher(it).find()
            }.joinToString("\n") + newline
        }

        return lines.filterNot {
            it.contains("[download") && !finishingProgressLinesRegex.matcher(it).find()
        }.joinToString("\n")
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

    fun ImageView.loadBlurryThumbnail(context: Context, hideThumb: Boolean, imageURL: String) {
        if(!hideThumb){
            if (imageURL.isNotEmpty()) {
                Picasso.get()
                    .load(imageURL)
                    .resize(1280, 0)
                    .transform(BlurTransformation(context, 1, 1))
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

    fun Long.toStringTimeStamp(forceMillis: Boolean = false, showMillisIfNonZero : Boolean = false): String {
        return this.milliseconds.toComponents { hours, minutes, seconds, nanoseconds ->
            buildString {
                if (hours > 0) {
                    append(hours)
                    append(':')
                }
                append(minutes.toString().padStart(if (hours > 0) 2 else 1, '0'))
                append(':')
                append(seconds.toString().padStart(2, '0'))

                val millis = nanoseconds / 1_000_000
                if (forceMillis || showMillisIfNonZero && millis > 0) {
                    append('.')
                    var millisString = millis.toString().padStart(3, '0')
                    if (showMillisIfNonZero) millisString = millisString.trimEnd('0')
                    append(millisString)
                }
            }
        }
    }

    fun String.convertToTimestamp(): Long =
        kotlin.runCatching { tryConvertToTimestamp() }.getOrNull() ?: 0L

    private val timeRegex =
        Regex("""^(?:(?:(\d+):)?(\d{1,2}):)?(\d+)(?:\.(\d+))?$""")

    fun String.tryConvertToTimestamp(): Long? {
        try {
            val match = timeRegex.matchEntire(this.trim()) ?: return null

            val hours = match.groups[1]?.value?.toInt() ?: 0
            val minutes = match.groups[2]?.value?.toInt() ?: 0
            val seconds = match.groups[3]!!.value.toInt()
            val millis = match.groups[4]?.value
                ?.take(3)?.padEnd(3,'0')?.toInt() ?: 0

            return (hours.hours + minutes.minutes + seconds.seconds + millis.milliseconds).inWholeMilliseconds
        }catch (ex: Exception) {
            return null
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

    fun String.isYoutubeURL() : Boolean {
        return Pattern.compile("((^(https?)://)?(www.)?(m.)?youtu(.be)?)|(^(https?)://(www.)?piped.video)").matcher(this).find()
    }

    fun String.isYoutubeChannelURL() : Boolean {
        return Pattern.compile("((^(https?)://)?(www.)?(m.)?youtu(.be)?(be.com))/@[a-zA-Z]+").matcher(this).find()
    }

    fun String.isYoutubeWatchVideosURL() : Boolean {
        return Pattern.compile("((^(https?)://)?(www.)?(m.)?youtu(.be)?(be.com))/watch_videos\\?video_ids=.*").matcher(this).find()
    }

    fun String.extractURL() : String {
        val res =
            Pattern.compile("(http|ftp|https)://([\\w_-]+(?:\\.[\\w_-]+)+)([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])")
                .matcher(this)
        return if (res.find()) {
            res.group()
        } else {
            this
        }
    }

    fun String.isURL(): Boolean {
        return Pattern.compile("(http|ftp|https)://([\\w_-]+(?:\\.[\\w_-]+)+)([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])").matcher(this).find()
    }

    fun <T1, T2, T3, T4, T5, T6, T7, R> combine(
        flow: Flow<T1>,
        flow2: Flow<T2>,
        flow3: Flow<T3>,
        flow4: Flow<T4>,
        flow5: Flow<T5>,
        flow6: Flow<T6>,
        flow7: Flow<T7>,
        transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R
    ): Flow<R> = combine(
        flow,
        combine(flow2, flow3, ::Pair),
        combine(flow4, flow5, ::Pair),
        combine(flow6, flow7, ::Pair),
    ) { t1, t2, t3, t4 ->
        transform(
            t1,
            t2.first,
            t2.second,
            t3.first,
            t3.second,
            t4.first,
            t4.second
        )
    }


    fun DownloadItem.needsDataUpdating() : Boolean {
        return this.title.isBlank() || this.author.isBlank() || this.thumb.isBlank()
    }

    fun String.applyFilenameTemplateForCuts() : String {
        return if(this.isBlank()) {
            "%(section_title&{} - |)s%(title).170B"
        }else {
            if(this.startsWith("%(section_title&{} - |)s")) this
            else "%(section_title&{} - |)s$this"
        }
    }

    fun String.getIDFromYoutubeURL() : String? {
        val regex = Regex(
            "(?:youtube\\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?)/|.*?[?&]v=)|youtu\\.be/)([^\"&?/\\s]{11})"
        )
        val match = regex.find(this)
        return if (match != null){
            match.groupValues[1]
        }else {
            null
        }
    }
}