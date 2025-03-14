package com.deniscerri.ytdl.util

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DimenRes
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.afollestad.materialdialogs.utils.MDUtil.textChanged
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.Format
import com.deniscerri.ytdl.database.models.GithubRelease
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.TemplateShortcut
import com.deniscerri.ytdl.database.models.YoutubePlayerClientItem
import com.deniscerri.ytdl.database.models.YoutubePoTokenItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.YTDLPViewModel
import com.deniscerri.ytdl.ui.downloadcard.VideoCutListener
import com.deniscerri.ytdl.util.Extensions.enableTextHighlight
import com.deniscerri.ytdl.util.Extensions.getMediaDuration
import com.deniscerri.ytdl.util.Extensions.toStringDuration
import com.deniscerri.ytdl.util.extractors.YTDLPUtil
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputLayout.END_ICON_NONE
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.gson.Gson
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


object UiUtil {
    @SuppressLint("SetTextI18n")
    fun populateFormatCard(context: Context, formatCard : MaterialCardView, chosenFormat: Format, audioFormats: List<Format>? = null, showSize: Boolean = true){
        var formatNote = chosenFormat.format_note
        if (formatNote.isEmpty()) formatNote = context.getString(R.string.defaultValue)
        else if (formatNote == "best") formatNote = context.getString(R.string.best_quality)
        else if (formatNote == "worst") formatNote = context.getString(R.string.worst_quality)

        var container = chosenFormat.container
        if (container == "Default" || container.isBlank()) container = context.getString(R.string.defaultValue)

        formatCard.findViewById<TextView>(R.id.container).text = container.uppercase()
        formatCard.findViewById<TextView>(R.id.format_note).text = formatNote.uppercase()

        formatCard.findViewById<TextView>(R.id.audio_formats).apply {
            if (!audioFormats.isNullOrEmpty()){
                text = "id: " + audioFormats.joinToString("+") { it.format_id }
                visibility = View.VISIBLE
            }else if (chosenFormat.vcodec != "none" && chosenFormat.vcodec != "" && chosenFormat.acodec != "none" && chosenFormat.acodec != "") {
                text = chosenFormat.acodec
                visibility = View.VISIBLE
            }else{
                visibility = View.GONE
            }
            setOnClickListener {
                formatCard.callOnClick()
            }
        }

        formatCard.findViewById<TextView>(R.id.format_id).apply {
            text = "id: ${chosenFormat.format_id}"
            setOnClickListener {
                formatCard.callOnClick()
            }
        }
        val codec =
            if (chosenFormat.encoding != "") {
                chosenFormat.encoding.uppercase()
            }else if (chosenFormat.vcodec != "none" && chosenFormat.vcodec != ""){
                chosenFormat.vcodec.uppercase()
            } else {
                chosenFormat.acodec.uppercase()
            }

        formatCard.findViewById<TextView>(R.id.codec).apply {
            if (codec == "" || codec == "none"){
                visibility = View.GONE
            }else{
                visibility = View.VISIBLE
                text = codec
            }
            setOnClickListener {
                formatCard.callOnClick()
            }
        }


        var filesize = chosenFormat.filesize
        if (!audioFormats.isNullOrEmpty() && filesize > 10L) filesize += audioFormats.sumOf { it.filesize }
        formatCard.findViewById<TextView>(R.id.file_size).apply {
            if (showSize) {
                text = FileUtil.convertFileSize(filesize)
            }else{
                text = "?"
            }
            setOnClickListener {
                formatCard.callOnClick()
            }
        }

        formatCard.findViewById<TextView>(R.id.bitrate).apply {
            if (chosenFormat.tbr.isNullOrBlank() || (chosenFormat.vcodec.isNotBlank() && chosenFormat.vcodec != "none")) {
                isVisible = false
            }else{
                text = chosenFormat.tbr
            }
        }

    }

    fun populateCommandCard(card: MaterialCardView, item: CommandTemplate){
        card.findViewById<TextView>(R.id.title).text = item.title
        card.findViewById<TextView>(R.id.content).text = item.content
        card.alpha = 1f
        card.tag = item.id
    }

     fun showCommandTemplateCreationOrUpdatingSheet(
         item: CommandTemplate?,
         context: Activity,
         lifeCycle: LifecycleOwner,
         commandTemplateViewModel: CommandTemplateViewModel,
         newTemplate: (newTemplate: CommandTemplate) -> Unit,
         dismissed: () -> Unit
     ){
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.create_command_template)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val ok : Button = bottomSheet.findViewById(R.id.template_create)!!
        val title : TextInputLayout = bottomSheet.findViewById(R.id.title)!!
        val content : TextInputLayout = bottomSheet.findViewById(R.id.content)!!
        val preferredCommandSwitch : MaterialSwitch = bottomSheet.findViewById(R.id.preferredCommandTemplateSwitch)!!
        val extraCommandsSwitch : MaterialSwitch = bottomSheet.findViewById(R.id.extraCommandsSwitch)!!
        val extraCommandsAudio : CheckBox = bottomSheet.findViewById(R.id.checkbox_audio)!!
        val extraCommandsVideo : CheckBox = bottomSheet.findViewById(R.id.checkbox_video)!!
        val extraCommandsDataFetchingSwitch : MaterialSwitch = bottomSheet.findViewById(R.id.extraCommandDataFetching)!!
        val shortcutsChipGroup : ChipGroup = bottomSheet.findViewById(R.id.shortcutsChipGroup)!!
        val editShortcuts : Button = bottomSheet.findViewById(R.id.edit_shortcuts)!!
        val urlRegex : TextInputLayout = bottomSheet.findViewById(R.id.url_regex)!!
        val urlRegexChips : ChipGroup = bottomSheet.findViewById(R.id.urlRegexChipGroup)!!

        extraCommandsDataFetchingSwitch.isVisible = sharedPreferences.getBoolean("enable_data_fetching_extra_commands", false)

        if (item != null){
            title.editText!!.setText(item.title)
            content.editText!!.setText(item.content)
            bottomSheet.findViewById<TextView>(R.id.bottom_sheet_subtitle)!!.text = content.resources.getString(R.string.update_template)
            ok.text = content.resources.getString(R.string.update)
            ok.isEnabled = true
            content.endIconDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_delete_all)
        }else{
            ok.isEnabled = false
            content.endIconDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_clipboard)
        }

        title.editText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                ok.isEnabled = title.editText!!.text.isNotEmpty() &&
                        content.editText!!.text.isNotEmpty() &&
                        if (extraCommandsSwitch.isChecked){
                            ((extraCommandsAudio.isChecked || extraCommandsVideo.isChecked))
                        }else{
                            true
                        }
            }
        })

        content.editText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                ok.isEnabled = title.editText!!.text.isNotEmpty() &&
                                content.editText!!.text.isNotEmpty() &&
                                if (extraCommandsSwitch.isChecked){
                                    ((extraCommandsAudio.isChecked || extraCommandsVideo.isChecked))
                                }else{
                                    true
                                }

                if (content.editText!!.text.isNotEmpty()){
                    content.endIconDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_delete_all)
                }else{
                    content.endIconDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_clipboard)
                }
            }
        })

        content.setEndIconOnClickListener {
            if(content.editText!!.text.isEmpty()){
                val clipboard: ClipboardManager =
                    context.getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                content.editText!!.setText(clipboard.primaryClip?.getItemAt(0)?.text)
            }else{
                content.editText!!.setText("")
            }
        }

        if (item != null){
            preferredCommandSwitch.isChecked = item.preferredCommandTemplate
            extraCommandsDataFetchingSwitch.isChecked = item.useAsExtraCommandDataFetching && extraCommandsDataFetchingSwitch.isVisible

            extraCommandsSwitch.isChecked = item.useAsExtraCommand
            if (item.useAsExtraCommand){
                extraCommandsAudio.isVisible = true
                extraCommandsAudio.isChecked = item.useAsExtraCommandAudio
                extraCommandsVideo.isVisible = true
                extraCommandsVideo.isChecked = item.useAsExtraCommandVideo
            }else{
                extraCommandsAudio.isVisible = false
                extraCommandsAudio.isChecked = false
                extraCommandsVideo.isVisible = false
                extraCommandsVideo.isChecked = false
            }

            val canUseURLRegex = item.useAsExtraCommand || item.useAsExtraCommandDataFetching || item.preferredCommandTemplate
            urlRegex.isVisible = canUseURLRegex
            urlRegexChips.isVisible = canUseURLRegex

            for(chip in item.urlRegex) {
                val tmp = context.layoutInflater.inflate(R.layout.input_chip, urlRegexChips, false) as Chip
                tmp.text = chip
                tmp.setOnClickListener {
                    urlRegexChips.removeView(tmp)
                }
                urlRegexChips.addView(tmp)
            }
        }

         preferredCommandSwitch.setOnCheckedChangeListener { compoundButton, b ->
             val canUseURLRegex = extraCommandsSwitch.isChecked || extraCommandsDataFetchingSwitch.isChecked || preferredCommandSwitch.isChecked
             urlRegex.isVisible = canUseURLRegex
             urlRegexChips.isVisible = canUseURLRegex
         }

         extraCommandsSwitch.setOnCheckedChangeListener { compoundButton, b ->
             extraCommandsAudio.isVisible = extraCommandsSwitch.isChecked
             extraCommandsAudio.isChecked = true
             extraCommandsVideo.isVisible = extraCommandsSwitch.isChecked
             extraCommandsVideo.isChecked = true

             val canUseURLRegex = extraCommandsSwitch.isChecked || extraCommandsDataFetchingSwitch.isChecked || preferredCommandSwitch.isChecked
             urlRegex.isVisible = canUseURLRegex
             urlRegexChips.isVisible = canUseURLRegex
         }



         extraCommandsAudio.setOnCheckedChangeListener { compoundButton, b ->
             ok.isEnabled = (extraCommandsAudio.isChecked || extraCommandsVideo.isChecked) && title.editText!!.text.isNotEmpty() && content.editText!!.text.isNotEmpty()
         }

         extraCommandsVideo.setOnCheckedChangeListener { compoundButton, b ->
             ok.isEnabled = (extraCommandsAudio.isChecked || extraCommandsVideo.isChecked) && title.editText!!.text.isNotEmpty() && content.editText!!.text.isNotEmpty()
         }

         extraCommandsDataFetchingSwitch.setOnCheckedChangeListener { compoundButton, b ->
             val canUseURLRegex = extraCommandsSwitch.isChecked || extraCommandsDataFetchingSwitch.isChecked || preferredCommandSwitch.isChecked
             urlRegex.isVisible = canUseURLRegex
             urlRegexChips.isVisible = canUseURLRegex
         }

         urlRegex.isEndIconVisible = false
         urlRegex.editText!!.doOnTextChanged { text, start, before, count ->
             urlRegex.isEndIconVisible = urlRegex.editText!!.text.isNotBlank()
         }

         urlRegex.setEndIconOnClickListener {
             val text = urlRegex.editText!!.text
             urlRegex.editText!!.setText("")
             val tmp = context.layoutInflater.inflate(R.layout.input_chip, urlRegexChips, false) as Chip
             tmp.text = text
             tmp.setOnClickListener {
                 urlRegexChips.removeView(tmp)
             }
             urlRegexChips.addView(tmp)
         }

        commandTemplateViewModel.shortcuts.observe(lifeCycle){
            shortcutsChipGroup.removeAllViews()
            it.forEach {shortcut ->
                val chip = context.layoutInflater.inflate(R.layout.suggestion_chip, shortcutsChipGroup, false) as Chip
                chip.text = shortcut.content
                chip.setOnClickListener {
                    content.editText!!.text.insert(content.editText!!.selectionStart, shortcut.content + " ")
                }
                shortcutsChipGroup.addView(chip)
            }
        }

        editShortcuts.setOnClickListener {
            showShortcutsSheet(context, lifeCycle, commandTemplateViewModel)
        }

        ok.setOnClickListener {
            val urlRegexes = urlRegexChips.children.map { (it as Chip).text.toString() }.toMutableList()
            if (item == null){

                val t = CommandTemplate(
                    0,
                    title.editText!!.text.toString(),
                    content.editText!!.text.toString(),
                    extraCommandsSwitch.isChecked,
                    extraCommandsAudio.isChecked,
                    extraCommandsVideo.isChecked,
                    extraCommandsDataFetchingSwitch.isChecked,
                    preferredCommandSwitch.isChecked,
                    urlRegexes
                )
                commandTemplateViewModel.insert(t)
                newTemplate(t)
            }else{
                item.title = title.editText!!.text.toString()
                item.content = content.editText!!.text.toString()
                item.useAsExtraCommand = extraCommandsSwitch.isChecked
                item.useAsExtraCommandAudio = extraCommandsAudio.isChecked
                item.useAsExtraCommandVideo = extraCommandsVideo.isChecked
                item.useAsExtraCommandDataFetching = extraCommandsDataFetchingSwitch.isChecked
                item.preferredCommandTemplate = preferredCommandSwitch.isChecked
                item.urlRegex = urlRegexes
                commandTemplateViewModel.update(item)
                newTemplate(item)
            }
            bottomSheet.cancel()
        }

        bottomSheet.setOnDismissListener {
            dismissed()
        }

        bottomSheet.show()
        bottomSheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    fun showShortcutsSheet(context: Activity, lifeCycle: LifecycleOwner, commandTemplateViewModel: CommandTemplateViewModel){
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.template_shortcuts)

        val title : TextInputLayout = bottomSheet.findViewById(R.id.title)!!
        val shortcutsChipGroup : ChipGroup = bottomSheet.findViewById(R.id.shortcutsChipGroup)!!
        title.isEndIconVisible = false
        title.editText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                title.isEndIconVisible = p0!!.isNotEmpty()
            }
        })

        title.setEndIconOnClickListener {
            commandTemplateViewModel.insertShortcut(TemplateShortcut(0, title.editText!!.text.toString()))
            title.editText!!.setText("")
            title.isEndIconVisible = false
        }

        commandTemplateViewModel.shortcuts.observe(lifeCycle){
            shortcutsChipGroup.removeAllViews()
            it.forEach {shortcut ->
                val chip = context.layoutInflater.inflate(R.layout.input_chip, shortcutsChipGroup, false) as Chip
                chip.text = shortcut.content
                chip.setOnClickListener{
                    commandTemplateViewModel.deleteShortcut(shortcut)
                }
                shortcutsChipGroup.addView(chip)
            }
        }

        bottomSheet.show()
        bottomSheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    fun copyLinkToClipBoard(context: Context, url: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.url), url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.link_copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }

    fun openLinkIntent(context: Context, url: String) {
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        context.startActivity(i)
    }



    fun showDatePickerOnly(fragmentManager: FragmentManager , onSubmit : (chosenDate: Calendar) -> Unit ){
        val date = Calendar.getInstance()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build()
            )
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener{
            date.timeInMillis = it
            onSubmit(date)
        }
        datePicker.show(fragmentManager, "datepicker")
    }

    fun showDatePicker(fragmentManager: FragmentManager , preferences: SharedPreferences, onSubmit : (chosenDate: Calendar) -> Unit ){
        val currentDate = Calendar.getInstance()
        currentDate.timeInMillis = (currentDate.timeInMillis - (currentDate.timeInMillis % 1800000)) + 1800000
        val date = Calendar.getInstance()

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.now())
                    .build()
            )
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener{
            date.timeInMillis = it

            showTimePicker(fragmentManager, preferences) { chosenTime ->
                date[Calendar.HOUR_OF_DAY] = chosenTime[Calendar.HOUR_OF_DAY]
                date[Calendar.MINUTE] = chosenTime[Calendar.MINUTE]
                onSubmit(date)
            }

        }
        datePicker.show(fragmentManager, "datepicker")
    }

    fun showTimePicker(fragmentManager: FragmentManager , preferences: SharedPreferences, onSubmit : (chosenTime: Calendar) -> Unit ){
        val currentDate = Calendar.getInstance()
        currentDate.timeInMillis = (currentDate.timeInMillis - (currentDate.timeInMillis % 1800000)) + 1800000
        val date = Calendar.getInstance()

        val lastTime = preferences.getString("latest_timepicker_date", "${currentDate.get(Calendar.HOUR_OF_DAY)}:${currentDate.get(Calendar.MINUTE)}")!!
        val hour = lastTime.split(":")[0].toInt()
        val minute = lastTime.split(":")[1].toInt()

        val timepicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .build()

        timepicker.addOnPositiveButtonClickListener{
            date[Calendar.HOUR_OF_DAY] = timepicker.hour
            date[Calendar.MINUTE] = timepicker.minute
            preferences.edit().putString("latest_timepicker_date", "${timepicker.hour}:${timepicker.minute}").apply()
            onSubmit(date)
        }
        timepicker.show(fragmentManager, "timepicker")
    }

    fun showDownloadItemDetailsCard(
        item: DownloadItem,
        context: Activity,
        status: DownloadRepository.Status,
        ytdlpViewModel: YTDLPViewModel,
        preferences: SharedPreferences,
        removeItem : (DownloadItem, BottomSheetDialog) -> Unit,
        downloadItem: (DownloadItem) -> Unit,
        longClickDownloadButton: (DownloadItem) -> Unit,
        scheduleButtonClick: (DownloadItem) -> Unit?
    ){
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.history_item_details_bottom_sheet)
        bottomSheet.findViewById<TextView>(R.id.bottom_sheet_title)?.apply {
            text = item.title.ifEmpty { item.url.ifEmpty { item.playlistTitle.ifEmpty {  "`${context.getString(R.string.defaultValue)}`" } } }
            setOnLongClickListener {
                showFullTextDialog(context, text.toString(), context.getString(R.string.title))
                true
            }
        }
        bottomSheet.findViewById<TextView>(R.id.bottom_sheet_author)?.apply {
            text = item.author
            setOnLongClickListener {
                showFullTextDialog(context, text.toString(), context.getString(R.string.author))
                true
            }
        }

        // BUTTON ----------------------------------
        val btn = bottomSheet.findViewById<FloatingActionButton>(R.id.download_button_type)
        val typeImageResource: Int =
            when (item.type) {
                DownloadViewModel.Type.audio -> {
                    R.drawable.ic_music
                }
                DownloadViewModel.Type.video -> {
                    R.drawable.ic_video
                }
                else -> {
                    R.drawable.ic_terminal
                }
            }
        btn?.setImageResource(typeImageResource)

        val time = bottomSheet.findViewById<Chip>(R.id.time)
        val thumbnail = bottomSheet.findViewById<Chip>(R.id.thumbnail)
        val formatNote = bottomSheet.findViewById<Chip>(R.id.format_note)
        val container = bottomSheet.findViewById<Chip>(R.id.container_chip)
        val codec = bottomSheet.findViewById<Chip>(R.id.codec)
        val fileSize = bottomSheet.findViewById<Chip>(R.id.file_size)
        val command = bottomSheet.findViewById<Chip>(R.id.command)

        when(status){
            DownloadRepository.Status.Scheduled -> {
                if (item.downloadStartTime <= System.currentTimeMillis() / 1000) time!!.visibility = View.GONE
                else {
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = item.downloadStartTime
                    time!!.text = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(calendar.time)

                    time.setOnClickListener {
                        scheduleButtonClick(item)
                        bottomSheet.dismiss()
                    }
                }
            }
            else -> {
                time!!.visibility = View.GONE
            }
        }

        thumbnail?.isVisible = item.thumb.isNotBlank()
        thumbnail?.apply {
            isVisible = item.thumb.isNotBlank()
            setOnClickListener {
                openLinkIntent(context, item.thumb)
            }
        }

        if (item.type != DownloadViewModel.Type.command){
            if (item.format.format_note == "?" || item.format.format_note == "") formatNote!!.visibility =
                View.GONE
            else formatNote!!.text = item.format.format_note
        }else{
            formatNote?.isVisible = false
        }

        if (item.format.container != "") {
            container!!.text = item.format.container.uppercase()
            container.setChipIconResource(typeImageResource)
        }else {
            container!!.visibility = View.GONE
        }

        val codecText =
            if (item.format.encoding != "") {
                item.format.encoding.uppercase()
            }else if (item.format.vcodec != "none" && item.format.vcodec != ""){
                item.format.vcodec.uppercase()
            } else {
                item.format.acodec.uppercase()
            }
        if (codecText == "" || codecText == "none"){
            codec!!.visibility = View.GONE
        }else{
            codec!!.visibility = View.VISIBLE
            codec.text = codecText
        }

        val fileSizeReadable = FileUtil.convertFileSize(item.format.filesize)
        if (fileSizeReadable == "?") fileSize!!.visibility = View.GONE
        else fileSize!!.text = fileSizeReadable

        command?.setOnClickListener {
            showGeneratedCommand(context, preferences, ytdlpViewModel.parseYTDLRequestString(item))
        }

        val link = bottomSheet.findViewById<Button>(R.id.bottom_sheet_link)
        val url = item.url
        link!!.text = url
        link.tag = item.id
        link.setOnClickListener{
            bottomSheet.dismiss()
            openLinkIntent(context, item.url)
        }
        link.setOnLongClickListener{
            bottomSheet.dismiss()
            copyLinkToClipBoard(context, item.url)
            true
        }
        val remove = bottomSheet.findViewById<Button>(R.id.bottomsheet_remove_button)
        remove!!.tag = item.id
        remove.setOnClickListener{
            removeItem(item, bottomSheet)
        }
        val openFile = bottomSheet.findViewById<Button>(R.id.bottomsheet_open_file_button)
        openFile!!.visibility = View.GONE

        val download = bottomSheet.findViewById<Button>(R.id.bottomsheet_redownload_button)
        download?.tag = item.id
        when(status){
            DownloadRepository.Status.Cancelled, DownloadRepository.Status.Saved -> {
                download!!.text = context.getString(R.string.download)
                download.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_downloads, 0, 0, 0);
                download.setOnLongClickListener {
                    longClickDownloadButton(item)
                    bottomSheet.cancel()
                    true
                }
            }
            DownloadRepository.Status.Queued -> {
                download!!.text = context.getString(R.string.configure_download)
                download.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_retries, 0, 0, 0);
                download.setOnClickListener {
                    longClickDownloadButton(item)
                    bottomSheet.cancel()
                }
                download.setOnLongClickListener {
                    longClickDownloadButton(item)
                    bottomSheet.cancel()
                    true
                }
            }
            DownloadRepository.Status.Scheduled -> {
                download!!.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_downloads, 0, 0, 0);
                download.text = context.getString(R.string.download_now)
            }
            DownloadRepository.Status.Error -> {
                download?.setOnClickListener {
                    longClickDownloadButton(item)
                    bottomSheet.cancel()
                }
                download?.setOnLongClickListener {
                    longClickDownloadButton(item)
                    bottomSheet.cancel()
                    true
                }
            }
            else -> {
                download?.setOnLongClickListener {
                    longClickDownloadButton(item)
                    bottomSheet.cancel()
                    true
                }
            }
        }

        if (status != DownloadRepository.Status.Queued && status != DownloadRepository.Status.Error){
            download?.setOnClickListener {
                bottomSheet.dismiss()
                downloadItem(item)
            }
        }

        bottomSheet.show()
        val displayMetrics = DisplayMetrics()
        context.windowManager.defaultDisplay.getMetrics(displayMetrics)
        bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    fun showHistoryItemDetailsCard(
        item: HistoryItem?,
        context: Activity,
        isPresent: Boolean,
        preferences: SharedPreferences,
        removeItem: (item:HistoryItem, removeFiles: Boolean) -> Unit,
        redownloadItem: (HistoryItem) -> Unit,
        redownloadShowDownloadCard: (HistoryItem) -> Unit,
    ){
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.history_item_details_bottom_sheet)

        bottomSheet.findViewById<TextView>(R.id.bottom_sheet_title)?.apply {
            text = item!!.title.ifEmpty { item.url }
            setOnLongClickListener{
                showFullTextDialog(context, text.toString(), context.getString(R.string.title))
                true
            }
        }
        bottomSheet.findViewById<TextView>(R.id.bottom_sheet_author)?.apply {
            text = item!!.author
            setOnLongClickListener{
                showFullTextDialog(context, text.toString(), context.getString(R.string.author))
                true
            }
        }

        // BUTTON ----------------------------------
        val btn = bottomSheet.findViewById<FloatingActionButton>(R.id.download_button_type)

        val typeImageResource: Int =
        if (item!!.type == DownloadViewModel.Type.audio) {
            if (isPresent) {
                R.drawable.ic_music_downloaded
            } else {
                R.drawable.ic_music
            }
        } else if (item.type == DownloadViewModel.Type.video) {
            if (isPresent) {
                R.drawable.ic_video_downloaded
            } else {
                R.drawable.ic_video
            }
        }else{
            R.drawable.ic_terminal
        }
        btn?.setImageResource(typeImageResource)

        if (isPresent){
            btn?.apply {
                if (item.downloadPath.size > 1){
                    viewTreeObserver.addOnGlobalLayoutListener(object :
                        ViewTreeObserver.OnGlobalLayoutListener {
                        @OptIn(ExperimentalBadgeUtils::class) override fun onGlobalLayout() {
                            val badgeDrawable = BadgeDrawable.create(context)
                            badgeDrawable.number = item.downloadPath.size
                            //Important to change the position of the Badge
                            badgeDrawable.horizontalOffset = 25
                            badgeDrawable.verticalOffset = 25
                            BadgeUtils.attachBadgeDrawable(badgeDrawable, btn, null)
                            btn.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        }
                    })
                }

                setOnClickListener {
                    FileUtil.shareFileIntent(context, item.downloadPath)
                }
            }
        }

        val time = bottomSheet.findViewById<TextView>(R.id.time)
        val thumbnail = bottomSheet.findViewById<TextView>(R.id.thumbnail)
        val formatNote = bottomSheet.findViewById<TextView>(R.id.format_note)
        val container = bottomSheet.findViewById<Chip>(R.id.container_chip)
        val codec = bottomSheet.findViewById<TextView>(R.id.codec)
        val fileSize = bottomSheet.findViewById<TextView>(R.id.file_size)
        val command = bottomSheet.findViewById<Chip>(R.id.command)
        val location = bottomSheet.findViewById<Chip>(R.id.location)
        val file = File(item.downloadPath.first())

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = item.time * 1000L
        time!!.text = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(calendar.time)
        time.isClickable = false

        thumbnail?.isVisible = item.thumb.isNotBlank()
        thumbnail?.apply {
            isVisible = item.thumb.isNotBlank()
            setOnClickListener {
                openLinkIntent(context, item.thumb)
            }
        }

        if (item.type != DownloadViewModel.Type.command){
            if (item.format.format_note == "?" || item.format.format_note == "") formatNote!!.visibility =
                View.GONE
            else formatNote!!.text = item.format.format_note
        }else{
            formatNote?.isVisible = false
        }

        if (item.format.container != "" && item.downloadPath.size == 1) {
            container!!.text = if (file.exists()) file.extension.uppercase() else item.format.container.uppercase()
            container.setChipIconResource(typeImageResource)
        }else {
            container!!.visibility = View.GONE
        }

        val codecText =
            if (item.format.encoding != "") {
                item.format.encoding.uppercase()
            }else if (item.format.vcodec != "none" && item.format.vcodec != ""){
                item.format.vcodec.uppercase()
            } else {
                item.format.acodec.uppercase()
            }
        if (codecText == "" || codecText == "none" || item.downloadPath.size > 1){
            codec!!.visibility = View.GONE
        }else{
            codec!!.visibility = View.VISIBLE
            codec.text = codecText
        }

        val fileSizeReadable = FileUtil.convertFileSize(if (file.exists()) file.length() else item.format.filesize)
        if (fileSizeReadable == "?" || item.downloadPath.size > 1) fileSize!!.visibility = View.GONE
        else fileSize!!.text = fileSizeReadable

        command?.setOnClickListener {
            showGeneratedCommand(context, preferences, item.command)
        }

        val availableFiles = item.downloadPath.filter { FileUtil.exists(it) }
        location?.isVisible = availableFiles.isNotEmpty()
        location?.setOnClickListener {
            showFullTextDialog(context, availableFiles.joinToString("\n"), context.getString(R.string.location))
        }


        val link = bottomSheet.findViewById<Button>(R.id.bottom_sheet_link)
        val url = item.url
        link!!.text = url
        link.tag = item.id
        link.setOnClickListener{
            bottomSheet.dismiss()
            openLinkIntent(context, item.url)
        }
        link.setOnLongClickListener{
            bottomSheet.dismiss()
            copyLinkToClipBoard(context, item.url)
            true
        }
        val remove = bottomSheet.findViewById<Button>(R.id.bottomsheet_remove_button)
        remove!!.tag = item.id
        remove.setOnClickListener{
            showRemoveHistoryItemDialog(item, context, delete = removeItem)
            bottomSheet.dismiss()
        }
        val openFile = bottomSheet.findViewById<Button>(R.id.bottomsheet_open_file_button)
        openFile!!.tag = item.id
        openFile.setOnClickListener{
            if (item.downloadPath.size == 1) {
                FileUtil.openFileIntent(context, item.downloadPath.first())
            }else{
                openMultipleFilesIntent(context, item.downloadPath)
            }
        }

        val redownload = bottomSheet.findViewById<Button>(R.id.bottomsheet_redownload_button)
        redownload!!.tag = item.id
        redownload.setOnClickListener{
            redownloadItem(item)
            bottomSheet.cancel()
        }

        redownload.setOnLongClickListener {
            redownloadShowDownloadCard(item)
            bottomSheet.cancel()
            true
        }

        if (!isPresent) openFile.visibility = View.GONE
        else redownload.visibility = View.GONE

        bottomSheet.show()
        val displayMetrics = DisplayMetrics()
        context.windowManager.defaultDisplay.getMetrics(displayMetrics)
        bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    
    fun showFormatDetails(format: Format, activity: Activity){
        val bottomSheet = BottomSheetDialog(activity)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.format_details_sheet)

        val formatIdParent = bottomSheet.findViewById<ConstraintLayout>(R.id.format_id_parent)
        val formatURLParent = bottomSheet.findViewById<ConstraintLayout>(R.id.format_url_parent)
        val containerParent = bottomSheet.findViewById<ConstraintLayout>(R.id.container_parent)
        val codecParent = bottomSheet.findViewById<ConstraintLayout>(R.id.codec_parent)
        val filesizeParent = bottomSheet.findViewById<ConstraintLayout>(R.id.filesize_parent)
        val formatnoteParent = bottomSheet.findViewById<ConstraintLayout>(R.id.format_note_parent)
        val fpsParent = bottomSheet.findViewById<ConstraintLayout>(R.id.fps_parent)
        val asrParent = bottomSheet.findViewById<ConstraintLayout>(R.id.asr_parent)
        val bitrateParent = bottomSheet.findViewById<ConstraintLayout>(R.id.bitrate_parent)


        val clicker = View.OnClickListener {
            copyToClipboard(((it as ConstraintLayout).getChildAt(1) as TextView).text.toString(), activity)
        }

        val longClicker = View.OnLongClickListener {
            val txt = ((it as ConstraintLayout).getChildAt(1) as TextView).text.toString()
            val snackbar = Snackbar.make(bottomSheet.findViewById(android.R.id.content)!!, txt, Snackbar.LENGTH_LONG)
            snackbar.setAction(android.R.string.copy){
                copyToClipboard(txt, activity)
            }
            val snackbarView: View = snackbar.view
            val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
            snackTextView.maxLines = 9999999
            snackbar.show()
            true
        }

        fun String?.doesntExist() : Boolean { return this.isNullOrBlank() || this == "None"}

        if (format.format_id.doesntExist()) formatIdParent?.visibility = View.GONE
        else {
            formatIdParent?.findViewById<TextView>(R.id.format_id_value)?.text = format.format_id
            formatIdParent?.setOnClickListener(clicker)
            formatIdParent?.setOnLongClickListener(longClicker)
        }

        if (format.url.doesntExist()) formatURLParent?.visibility = View.GONE
        else {
            formatURLParent?.findViewById<TextView>(R.id.format_url_value)?.text = format.url
            formatURLParent?.setOnClickListener(clicker)
            formatURLParent?.setOnLongClickListener(longClicker)
        }


        if (format.container.doesntExist()) containerParent?.visibility = View.GONE
        else {
            containerParent?.findViewById<TextView>(R.id.container_value)?.text = format.container
            containerParent?.setOnClickListener(clicker)
            containerParent?.setOnLongClickListener(longClicker)
        }

        val codecField =
            if (format.encoding != "") {
                format.encoding.uppercase()
            }else if (format.vcodec != "none" && format.vcodec != ""){
                format.vcodec.uppercase()
            } else {
                format.acodec.uppercase()
            }

        if (codecField.doesntExist()) codecParent?.visibility = View.GONE
        else {
            codecParent?.findViewById<TextView>(R.id.codec_value)?.text = codecField
            codecParent?.setOnClickListener(clicker)
            codecParent?.setOnLongClickListener(longClicker)
        }

        if (format.filesize != 0L) filesizeParent?.visibility = View.GONE
        else {
            filesizeParent?.findViewById<TextView>(R.id.filesize_value)?.text = FileUtil.convertFileSize(format.filesize)
            filesizeParent?.setOnClickListener(clicker)
            filesizeParent?.setOnLongClickListener(longClicker)
        }

        if (format.format_note.doesntExist()) formatnoteParent?.visibility = View.GONE
        else {
            formatnoteParent?.findViewById<TextView>(R.id.format_note_value)?.text = format.format_note
            formatnoteParent?.setOnClickListener(clicker)
            formatnoteParent?.setOnLongClickListener(longClicker)
        }

        if (format.fps.doesntExist() || format.fps == "0") fpsParent?.visibility = View.GONE
        else {
            fpsParent?.findViewById<TextView>(R.id.fps_value)?.text = format.fps
            fpsParent?.setOnClickListener(clicker)
            fpsParent?.setOnLongClickListener(longClicker)
        }

        if (format.asr.doesntExist()) asrParent?.visibility = View.GONE
        else {
            asrParent?.findViewById<TextView>(R.id.asr_value)?.text = format.asr
            asrParent?.setOnClickListener(clicker)
            asrParent?.setOnLongClickListener(longClicker)
        }

        if (format.tbr.doesntExist()) bitrateParent?.visibility = View.GONE
        else{
            bitrateParent?.findViewById<TextView>(R.id.bitrate_value)?.text = format.tbr
            bitrateParent?.setOnClickListener(clicker)
            bitrateParent?.setOnLongClickListener(longClicker)
        }



        bottomSheet.show()
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    @SuppressLint("RestrictedApi")
    fun showSubtitleLanguagesDialog(context: Activity, currentValue: String, ok: (newValue: String) -> Unit){
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(context.getString(R.string.subtitle_languages))
        val view = context.layoutInflater.inflate(R.layout.subtitle_dialog, null)
        val editText = view.findViewById<EditText>(R.id.subtitle_edittext)
        view.findViewById<TextInputLayout>(R.id.subtitle).hint = context.getString(R.string.subtitle_languages)
        editText.setText(currentValue)
        editText.setSelection(editText.text.length)
        builder.setView(view)
        builder.setPositiveButton(
            context.getString(R.string.ok)
        ) { _: DialogInterface?, _: Int ->
            ok(editText.text.toString())
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            context.getString(R.string.cancel)
        ) { _: DialogInterface?, _: Int -> }

        builder.setNeutralButton("?")  { _: DialogInterface?, _: Int ->
            val browserIntent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yt-dlp/yt-dlp#subtitle-options"))
            context.startActivity(browserIntent)
        }

        view.findViewById<View>(R.id.suggested).visibility = View.GONE

        val dialog = builder.create()
        dialog.show()
        val imm = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        editText!!.postDelayed({
            editText.requestFocus()
            imm.showSoftInput(editText, 0)
        }, 300)

        //handle suggestion chips
        CoroutineScope(Dispatchers.IO).launch {
            val chipGroup = view.findViewById<ChipGroup>(R.id.subtitle_suggested_chipgroup)
            val chips = mutableListOf<Chip>()
            context.getStringArray(R.array.subtitle_langs).forEachIndexed { index, s ->
                val tmp = context.layoutInflater.inflate(R.layout.filter_chip, chipGroup, false) as Chip
                s.split("-", ".*").run {
                    if (s.endsWith(".*")) {
                        val name = "${Locale(this[0]).displayName} (${context.getString(R.string.all)})"
                        tmp.text = name
                    }else{
                        if (this.size == 1) {
                            Locale(this[0])
                        } else {
                            Locale(this[0], this[1])
                        }.apply {
                            tmp.text = this.displayName
                        }
                    }
                }

                tmp.tag = s
                tmp.id = index

                tmp.setOnClickListener {
                    val c = it as Chip
                    val currentLanguages = editText.text.toString().split(",").filter { f -> f.isNotBlank() }.toMutableList()
                    if(!c.isChecked){
                        editText.setText(currentLanguages.filter { l -> l != c.tag }.joinToString(","))
                        editText.setSelection(editText.text.length)
                    }else{
                        currentLanguages.add(c.tag.toString())
                        editText.setText(currentLanguages.joinToString(","))
                        editText.setSelection(editText.text.length)
                    }
                }

                chips.add(tmp)
            }
            withContext(Dispatchers.Main){
                view.findViewById<View>(R.id.suggested).visibility = View.VISIBLE
                chips.forEach {
                    it.isChecked = editText.text.split(",").any { it2 -> it2 == it.tag.toString() }
                    chipGroup!!.addView(it)
                }

                editText.textChanged {
                    chipGroup.children.forEach { (it as Chip).isChecked = editText.text.split(",").any { it2 -> it2 == it.tag.toString() } }
                }
            }
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
    }


    fun copyToClipboard(text: String, activity: Activity){
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(text, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(activity, activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }


    suspend fun showCommandTemplates(activity: Activity, commandTemplateViewModel: CommandTemplateViewModel, onlyOne: Boolean = false, itemSelected: (itemSelected: List<CommandTemplate>) -> Unit) {
        val bottomSheet = BottomSheetDialog(activity)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.command_template_list)

        val linearLayout = bottomSheet.findViewById<LinearLayout>(R.id.command_list_linear_layout)
        val list = withContext(Dispatchers.IO){
            commandTemplateViewModel.getAll()
        }

        linearLayout!!.removeAllViews()
        val selectedItems = mutableListOf<CommandTemplate>()
        val ok = bottomSheet.findViewById<MaterialButton>(R.id.command_ok)
        ok?.isVisible = !onlyOne
        ok?.isEnabled = list.size == 1

        list.forEach {template ->
            val item = activity.layoutInflater.inflate(R.layout.command_template_item, linearLayout, false) as MaterialCardView
            item.findViewById<TextView>(R.id.title).text = template.title
            item.findViewById<TextView>(R.id.content).text = template.content
            item.setOnClickListener {
                if (selectedItems.contains(template)){
                    selectedItems.remove(template)
                    (it as MaterialCardView).isChecked = false
                }else{
                    selectedItems.add(template)
                    (it as MaterialCardView).isChecked = true
                }

                if (onlyOne) {
                    itemSelected(listOf(template))
                    bottomSheet.cancel()
                }

                ok?.isEnabled = selectedItems.isNotEmpty()
            }
            linearLayout.addView(item)
        }

        ok?.setOnClickListener {
            itemSelected(selectedItems.ifEmpty { listOf(list.first()) })
            bottomSheet.cancel()
        }

        bottomSheet.setOnShowListener {
            val behavior = bottomSheet.behavior
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels / 2
        }

        bottomSheet.show()

    }

    suspend fun showShortcuts(activity: Activity, commandTemplateViewModel: CommandTemplateViewModel, itemSelected: (itemSelected: String) -> Unit, itemRemoved: (itemRemoved: String) -> Unit){
        val bottomSheet = BottomSheetDialog(activity)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.template_shortcuts_list)

        val chipGroup = bottomSheet.findViewById<ChipGroup>(R.id.shortcutsChipGroup)
        val shortcutList = withContext(Dispatchers.IO){
            commandTemplateViewModel.getAllShortcuts()
        }

        chipGroup!!.removeAllViews()
        shortcutList.forEach {shortcut ->
            val chip = activity.layoutInflater.inflate(R.layout.suggestion_chip, chipGroup, false) as Chip
            chip.text = shortcut.content
            chip.setOnClickListener {
                chip.isChecked = false
                itemSelected(shortcut.content)
            }
            chipGroup.addView(chip)
        }

        bottomSheet.setOnShowListener {
            val behavior = bottomSheet.behavior
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels / 3
        }

        bottomSheet.show()

    }

    fun configureVideo(
        view: View,
        context: Activity,
        items: List<DownloadItem>,
        embedSubsClicked : (Boolean) -> Unit,
        addChaptersClicked: (Boolean) -> Unit,
        splitByChaptersClicked: (Boolean) -> Unit,
        saveThumbnailClicked: (Boolean) -> Unit,
        sponsorBlockItemsSet: (values: Array<String>, checkedItems: List<Boolean>) -> Unit,
        cutClicked: (VideoCutListener) -> Unit,
        cutValueChanged: (String) -> Unit,
        cutDisabledClicked: () -> Unit,
        filenameTemplateSet: (String) -> Unit,
        saveSubtitlesClicked: (Boolean) -> Unit,
        saveAutoSubtitlesClicked: (Boolean) -> Unit,
        subtitleLanguagesSet: (String) -> Unit,
        removeAudioClicked: (Boolean) -> Unit,
        recodeVideoClicked: (Boolean) -> Unit,
        alsoDownloadAsAudioClicked: (Boolean) -> Unit,
        extraCommandsClicked: () -> Unit,
        liveFromStart: (Boolean) -> Unit,
        waitForVideo: (Boolean, Int) -> Unit,
    ){
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val addChapters = view.findViewById<Chip>(R.id.add_chapters)
        addChapters!!.isChecked = items.all { it.videoPreferences.addChapters }
        addChapters.setOnClickListener{
            addChaptersClicked(addChapters.isChecked)
        }

        val splitByChapters = view.findViewById<Chip>(R.id.split_by_chapters)
        if(items.size == 1 && items[0].downloadSections.isNotBlank()){
            splitByChapters.isEnabled = false
            splitByChapters.isChecked = false
        }else{
            splitByChapters!!.isChecked = items.all { it.videoPreferences.splitByChapters }
        }
        if (splitByChapters.isChecked){
            items.forEach { it.videoPreferences.addChapters = false }
            addChapters.isChecked = false
            addChapters.isEnabled = false
            addChaptersClicked(false)
        }
        splitByChapters.setOnClickListener {
            if (splitByChapters.isChecked){
                addChapters.isEnabled = false
                addChapters.isChecked = false
                addChaptersClicked(false)
            }else{
                addChapters.isEnabled = true
            }
            splitByChaptersClicked(splitByChapters.isChecked)
        }

        val saveThumbnail = view.findViewById<Chip>(R.id.save_thumbnail)
        saveThumbnail!!.isChecked = items.all { it.SaveThumb }
        saveThumbnail.setOnClickListener {
            saveThumbnailClicked(saveThumbnail.isChecked)
        }


        val adjustSubtitles = view.findViewById<Chip>(R.id.adjust_subtitles)
        adjustSubtitles.setOnClickListener {
            val adjustSubtitleView = context.layoutInflater.inflate(R.layout.subtitle_download_preferences_dialog, null)
            val embedSubs = adjustSubtitleView.findViewById<MaterialSwitch>(R.id.embed_subtitles)
            val saveSubtitles = adjustSubtitleView.findViewById<MaterialSwitch>(R.id.save_subs)
            val saveAutoSubtitles = adjustSubtitleView.findViewById<MaterialSwitch>(R.id.save_auto_subs)
            val subtitleLanguages = adjustSubtitleView.findViewById<ConstraintLayout>(R.id.subtitle_languages)
            val subtitleLanguagesDescription = adjustSubtitleView.findViewById<TextView>(R.id.subtitle)
            subtitleLanguagesDescription.text = items.first().videoPreferences.subsLanguages
            subtitleLanguages.isClickable = embedSubs.isChecked || saveSubtitles.isChecked

            embedSubs!!.isChecked = items.all { it.videoPreferences.embedSubs }
            embedSubs.setOnClickListener {
                subtitleLanguages.isClickable = embedSubs.isChecked || saveSubtitles.isChecked
                embedSubsClicked(embedSubs.isChecked)
            }

            if (items.all { it.videoPreferences.writeSubs}) {
                saveSubtitles.isChecked = true
                subtitleLanguages.visibility = View.VISIBLE
            }

            if (items.all { it.videoPreferences.writeAutoSubs}) {
                saveAutoSubtitles.isChecked = true
                subtitleLanguages.visibility = View.VISIBLE
            }

            saveSubtitles.setOnCheckedChangeListener { _, _ ->
                subtitleLanguages.isClickable = embedSubs.isChecked || saveSubtitles.isChecked || saveAutoSubtitles.isChecked
                saveSubtitlesClicked(saveSubtitles.isChecked)
            }

            saveAutoSubtitles.setOnCheckedChangeListener { _, _ ->
                subtitleLanguages.isClickable = embedSubs.isChecked || saveSubtitles.isChecked || saveAutoSubtitles.isChecked
                saveAutoSubtitlesClicked(saveAutoSubtitles.isChecked)
            }

            subtitleLanguages.isClickable = embedSubs.isChecked || saveSubtitles.isChecked
            subtitleLanguages.setOnClickListener {
                val currentSubtitleLang = if (items.size == 1 || items.all { it.videoPreferences.subsLanguages == items[0].videoPreferences.subsLanguages }){
                    items[0].videoPreferences.subsLanguages
                }else {
                    ""
                }.ifEmpty { sharedPreferences.getString("subs_lang", "en.*,.*-orig")!! }

                showSubtitleLanguagesDialog(context, currentSubtitleLang){
                    subtitleLanguagesSet(it)
                    subtitleLanguagesDescription.text = it
                }
            }

            val adjustSubtitleDialog = MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.subtitles))
                .setView(adjustSubtitleView)
                .setIcon(R.drawable.ic_subtitles)
                .setNegativeButton(context.resources.getString(R.string.dismiss)) { _: DialogInterface?, _: Int -> }

            adjustSubtitleDialog.show()
        }

        if (items.size == 1 && items.first().id == 0L){
            val adjustAudio = view.findViewById<Chip>(R.id.adjust_audio)
            adjustAudio.setOnClickListener {
                val adjustAudioView = context.layoutInflater.inflate(R.layout.audio_download_preferences_dialog, null)
                adjustAudioView.findViewById<MaterialSwitch>(R.id.remove_audio).apply {
                    isChecked = items.first().videoPreferences.removeAudio
                    setOnCheckedChangeListener { _, b ->
                        removeAudioClicked(b)
                    }
                }

                adjustAudioView.findViewById<MaterialSwitch>(R.id.also_download_audio).apply {
                    isChecked = items.first().videoPreferences.alsoDownloadAsAudio
                    setOnCheckedChangeListener { _, b ->
                        alsoDownloadAsAudioClicked(b)
                    }
                }

                val adjustAudioDialog = MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.audio))
                    .setView(adjustAudioView)
                    .setIcon(R.drawable.ic_music)
                    .setNegativeButton(context.resources.getString(R.string.dismiss)) { _: DialogInterface?, _: Int -> }

                adjustAudioDialog.show()
            }
        }else{
            val adjustAudio = view.findViewById<Chip>(R.id.adjust_audio)
            adjustAudio.isVisible = false
            val removeAudio = view.findViewById<Chip>(R.id.remove_audio)
            removeAudio.isVisible = true
            removeAudio.isChecked = items.all { it.videoPreferences.removeAudio }
            removeAudio.setOnCheckedChangeListener { _, _ ->
                removeAudioClicked(removeAudio.isChecked)
            }
        }

        val adjustLiveStream = view.findViewById<Chip>(R.id.adjust_live_stream)
        if (items.size == 1) {
            adjustLiveStream.setOnClickListener {
                val adjustLiveStreamView = context.layoutInflater.inflate(R.layout.livestream_download_preferences_dialog, null)
                val liveFromStartSwitch = adjustLiveStreamView.findViewById<MaterialSwitch>(R.id.live_from_start)
                val waitForVideoSwitch = adjustLiveStreamView.findViewById<MaterialSwitch>(R.id.wait_for_video)
                val waitForVideoSettings = adjustLiveStreamView.findViewById<View>(R.id.wait_for_video_settings)

                val waitEveryNr = adjustLiveStreamView.findViewById<TextInputLayout>(R.id.every_nr).editText!!

                val item = items.first()

                item.videoPreferences.waitForVideoMinutes.apply {
                    if(this > 0) {
                        waitEveryNr.setText(this.toString())
                    }
                }

                waitEveryNr.doOnTextChanged { text, start, before, count ->
                    var number = 1
                    kotlin.runCatching {
                        number = Integer.parseInt(waitEveryNr.text.toString())
                    }

                    waitForVideo(true, number)
                }

                liveFromStartSwitch.setOnCheckedChangeListener { compoundButton, b ->
                    liveFromStart(b)
                }

                waitForVideoSwitch.setOnCheckedChangeListener { compoundButton, b ->
                    waitForVideoSettings.isVisible = b

                    var number = 1
                    kotlin.runCatching {
                        number = Integer.parseInt(waitEveryNr.text.toString())
                    }

                    waitForVideo(b, number)
                }

                liveFromStartSwitch.isChecked = item.videoPreferences.liveFromStart
                waitForVideoSwitch.isChecked = item.videoPreferences.waitForVideoMinutes > 0
                waitForVideoSettings.isVisible = waitForVideoSwitch.isChecked

                val adjustLiveStreamDialog = MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.live_stream))
                    .setView(adjustLiveStreamView)
                    .setIcon(R.drawable.baseline_live_tv_24)
                    .setNegativeButton(context.resources.getString(R.string.dismiss)) { _: DialogInterface?, _: Int -> }

                adjustLiveStreamDialog.show()
            }
        }else {
            adjustLiveStream.isVisible = false
        }

        val recodeVideo = view.findViewById<Chip>(R.id.recode_video)
        recodeVideo.isChecked = items.all { it.videoPreferences.recodeVideo }
        recodeVideo.setOnCheckedChangeListener { _, _ ->
            recodeVideoClicked(recodeVideo.isChecked)
        }


        val sponsorBlock = view.findViewById<Chip>(R.id.sponsorblock_filters)
        sponsorBlock.isEnabled = sharedPreferences.getBoolean("use_sponsorblock", true)
        sponsorBlock!!.setOnClickListener {
            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(context.getString(R.string.select_sponsorblock_filtering))
            val values = context.resources.getStringArray(R.array.sponsorblock_settings_values)
            val entries = context.resources.getStringArray(R.array.sponsorblock_settings_entries)
            val checkedItems : ArrayList<Boolean> = arrayListOf()
            values.forEach {
                if (items.all{ i -> i.videoPreferences.sponsorBlockFilters.contains(it)}) {
                    checkedItems.add(true)
                }else{
                    checkedItems.add(false)
                }
            }

            builder.setMultiChoiceItems(
                entries,
                checkedItems.toBooleanArray()
            ) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }

            builder.setPositiveButton(
                context.getString(R.string.ok)
            ) { _: DialogInterface?, _: Int ->
                sponsorBlockItemsSet(values, checkedItems)
            }

            // handle the negative button of the alert dialog
            builder.setNegativeButton(
                context.getString(R.string.cancel)
            ) { _: DialogInterface?, _: Int -> }

            val dialog = builder.create()
            dialog.show()
        }

        val cut = view.findViewById<Chip>(R.id.cut)
        if (items.size > 1 || items.first().url.isEmpty()) cut.isVisible = false
        else{
            cut.setOnClickListener(null)
            val invalidDuration = items[0].duration == "-1"
            if(items[0].duration.isNotEmpty() && !invalidDuration){
                val downloadItem = items[0]
                cut.alpha = 1f
                if (downloadItem.downloadSections.isNotBlank()) cut.text = downloadItem.downloadSections
                val cutVideoListener = object : VideoCutListener {

                    override fun onChangeCut(list: List<String>) {
                        if (list.isEmpty()){
                            cutValueChanged("")
                            cut.text = context.getString(R.string.cut)

                            splitByChapters.isEnabled = true
                            splitByChapters.isChecked = downloadItem.videoPreferences.splitByChapters
                            if (splitByChapters.isChecked){
                                addChapters.isEnabled = false
                                addChapters.isChecked = false
                            }else{
                                addChapters.isEnabled = true
                            }
                        }else{
                            var value = ""
                            list.forEach {
                                value += "$it;"
                            }
                            cutValueChanged(value)
                            cut.text = value.dropLast(1)

                            splitByChapters.isEnabled = false
                            splitByChapters.isChecked = false
                            addChapters.isEnabled = true
                        }

                    }
                }
                cut.setOnClickListener {
                    cutClicked(cutVideoListener)
                }
            }else{
                cut.alpha = 0.3f
                if (!invalidDuration) {
                    cut.setOnClickListener {
                        cutDisabledClicked()
                    }
                }
            }
        }

        val filenameTemplate = view.findViewById<Chip>(R.id.filename_template)
        filenameTemplate.setOnClickListener {
            val currentFilename = if (items.size == 1 || items.all { it.customFileNameTemplate == items[0].customFileNameTemplate }){
                items[0].customFileNameTemplate
            }else {
                ""
            }
            showFilenameTemplateDialog(context, currentFilename) {
                filenameTemplateSet(it)
            }
        }

        val extraCommands = view.findViewById<Chip>(R.id.extra_commands)
        if (sharedPreferences.getBoolean("use_extra_commands", false)){
            extraCommands.visibility = View.VISIBLE
            extraCommands.setOnClickListener {
                extraCommandsClicked()
            }
        }else{
            extraCommands.visibility = View.GONE
        }
    }

    fun configureAudio(
        view: View,
        context: Activity,
        items: List<DownloadItem>,
        embedThumbClicked: (Boolean) -> Unit,
        cropThumbClicked: (Boolean) -> Unit,
        splitByChaptersClicked: (Boolean) -> Unit,
        filenameTemplateSet: (String) -> Unit,
        sponsorBlockItemsSet: (Array<String>, List<Boolean>) -> Unit,
        cutClicked: (VideoCutListener) -> Unit,
        cutDisabledClicked: () -> Unit,
        cutValueChanged: (String) -> Unit,
        extraCommandsClicked: () -> Unit
    ){
        val embedThumb = view.findViewById<Chip>(R.id.embed_thumb)
        val cropThumb = view.findViewById<Chip>(R.id.crop_thumb)

        embedThumb!!.isChecked = items.all { it.audioPreferences.embedThumb }
        cropThumb.isVisible = embedThumb.isChecked
        embedThumb.setOnClickListener {
            embedThumbClicked(embedThumb.isChecked)
            cropThumb.isVisible = embedThumb.isChecked
        }

        cropThumb!!.isChecked = items.all { it.audioPreferences.cropThumb == true }
        cropThumb.setOnClickListener {
            cropThumbClicked(cropThumb.isChecked)
        }

        val splitByChapters = view.findViewById<Chip>(R.id.split_by_chapters)
        if (items.size == 1 && items[0].downloadSections.isNotBlank()){
            splitByChapters.isEnabled = false
            splitByChapters.isChecked = false
        }else{
            splitByChapters!!.isChecked = items.all { it.audioPreferences.splitByChapters }
        }

        splitByChapters.setOnClickListener {
            splitByChaptersClicked(splitByChapters.isChecked)
        }

        val filenameTemplate = view.findViewById<Chip>(R.id.filename_template)
        filenameTemplate.setOnClickListener {
            val currentFilename = if (items.size == 1 || items.all { it.customFileNameTemplate == items[0].customFileNameTemplate }){
                items[0].customFileNameTemplate
            }else {
                ""
            }
            showFilenameTemplateDialog(context, currentFilename) {
                filenameTemplateSet(it)
            }
        }

        val sponsorBlock = view.findViewById<Chip>(R.id.sponsorblock_filters)
        sponsorBlock!!.setOnClickListener {
            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(context.getString(R.string.select_sponsorblock_filtering))
            val values = context.resources.getStringArray(R.array.sponsorblock_settings_values)
            val entries = context.resources.getStringArray(R.array.sponsorblock_settings_entries)
            val checkedItems : ArrayList<Boolean> = arrayListOf()
            values.forEach {
                if (items.all{ i -> i.audioPreferences.sponsorBlockFilters.contains(it)}) {
                    checkedItems.add(true)
                }else{
                    checkedItems.add(false)
                }
            }

            builder.setMultiChoiceItems(
                entries,
                checkedItems.toBooleanArray()
            ) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }

            builder.setPositiveButton(
                context.getString(R.string.ok)
            ) { _: DialogInterface?, _: Int ->
                sponsorBlockItemsSet(values, checkedItems)
            }

            // handle the negative button of the alert dialog
            builder.setNegativeButton(
                context.getString(R.string.cancel)
            ) { _: DialogInterface?, _: Int -> }

            val dialog = builder.create()
            dialog.show()
        }

        val cut = view.findViewById<Chip>(R.id.cut)
        if (items.size > 1 || items.first().url.isEmpty()) cut.isVisible = false
        else{
            cut.setOnClickListener(null)
            val downloadItem = items[0]
            val invalidDuration = downloadItem.duration == "-1"
            if (downloadItem.duration.isNotEmpty() && !invalidDuration){
                cut.alpha = 1f
                if (downloadItem.downloadSections.isNotBlank()) cut.text = downloadItem.downloadSections
                val cutVideoListener = object : VideoCutListener {
                    override fun onChangeCut(list: List<String>) {
                        if (list.isEmpty()){
                            cutValueChanged("")
                            cut.text = context.getString(R.string.cut)

                            splitByChapters.isEnabled = true
                            splitByChapters.isChecked = downloadItem.audioPreferences.splitByChapters
                        }else{
                            var value = ""
                            list.forEach {
                                value += "$it;"
                            }
                            cutValueChanged(value)
                            cut.text = value.dropLast(1)

                            splitByChapters.isEnabled = false
                            splitByChapters.isChecked = false
                        }
                    }
                }
                cut.setOnClickListener {
                    cutClicked(cutVideoListener)
                }

            }else{
                cut.alpha = 0.3f
                if (!invalidDuration) {
                    cut.setOnClickListener {
                        cutDisabledClicked()
                    }
                }
            }
        }



        val extraCommands = view.findViewById<Chip>(R.id.extra_commands)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (sharedPreferences.getBoolean("use_extra_commands", false)){
            extraCommands.visibility = View.VISIBLE
            extraCommands.setOnClickListener {
                extraCommandsClicked()
            }
        }else{
            extraCommands.visibility = View.GONE
        }
    }

    fun configureCommand(
        view: View,
        size: Int,
        shortcutCount: Int,
        newTemplateClicked: () -> Unit,
        editSelectedClicked: () -> Unit,
        shortcutClicked: suspend () -> Unit,
    ){
        val newTemplate : Chip = view.findViewById(R.id.newTemplate)
        newTemplate.setOnClickListener {
            newTemplateClicked()
        }

        val editSelected : Chip = view.findViewById(R.id.editSelected)
        editSelected.isEnabled = size == 1
        editSelected.setOnClickListener {
            editSelectedClicked()
        }

        val shortcuts = view.findViewById<View>(R.id.shortcut)
        shortcuts.isEnabled = shortcutCount > 0
        shortcuts.setOnClickListener {
            runBlocking {
                shortcutClicked()
            }
        }
    }

    fun handleNoResults(context: Activity, message: String, url: String? = null, continueAnyway: Boolean = false, continued: () -> Unit, closed: () -> Unit, cookieFetch: () -> Unit) {
        val errDialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.no_results)
            .setMessage(message)

        errDialog.setPositiveButton(R.string.copy_log) { d:DialogInterface?, _:Int ->
            val clipboard: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setText(message)
            d?.dismiss()
        }

        val cookieRelated = message.contains("cookie", true) || message.contains("sign in", true)
        if (cookieRelated && !url.isNullOrBlank()) {
            errDialog.setNeutralButton(context.getString(R.string.get_cookies)) { d: DialogInterface?, _ : Int ->
                cookieFetch()
            }
        }else if (continueAnyway) {
            errDialog.setNeutralButton(R.string.continue_anyway) {d: DialogInterface?, _:Int ->
                continued()
            }
        }



        errDialog.setOnCancelListener {
            closed()
        }

        errDialog.show()
    }

    fun showErrorDialog(context: Context, it: String){
        val title = context.getString(R.string.errored)

        val errDialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(it)
            .setPositiveButton(android.R.string.copy) { d:DialogInterface?, _:Int ->
                val clipboard: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setText(it)
                d?.dismiss()
            }

        errDialog.show()
    }

    fun showGenericDeleteDialog(context: Context, itemTitle: String, accepted: () -> Unit){
        val deleteDialog = MaterialAlertDialogBuilder(context)
        deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete) + " \"" + itemTitle + "\"!")
        deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            accepted()
        }
        deleteDialog.show()
    }

    fun showGenericConfirmDialog(context: Context, title: String, content:String, accepted: () -> Unit){
        val deleteDialog = MaterialAlertDialogBuilder(context)
        deleteDialog.setTitle(title)
        deleteDialog.setMessage(content)
        deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(context.getString(R.string.continue_anyway)) { _: DialogInterface?, _: Int ->
            accepted()
        }
        deleteDialog.show()
    }

    fun showGenericDeleteAllDialog(context: Context, accepted: () -> Unit){
        val deleteDialog = MaterialAlertDialogBuilder(context)
        deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete_multiple_items))
        deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            accepted()
        }
        deleteDialog.show()
    }

    fun showRemoveHistoryItemDialog(item: HistoryItem, context: Activity, delete: (item: HistoryItem, deleteFile: Boolean) -> Unit){
        val deleteFile = booleanArrayOf(false)
        val deleteDialog = MaterialAlertDialogBuilder(context)
        deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
        val path = item.downloadPath
        if (path.any { File(it).exists() && it.isNotEmpty() }) {
            deleteDialog.setMultiChoiceItems(
                arrayOf(context.getString(R.string.delete_file_too)),
                booleanArrayOf(false)
            ) { _: DialogInterface?, _: Int, b: Boolean -> deleteFile[0] = b }
        }
        deleteDialog.setNegativeButton(context.getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(context.getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            delete(item, deleteFile[0])
        }
        deleteDialog.show()
    }

    @SuppressLint("RestrictedApi")
    fun showFilenameTemplateDialog(context: Activity, currentFilename: String, dialogTitle: String = context.getString(R.string.file_name_template), filenameSelected: (f: String) -> Unit){
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(dialogTitle)
        val view = context.layoutInflater.inflate(R.layout.filename_template_dialog, null)
        val textInput = view.findViewById<TextInputLayout>(R.id.filename)
        val editText = view.findViewById<EditText>(R.id.filename_edittext)
        textInput.hint = context.getString(R.string.file_name_template)
        editText.setText(currentFilename)
        editText.setSelection(editText.text.length)
        builder.setView(view)
        builder.setPositiveButton(
            context.getString(R.string.ok)
        ) { _: DialogInterface?, _: Int ->
            filenameSelected(editText.text.toString())
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            context.getString(R.string.cancel)
        ) { _: DialogInterface?, _: Int -> }

        builder.setNeutralButton("?")  { _: DialogInterface?, _: Int ->
            val browserIntent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yt-dlp/yt-dlp#user-content-outtmpl-postprocess-note"))
            context.startActivity(browserIntent)
        }

        val dialog = builder.create()
        dialog.show()
        val imm = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        editText!!.postDelayed({
            editText.requestFocus()
            imm.showSoftInput(editText, 0)
        }, 300)


        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val myTemplates = preferences.getStringSet("filename_templates", setOf())!!.toMutableSet()
        val myTemplatesView = view.findViewById<View>(R.id.mytemplates)
        val myTemplatesChipGroup = view.findViewById<ChipGroup>(R.id.filename_personal_chipgroup)

        textInput.setEndIconOnClickListener {
            if (editText.text.isBlank()) return@setEndIconOnClickListener
            val chip = createPersonalFilenameTemplateChip(context, editText.text.toString(), myTemplatesChipGroup,
                onClick = { chip ->
                    if (chip.isChecked){
                        editText.setText(chip.text.toString())
                        val selectionStart = editText.selectionStart
                        editText.setSelection(selectionStart + chip.text.length)
                    }else{
                        editText.setText("")
                    }

                },
                onLongClick = {
                    showGenericDeleteDialog(context, it.text.toString()) {
                        myTemplates.remove(it.text.toString())
                        myTemplatesView.isVisible = myTemplates.isNotEmpty()
                        myTemplatesChipGroup.removeView(it)
                        preferences.edit().putStringSet("filename_templates", myTemplates).apply()
                    }
                }
            )
            chip.isChecked = true
            textInput.endIconDrawable = null
            myTemplates.add(editText.text.toString())
            preferences.edit().putStringSet("filename_templates", myTemplates).apply()
            myTemplatesChipGroup.addView(chip)
            myTemplatesView.isVisible = true
        }

        CoroutineScope(Dispatchers.IO).launch {
            //handle personal template chips
            val mychips = mutableListOf<Chip>()
            myTemplates.forEachIndexed { index, s ->
                val chip = createPersonalFilenameTemplateChip(context, s, myTemplatesChipGroup,
                    onClick = { c ->
                        if (c.isChecked){
                            editText.setText(c.text.toString())
                            val selectionStart = editText.selectionStart
                            editText.setSelection(selectionStart + c.text.length)
                        }else{
                            editText.setText("")
                        }
                    },
                    onLongClick = { c ->
                        showGenericDeleteDialog(context, c.text.toString()) {
                            myTemplates.remove(c.text.toString())
                            myTemplatesView.isVisible = myTemplates.isNotEmpty()
                            myTemplatesChipGroup.removeView(c)
                            preferences.edit().putStringSet("filename_templates", myTemplates).apply()
                        }
                    }
                )
                mychips.add(chip)
            }
            //handle suggestion chips
            val chipGroup = view.findViewById<ChipGroup>(R.id.filename_suggested_chipgroup)
            val chips = mutableListOf<Chip>()
            context.getStringArray(R.array.filename_templates).forEachIndexed { index, s ->
                val tmp = context.layoutInflater.inflate(R.layout.filter_chip, chipGroup, false) as Chip
                tmp.text = s.split("___")[0]
                tmp.id = index
                if (Build.VERSION.SDK_INT >= 26){
                    tmp.tooltipText = s.split("___")[1]
                }

                tmp.setOnClickListener {
                    val c = it as Chip
                    val selectionStart = editText.selectionStart
                    editText.text.insert(selectionStart, c.text.toString())
                    editText.setSelection(selectionStart + c.text.length)
                }

                chips.add(tmp)
            }


            withContext(Dispatchers.Main){
                if (mychips.isNotEmpty()){
                    myTemplatesView.isVisible = true
                    mychips.forEach {
                        it.isChecked = editText.text.contains(it.text)
                        myTemplatesChipGroup!!.addView(it)
                    }
                }


                view.findViewById<View>(R.id.suggested).visibility = View.VISIBLE
                chips.forEach {
                    it.isChecked = editText.text.contains(it.text)
                    chipGroup!!.addView(it)
                }

                editText.doOnTextChanged { text, start, before, count ->
                    chips.forEach {
                        it.isChecked = editText.text.contains(it.text)
                    }

                    mychips.forEach {
                        it.isChecked = editText.text.contains(it.text)
                    }

                    if (!myTemplates.contains(editText.text.toString()) && editText.text.isNotBlank()){
                        textInput.endIconDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_plus)
                    }else{
                        textInput.endIconDrawable = null
                    }
                }
            }
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
    }

    private fun createPersonalFilenameTemplateChip(context: Activity, text: String, myChipGroup: ChipGroup, onClick: (f: Chip) -> Unit, onLongClick: (f: Chip) -> Unit) : Chip {
        val tmp = context.layoutInflater.inflate(R.layout.filter_chip, myChipGroup, false) as Chip
        tmp.text = text
        if (Build.VERSION.SDK_INT >= 26){
            tmp.tooltipText = text
        }

        tmp.setOnClickListener {
            onClick(it as Chip)
        }

        tmp.setOnLongClickListener {
            onLongClick(it as Chip)
            true
        }

        return tmp

    }

    private fun showGeneratedCommand(context: Activity, preferences: SharedPreferences, command: String) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(context.getString(R.string.command))
        val view = context.layoutInflater.inflate(R.layout.command_dialog, null)
        view.findViewById<TextView>(R.id.commandText).apply {
            text = command
            isLongClickable = true
            setTextIsSelectable(true)
            if (preferences.getBoolean("use_code_color_highlighter", true)) {
                enableTextHighlight()
            }
            setPadding(20, 0, 20, 0)
        }

        builder.setView(view)
        builder.setPositiveButton(
            context.getString(android.R.string.copy)
        ) { _: DialogInterface?, _: Int ->
            copyToClipboard(command, context)
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            context.getString(R.string.dismiss)
        ) { _: DialogInterface?, _: Int -> }

        val dialog = builder.create()
        dialog.show()
    }

    private fun showFullTextDialog(context: Activity, txt: String, title: String){
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)
        val view = context.layoutInflater.inflate(R.layout.command_dialog, null)
        val text = view.findViewById<TextView>(R.id.commandText)
        text.text = txt
        text.isLongClickable = true
        text.setTextIsSelectable(true)

        builder.setView(view)
        builder.setPositiveButton(
            context.getString(android.R.string.copy)
        ) { _: DialogInterface?, _: Int ->
            copyToClipboard(txt, context)
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            context.getString(R.string.dismiss)
        ) { _: DialogInterface?, _: Int -> }

        val dialog = builder.create()
        dialog.show()
    }


    fun getAlphaAnimator(view: View, alphaTo: Float): Animator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, alphaTo)
    }

    fun getScaleXAnimator(view: View, scaleTo: Float): Animator {
        return ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, scaleTo)
    }

    fun getScaleYAnimator(view: View, scaleTo: Float): Animator {
        return ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleX, scaleTo)
    }

    fun getElevationAnimator(view: MaterialCardView, @DimenRes elevationTo: Int): Animator {
        val valueFrom = view.cardElevation
        val valueTo = view.context.resources.getDimensionPixelSize(elevationTo).toFloat()

        return ValueAnimator.ofFloat(valueFrom, valueTo).apply {
            addUpdateListener {
                view.cardElevation = it.animatedValue as? Float ?: return@addUpdateListener
            }
        }
    }

    fun openMultipleFilesIntent(context: Activity, path: List<String>){
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.filepathlist)

        val list = bottomSheet.findViewById<LinearLayout>(R.id.filepath_list)

        list?.apply {
            path.forEach {path ->
                val file = File(path)
                val item = context.layoutInflater.inflate(R.layout.filepath_card, list, false)
                item.apply {
                    findViewById<TextView>(R.id.file_name).text = file.nameWithoutExtension

                    findViewById<TextView>(R.id.duration).apply {
                        val duration = file.getMediaDuration(context)
                        isVisible = duration > 0
                        text = duration.toStringDuration(Locale.US)
                    }


                    findViewById<TextView>(R.id.filesize).text = FileUtil.convertFileSize(file.length())
                    findViewById<TextView>(R.id.extension).text = file.extension.uppercase()
                    if (!file.exists()){
                        isEnabled = false
                        alpha = 0.7f
                    }
                    isEnabled = file.exists()
                    setOnClickListener {
                        FileUtil.openFileIntent(context, path)
                        bottomSheet.dismiss()
                    }
                }
                list.addView(item)

            }
        }

        bottomSheet.show()
        val displayMetrics = DisplayMetrics()
        context.windowManager.defaultDisplay.getMetrics(displayMetrics)
        bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun showAddEditCustomYTDLPSource(context: Activity, title: String = "", repo: String = "", created: (title: String, repo: String) -> Unit) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(context.getString(R.string.new_source))
        val view = context.layoutInflater.inflate(R.layout.create_ytdlp_sources, null)
        val titleTextInput = view.findViewById<TextInputLayout>(R.id.title_textinput).editText!!
        val repoTextInput = view.findViewById<TextInputLayout>(R.id.repo_textinput).editText!!
        titleTextInput.setText(title)
        repoTextInput.setText(repo)
        builder.setView(view)
        builder.setPositiveButton(context.getString(R.string.add)) { _: DialogInterface?, _: Int ->
            val t = titleTextInput.text.toString()
            val r = repoTextInput.text.toString()
            created(t, r)
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            context.getString(R.string.cancel)
        ) { _: DialogInterface?, _: Int -> }

        val dialog = builder.create()
        dialog.show()

        val createBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        createBtn.isEnabled = titleTextInput.text.toString().isNotBlank() && repoTextInput.text.toString().isNotBlank()
        titleTextInput.doAfterTextChanged {
            createBtn.isEnabled = titleTextInput.text.toString().isNotBlank() && repoTextInput.text.toString().isNotBlank()
        }

        repoTextInput.doAfterTextChanged {
            createBtn.isEnabled = titleTextInput.text.toString().isNotBlank() && repoTextInput.text.toString().isNotBlank()
        }

        val imm = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        repoTextInput.postDelayed({
            repoTextInput.requestFocus()
            imm.showSoftInput(repoTextInput, 0)
        }, 300)
    }

    fun showYTDLSourceBottomSheet(context: Activity, preferences: SharedPreferences, selectedSource: (title: String, repo: String) -> Unit) {
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.ytdlp_sources_list)

        val list = preferences.getStringSet("custom_ytdlp_sources", emptySet())!!.toMutableList()
        val parentView = bottomSheet.findViewById<LinearLayout>(R.id.sourcesList)!!

        bottomSheet.findViewById<View>(R.id.add)?.apply {
            setOnClickListener {
                showAddEditCustomYTDLPSource(context) { title, repo ->
                    list.add("${title}___${repo}")
                    preferences.edit().putStringSet("custom_ytdlp_sources", list.toSet()).apply()
                    bottomSheet.dismiss()
                    selectedSource(title, repo)
                }
            }
        }

        val defaultSourceTitles = context.getStringArray(R.array.ytdlp_source)
        val defaultSourceValues = context.getStringArray(R.array.ytdlp_source_values)
        val tmp = list.toMutableList()
        tmp.addAll(0, defaultSourceTitles.mapIndexed { index, s -> "${s}___${defaultSourceValues[index]}" })
        tmp.forEach { s ->
            val title = s.split("___")[0]
            val source = s.split("___")[1]
            val isEditable = !defaultSourceValues.contains(source)
            val child = LayoutInflater.from(context).inflate(R.layout.custom_ytdlp_source, null)
            child.findViewById<MaterialCardView>(R.id.sampleCustomSource).setOnClickListener {
                bottomSheet.dismiss()
                selectedSource(title, source)
            }

            child.findViewById<TextView>(R.id.sampleTitle).apply {
                text = title
            }
            child.findViewById<RadioButton>(R.id.sampleRadioBtn).apply {
                isChecked = preferences.getString("ytdlp_source", "stable") == source
                setOnClickListener {
                    bottomSheet.dismiss()
                    selectedSource(title, source)
                }
            }
            child.findViewById<TextView>(R.id.sampleRepo).text = source
            child.findViewById<View>(R.id.options).apply {
                isVisible = isEditable
                setOnClickListener {
                    val popup = PopupMenu(context, it)
                    popup.menuInflater.inflate(R.menu.custom_ytdlp_source_menu, popup.menu)
                    popup.setOnMenuItemClickListener { m ->
                        when (m.itemId) {
                            R.id.edit -> {
                                popup.dismiss()
                                showAddEditCustomYTDLPSource(context, title, source) { nt, nr ->
                                    child.findViewById<TextView>(R.id.sampleTitle).text = nt
                                    child.findViewById<TextView>(R.id.sampleRepo).text = nr
                                    val index = list.indexOf(s)
                                    list[index] = "${nt}___${nr}"
                                    preferences.edit().putStringSet("custom_ytdlp_sources", list.toSet()).apply()
                                    if (child.findViewById<RadioButton>(R.id.sampleRadioBtn).isChecked) {
                                        selectedSource(nt, nr)
                                    }
                                }
                            }

                            R.id.remove -> {
                                popup.dismiss()
                                showGenericDeleteDialog(context, title) {
                                    list.remove(s)
                                    preferences.edit()
                                        .putStringSet("custom_ytdlp_sources", list.toSet()).apply()
                                    if (child.findViewById<RadioButton>(R.id.sampleRadioBtn).isChecked) {
                                        parentView.children.first().performClick()
                                    }
                                    parentView.removeView(child)
                                }
                            }
                        }
                        true
                    }
                    popup.show()
                }
            }

            parentView.addView(child)
        }

        bottomSheet.show()
        bottomSheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun showNewAppUpdateDialog(v: GithubRelease, context: Activity, preferences: SharedPreferences) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val skippedVersions = preferences.getString("skip_updates", "")?.split(",")?.distinct()?.toMutableList() ?: mutableListOf()

        val updateDialog = MaterialAlertDialogBuilder(context)
            .setTitle(v.tag_name)
            .setMessage(v.body)
            .setIcon(R.drawable.ic_update_app)
            .setNeutralButton(R.string.ignore){ d: DialogInterface?, _:Int ->
                skippedVersions.add(v.tag_name)
                preferences.edit().putString("skip_updates", skippedVersions.joinToString(",")).apply()
                d?.dismiss()
            }
            .setNegativeButton(context.getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }
            .setPositiveButton(context.getString(R.string.update)) { d: DialogInterface?, _: Int ->
                runCatching {
                    val releaseVersion = v.assets.firstOrNull { it.name.contains(Build.SUPPORTED_ABIS[0]) }
                    if (releaseVersion == null){
                        Toast.makeText(context, R.string.couldnt_find_apk, Toast.LENGTH_SHORT).show()
                        return@runCatching
                    }


                    val uri = Uri.parse(releaseVersion.browser_download_url)
                    Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .mkdirs()
                    val downloadID = downloadManager.enqueue(
                        DownloadManager.Request(uri)
                            .setAllowedNetworkTypes(
                                DownloadManager.Request.NETWORK_WIFI or
                                        DownloadManager.Request.NETWORK_MOBILE
                            )
                            .setAllowedOverRoaming(true)
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setTitle(releaseVersion.name)
                            .setDescription(context.getString(R.string.downloading_update))
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, releaseVersion.name)
                    )

                    val onDownloadComplete: BroadcastReceiver =
                        object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent) {
                                context?.unregisterReceiver(this)
                                FileUtil.openFileIntent(context!!,
                                    Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS)?.absolutePath +
                                            File.separator + releaseVersion.name)
                            }
                        }

                    if (Build.VERSION.SDK_INT >= 26) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            context.registerReceiver(onDownloadComplete, IntentFilter(
                                DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                                Context.RECEIVER_NOT_EXPORTED
                            )
                        }else{
                            context.registerReceiver(onDownloadComplete, IntentFilter(
                                DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                            )
                        }
                    }
                    d?.dismiss()
                }
            }
        val view = updateDialog.show()
        val textView = view.findViewById<TextView>(android.R.id.message)
        textView!!.movementMethod = LinkMovementMethod.getInstance()
        val mw = Markwon.builder(context).usePlugin(object: AbstractMarkwonPlugin() {

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view, link ->
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    context.startActivity(browserIntent)
                }
            }
        }).build()
        mw.setMarkdown(textView, v.body)
    }
}