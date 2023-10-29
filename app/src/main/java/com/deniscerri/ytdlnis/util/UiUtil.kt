package com.deniscerri.ytdlnis.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.getStringArray
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.HistoryItem
import com.deniscerri.ytdlnis.database.models.TemplateShortcut
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.ui.downloadcard.ConfigureDownloadBottomSheetDialog
import com.deniscerri.ytdlnis.ui.downloadcard.VideoCutListener
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
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.neo.highlight.core.Highlight
import com.neo.highlight.util.listener.HighlightTextWatcher
import com.neo.highlight.util.scheme.ColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern


object UiUtil {
    @SuppressLint("SetTextI18n")
    fun populateFormatCard(context: Context, formatCard : MaterialCardView, chosenFormat: Format, audioFormats: List<Format>?){
        var formatNote = chosenFormat.format_note
        if (formatNote.isEmpty()) formatNote = context.getString(R.string.defaultValue)
        else if (formatNote == "best") formatNote = context.getString(R.string.best_quality)
        else if (formatNote == "worst") formatNote = context.getString(R.string.worst_quality)

        var container = chosenFormat.container
        if (container == "Default") container = context.getString(R.string.defaultValue)

        formatCard.findViewById<TextView>(R.id.container).text = container.uppercase()
        formatCard.findViewById<TextView>(R.id.format_note).text = formatNote.uppercase()

        val audioFormatsTextView = formatCard.findViewById<TextView>(R.id.audio_formats)
        if (!audioFormats.isNullOrEmpty()) {
            audioFormatsTextView.text = audioFormats.joinToString("+") { it.format_id }
            audioFormatsTextView.visibility = View.VISIBLE
        }else{
            audioFormatsTextView.visibility = View.GONE
        }
        formatCard.findViewById<TextView>(R.id.format_id).text = "id: ${chosenFormat.format_id}"
        val codec =
            if (chosenFormat.encoding != "") {
                chosenFormat.encoding.uppercase()
            }else if (chosenFormat.vcodec != "none" && chosenFormat.vcodec != ""){
                chosenFormat.vcodec.uppercase()
            } else {
                chosenFormat.acodec.uppercase()
            }
        if (codec == "" || codec == "none"){
            formatCard.findViewById<TextView>(R.id.codec).visibility = View.GONE
        }else{
            formatCard.findViewById<TextView>(R.id.codec).visibility = View.VISIBLE
            formatCard.findViewById<TextView>(R.id.codec).text = codec
        }
        var filesize = chosenFormat.filesize
        if (!audioFormats.isNullOrEmpty()) filesize += audioFormats.sumOf { it.filesize }
        formatCard.findViewById<TextView>(R.id.file_size).text = FileUtil.convertFileSize(filesize)

    }

    fun populateCommandCard(card: MaterialCardView, item: CommandTemplate){
        card.findViewById<TextView>(R.id.title).text = item.title
        card.findViewById<TextView>(R.id.content).text = item.content
        card.alpha = 1f
        card.tag = item.id
    }

     fun showCommandTemplateCreationOrUpdatingSheet(item: CommandTemplate?, context: Activity, lifeCycle: LifecycleOwner, commandTemplateViewModel: CommandTemplateViewModel, newTemplate: (newTemplate: CommandTemplate) -> Unit){
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.create_command_template)

        val ok : Button = bottomSheet.findViewById(R.id.template_create)!!
        val title : TextInputLayout = bottomSheet.findViewById(R.id.title)!!
        val content : TextInputLayout = bottomSheet.findViewById(R.id.content)!!
        val extraCommandsSwitch : MaterialSwitch = bottomSheet.findViewById(R.id.extraCommandsSwitch)!!
        val shortcutsChipGroup : ChipGroup = bottomSheet.findViewById(R.id.shortcutsChipGroup)!!
        val editShortcuts : Button = bottomSheet.findViewById(R.id.edit_shortcuts)!!

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
                ok.isEnabled =
                    !(title.editText!!.text.isEmpty() || content.editText!!.text.isEmpty())
            }
        })

        content.editText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(p0: Editable?) {
                ok.isEnabled =
                    (title.editText!!.text.isNotEmpty() && content.editText!!.text.isNotEmpty())
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
            extraCommandsSwitch.isChecked = item.useAsExtraCommand
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
            if (item == null){
                val t = CommandTemplate(0, title.editText!!.text.toString(), content.editText!!.text.toString(), extraCommandsSwitch.isChecked)
                commandTemplateViewModel.insert(t)
                newTemplate(t)
            }else{
                item.title = title.editText!!.text.toString()
                item.content = content.editText!!.text.toString()
                item.useAsExtraCommand = extraCommandsSwitch.isChecked
                Log.e("aa", item.toString())
                commandTemplateViewModel.update(item)
                newTemplate(item)
            }
            bottomSheet.cancel()
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


    fun openFileIntent(context: Context, downloadPath: String) {
        val uri = downloadPath.runCatching {
            DocumentFile.fromSingleUri(context, Uri.parse(downloadPath)).run{
                if (this?.exists() == true){
                    this.uri
                }else if (File(this@runCatching).exists()){
                    FileProvider.getUriForFile(context, context.packageName + ".fileprovider",
                        File(this@runCatching))
                }else null
            }
        }.getOrNull()

        if (uri == null){
            Toast.makeText(context, "Error opening file!", Toast.LENGTH_SHORT).show()
        }else{
            Intent().apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                action = Intent.ACTION_VIEW
                data = uri
            }.run{
                context.startActivity(this)
            }
        }
    }

    fun shareFileIntent(context: Context, paths: List<String>){
        val uris : ArrayList<Uri> = arrayListOf()
        paths.runCatching {
            this.forEach {path ->
                val uri = DocumentFile.fromSingleUri(context, Uri.parse(path)).run{
                    if (this?.exists() == true){
                        this.uri
                    }else if (File(path).exists()){
                        FileProvider.getUriForFile(context, context.packageName + ".fileprovider",
                            File(path))
                    }else null
                }
                if (uri != null) uris.add(uri)
            }

        }

        if (uris.isEmpty()){
            Toast.makeText(context, "Error sharing files!", Toast.LENGTH_SHORT).show()
        }else{
            Intent().apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                action = Intent.ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                type = if (uris.size == 1) uris[0].let { context.contentResolver.getType(it) } ?: "media/*" else "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }.run{
                context.startActivity(Intent.createChooser(this, context.getString(R.string.share)))
            }
        }
    }

    fun showDatePicker(fragmentManager: FragmentManager , onSubmit : (chosenDate: Calendar) -> Unit ){
        val currentDate = Calendar.getInstance()
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


            val timepicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(currentDate.get(Calendar.HOUR_OF_DAY))
                .setMinute(currentDate.get(Calendar.MINUTE))
                .build()

            timepicker.addOnPositiveButtonClickListener{
                date[Calendar.HOUR_OF_DAY] = timepicker.hour
                date[Calendar.MINUTE] = timepicker.minute
                onSubmit(date)
            }
            timepicker.show(fragmentManager, "timepicker")

        }
        datePicker.show(fragmentManager, "datepicker")
    }

    fun showTimePicker(fragmentManager: FragmentManager , onSubmit : (chosenTime: Calendar) -> Unit ){
        val currentDate = Calendar.getInstance()
        val date = Calendar.getInstance()

        val timepicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(currentDate.get(Calendar.HOUR_OF_DAY))
            .setMinute(currentDate.get(Calendar.MINUTE))
            .build()

        timepicker.addOnPositiveButtonClickListener{
            date[Calendar.HOUR_OF_DAY] = timepicker.hour
            date[Calendar.MINUTE] = timepicker.minute
            onSubmit(date)
        }
        timepicker.show(fragmentManager, "timepicker")
    }

    fun showDownloadItemDetailsCard(
        item: DownloadItem,
        context: Activity,
        status: DownloadRepository.Status,
        removeItem : (DownloadItem, BottomSheetDialog) -> Unit,
        downloadItem: (DownloadItem) -> Unit,
        longClickDownloadButton: (DownloadItem) -> Unit,
        scheduleButtonClick: (DownloadItem) -> Unit?
    ){
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.history_item_details_bottom_sheet)
        val title = bottomSheet.findViewById<TextView>(R.id.bottom_sheet_title)
        title!!.text = item.title.ifEmpty { "`${context.getString(R.string.defaultValue)}`" }
        val author = bottomSheet.findViewById<TextView>(R.id.bottom_sheet_author)
        author!!.text = item.author.ifEmpty { "`${context.getString(R.string.defaultValue)}`" }

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
        val formatNote = bottomSheet.findViewById<Chip>(R.id.format_note)
        val container = bottomSheet.findViewById<Chip>(R.id.container_chip)
        val codec = bottomSheet.findViewById<Chip>(R.id.codec)
        val fileSize = bottomSheet.findViewById<Chip>(R.id.file_size)

        when(status){
            DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused -> {
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

        if (item.format.format_note == "?" || item.format.format_note == "") formatNote!!.visibility =
            View.GONE
        else formatNote!!.text = item.format.format_note

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
            DownloadRepository.Status.Queued, DownloadRepository.Status.QueuedPaused -> {
                if (item.downloadStartTime <= System.currentTimeMillis() / 1000) download!!.visibility = View.GONE
                else{
                    download!!.text = context.getString(R.string.download_now)
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

        download?.setOnClickListener {
            bottomSheet.dismiss()
            downloadItem(item)
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
        removeItem: (item:HistoryItem, removeFiles: Boolean) -> Unit,
        redownloadItem: (HistoryItem) -> Unit,
        redownloadShowDownloadCard: (HistoryItem) -> Unit,
    ){
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.history_item_details_bottom_sheet)
        val title = bottomSheet.findViewById<TextView>(R.id.bottom_sheet_title)
        title!!.text = item!!.title
        val author = bottomSheet.findViewById<TextView>(R.id.bottom_sheet_author)
        author!!.text = item.author

        // BUTTON ----------------------------------
        val btn = bottomSheet.findViewById<FloatingActionButton>(R.id.download_button_type)

        val typeImageResource: Int =
        if (item.type == DownloadViewModel.Type.audio) {
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
            btn?.setOnClickListener {
                shareFileIntent(context, listOf(item.downloadPath))
            }
        }

        val time = bottomSheet.findViewById<TextView>(R.id.time)
        val formatNote = bottomSheet.findViewById<TextView>(R.id.format_note)
        val container = bottomSheet.findViewById<Chip>(R.id.container_chip)
        val codec = bottomSheet.findViewById<TextView>(R.id.codec)
        val fileSize = bottomSheet.findViewById<TextView>(R.id.file_size)
        val file = File(item.downloadPath)

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = item.time * 1000L
        time!!.text = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(calendar.time)
        time.isClickable = false

        if (item.format.format_note == "?" || item.format.format_note == "") formatNote!!.visibility =
            View.GONE
        else formatNote!!.text = item.format.format_note.uppercase()

        if (item.format.container != "") {
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
        if (codecText == "" || codecText == "none"){
            codec!!.visibility = View.GONE
        }else{
            codec!!.visibility = View.VISIBLE
            codec.text = codecText
        }

        val fileSizeReadable = FileUtil.convertFileSize(if (file.exists()) file.length() else item.format.filesize)
        if (fileSizeReadable == "?") fileSize!!.visibility = View.GONE
        else fileSize!!.text = fileSizeReadable

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
            openFileIntent(context, item.downloadPath)
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

        if (format.format_id.isBlank()) formatIdParent?.visibility = View.GONE
        else {
            formatIdParent?.findViewById<TextView>(R.id.format_id_value)?.text = format.format_id
            formatIdParent?.setOnClickListener {
                copyToClipboard(format.format_id, activity)
            }
        }

        if (format.url.isNullOrBlank()) formatURLParent?.visibility = View.GONE
        else {
            formatURLParent?.findViewById<TextView>(R.id.format_url_value)?.text = format.url
            formatURLParent?.setOnClickListener {
                copyToClipboard(format.url!!, activity)
            }
        }


        if (format.container.isBlank()) containerParent?.visibility = View.GONE
        else {
            containerParent?.findViewById<TextView>(R.id.container_value)?.text = format.container
            containerParent?.setOnClickListener {
                copyToClipboard(format.container, activity)
            }
        }

        val codecField =
            if (format.encoding != "") {
                format.encoding.uppercase()
            }else if (format.vcodec != "none" && format.vcodec != ""){
                format.vcodec.uppercase()
            } else {
                format.acodec.uppercase()
            }

        if (codecField.isBlank()) codecParent?.visibility = View.GONE
        else {
            codecParent?.findViewById<TextView>(R.id.codec_value)?.text = codecField
            codecParent?.setOnClickListener {
                copyToClipboard(codecField, activity)
            }
        }

        if (format.filesize != 0L) filesizeParent?.visibility = View.GONE
        else {
            filesizeParent?.findViewById<TextView>(R.id.filesize_value)?.text = FileUtil.convertFileSize(format.filesize)
            filesizeParent?.setOnClickListener {
                copyToClipboard(FileUtil.convertFileSize(format.filesize), activity)
            }
        }

        if (format.format_note.isBlank()) formatnoteParent?.visibility = View.GONE
        else {
            formatnoteParent?.findViewById<TextView>(R.id.format_note_value)?.text = format.format_note
            formatnoteParent?.setOnClickListener {
                copyToClipboard(format.format_note, activity)
            }
        }

        if (format.fps.isNullOrBlank() || format.fps == "0") fpsParent?.visibility = View.GONE
        else {
            fpsParent?.findViewById<TextView>(R.id.fps_value)?.text = format.fps
            fpsParent?.setOnClickListener {
                copyToClipboard(format.fps!!, activity)
            }
        }

        if (format.asr.isNullOrBlank()) asrParent?.visibility = View.GONE
        else {
            asrParent?.findViewById<TextView>(R.id.asr_value)?.text = format.asr
            asrParent?.setOnClickListener {
                copyToClipboard(format.asr!!, activity)
            }
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

    private fun showSubtitleLanguagesDialog(context: Activity, currentValue: String, ok: (newValue: String) -> Unit){
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
                tmp.text = s
                tmp.id = index

                tmp.setOnClickListener {
                    val c = it as Chip
                    if(!c.isChecked){
                        editText.setText(editText.text.toString().replace(c.text.toString(), ""))
                        editText.setSelection(editText.text.length)
                    }else{
                        editText.append(c.text)
                    }
                }

                chips.add(tmp)
            }
            withContext(Dispatchers.Main){
                view.findViewById<View>(R.id.suggested).visibility = View.VISIBLE
                chips.forEach {
                    it.isChecked = editText.text.contains(it.text)
                    chipGroup!!.addView(it)
                }
            }
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
    }


    private fun copyToClipboard(text: String, activity: Activity){
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(text, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(activity, activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }


    suspend fun showCommandTemplates(activity: Activity, commandTemplateViewModel: CommandTemplateViewModel, itemSelected: (itemSelected: List<CommandTemplate>) -> Unit) {
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
                if (chip.isChecked){
                    itemSelected(shortcut.content)
                }else{
                    itemRemoved(shortcut.content)
                }
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
        filenameTemplateSet: (String) -> Unit,
        saveSubtitlesClicked: (Boolean) -> Unit,
        subtitleLanguagesSet: (String) -> Unit,
        removeAudioClicked: (Boolean) -> Unit,
        extraCommandsClicked: () -> Unit
    ){
        val embedSubs = view.findViewById<Chip>(R.id.embed_subtitles)
        val saveSubtitles = view.findViewById<Chip>(R.id.save_subtitles)
        val subtitleLanguages = view.findViewById<Chip>(R.id.subtitle_languages)

        embedSubs!!.isChecked = items.all { it.videoPreferences.embedSubs }
        embedSubs.setOnClickListener {
            subtitleLanguages.isEnabled = embedSubs.isChecked || saveSubtitles.isChecked
            embedSubsClicked(embedSubs.isChecked)
        }

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

        val sponsorBlock = view.findViewById<Chip>(R.id.sponsorblock_filters)
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
        if (items.size > 1) cut.isVisible = false
        else{
            if(items[0].duration.isNotEmpty()){
                val downloadItem = items[0]
                cut.isEnabled = true
                if (downloadItem.downloadSections.isNotBlank()) cut.text = downloadItem.downloadSections
                val cutVideoListener = object : VideoCutListener {

                    override fun onChangeCut(list: List<String>) {
                        if (list.isEmpty()){
                            downloadItem.downloadSections = ""
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
                            downloadItem.downloadSections = value
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
                cut.isEnabled = false
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


        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        items.forEach { it.videoPreferences.subsLanguages = sharedPreferences.getString("subs_lang", "en.*,.*-orig")!! }
        if (items.all { it.videoPreferences.writeSubs}) {
            saveSubtitles.isChecked = true
            subtitleLanguages.visibility = View.VISIBLE
        }

        saveSubtitles.setOnCheckedChangeListener { _, _ ->
            subtitleLanguages.isEnabled = embedSubs.isChecked || saveSubtitles.isChecked
            saveSubtitlesClicked(saveSubtitles.isChecked)
        }

        subtitleLanguages.isEnabled = embedSubs.isChecked || saveSubtitles.isChecked
        subtitleLanguages.setOnClickListener {
            val currentSubtitleLang = if (items.size == 1 || items.all { it.videoPreferences.subsLanguages == items[0].videoPreferences.subsLanguages }){
                items[0].videoPreferences.subsLanguages
            }else {
                ""
            }
            showSubtitleLanguagesDialog(context, currentSubtitleLang){
                subtitleLanguagesSet(it)
            }
        }

        val removeAudio = view.findViewById<Chip>(R.id.remove_audio)
        removeAudio.isChecked = items.all { it.videoPreferences.removeAudio }
        removeAudio.setOnCheckedChangeListener { _, _ ->
            removeAudioClicked(removeAudio.isChecked)
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
        splitByChaptersClicked: (Boolean) -> Unit,
        filenameTemplateSet: (String) -> Unit,
        sponsorBlockItemsSet: (Array<String>, List<Boolean>) -> Unit,
        cutClicked: (VideoCutListener) -> Unit,
        extraCommandsClicked: () -> Unit,

    ){
        val embedThumb = view.findViewById<Chip>(R.id.embed_thumb)
        embedThumb!!.isChecked = items.all { it.audioPreferences.embedThumb }
        embedThumb.setOnClickListener {
            embedThumbClicked(embedThumb.isChecked)
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
        if (items.size > 1) cut.isVisible = false
        else{
            val downloadItem = items[0]
            if (downloadItem.duration.isNotEmpty()){
                cut.isEnabled = true
                if (downloadItem.downloadSections.isNotBlank()) cut.text = downloadItem.downloadSections
                val cutVideoListener = object : VideoCutListener {
                    override fun onChangeCut(list: List<String>) {
                        if (list.isEmpty()){
                            downloadItem.downloadSections = ""
                            cut.text = context.getString(R.string.cut)

                            splitByChapters.isEnabled = true
                            splitByChapters.isChecked = downloadItem.audioPreferences.splitByChapters
                        }else{
                            var value = ""
                            list.forEach {
                                value += "$it;"
                            }
                            downloadItem.downloadSections = value
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
                cut.isEnabled = false
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

    fun handleResultResponse(context: Activity, it: ResultViewModel.ResultsUiState, closed: () -> Unit){
        val title = context.getString(it.errorMessage!!.first)
        val message = it.errorMessage!!.second

        val errDialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)

        for (a in it.actions!!){
            when(a.second){
                ResultViewModel.ResultAction.COPY_LOG -> {
                    errDialog.setPositiveButton(a.first) { d:DialogInterface?, _:Int ->
                        val clipboard: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setText(message)
                        d?.dismiss()
                    }
                }
            }
        }

        errDialog.setOnCancelListener {
            closed()
        }

        errDialog.show()
    }

    @SuppressLint("SetTextI18n")
    fun handleDownloadsResponse(context: Activity, lifecycleScope: CoroutineScope, supportFragmentManager: FragmentManager, it: DownloadViewModel.DownloadsUiState, downloadViewModel: DownloadViewModel, historyViewModel: HistoryViewModel){
        val downloadAnywayAction = it.actions?.first { it.second == DownloadViewModel.DownloadsAction.DOWNLOAD_ANYWAY}
        if (downloadAnywayAction != null){
            if (downloadAnywayAction.third == null) return
            val downloads =downloadAnywayAction.third!!.toMutableList()
            val ids = downloadAnywayAction.third!!.map { it.id }
            val titles = downloadAnywayAction.third!!.map { it.title }


            val title = context.getString(it.errorMessage!!.first)
            val message = context.getString(it.errorMessage!!.second)

            val errDialog = MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)

            var dialog: Dialog? = null

            val linearLayout = context.layoutInflater.inflate(R.layout.already_exists_card, null) as LinearLayout
            ids.forEachIndexed { index, id ->
                val alreadyExistsItem = context.layoutInflater.inflate(R.layout.already_exists_item, null)
                alreadyExistsItem.tag = id.toString()
                val ttle = alreadyExistsItem.findViewById<Button>(R.id.already_exists_title)
                ttle.text = "${index + 1}. ${titles[index]}"
                CoroutineScope(Dispatchers.IO).launch {
                    val editBtn = alreadyExistsItem.findViewById<Button>(R.id.already_exists_edit)
                    var downloadItem: DownloadItem = downloadViewModel.getItemByID(id)
                    val historyItem: HistoryItem? = downloadViewModel.getHistoryItemById(id)

                    if (historyItem != null){
                        val idx = downloads.indexOfFirst { it.id == historyItem.id }
                        downloadItem = downloadViewModel.createDownloadItemFromHistory(historyItem)
                        downloadItem.id = historyItem.id
                        downloads[idx] = downloadItem
                    }

                    withContext(Dispatchers.Main){
                        editBtn.visibility = View.VISIBLE
                    }

                    editBtn.setOnClickListener {
                        val resultItem = downloadViewModel.createResultItemFromDownload(downloadItem)
                        val onItemUpdated = object: ConfigureDownloadBottomSheetDialog.OnDownloadItemUpdateListener {
                            override fun onDownloadItemUpdate(
                                resultItemID: Long,
                                item: DownloadItem
                            ) {
                                lifecycleScope.launch {
                                    val idx = downloads.indexOfFirst { it.id == item.id }
                                    downloads[idx] = item
                                    withContext(Dispatchers.Main){
                                        ttle.text = "${index + 1}. ${item.title}"
                                    }
                                }
                            }
                        }
                        val bottomSheet = ConfigureDownloadBottomSheetDialog(resultItem, downloadItem, onItemUpdated)
                        bottomSheet.show(supportFragmentManager, "configureDownloadSingleSheet")
                    }

                    if (historyItem != null){
                        ttle.setOnClickListener {
                            showHistoryItemDetailsCard(historyItem, context, isPresent = true,
                                removeItem = { item, deleteFile ->
                                    historyViewModel.delete(item, deleteFile)
                                },
                                redownloadItem = { },
                                redownloadShowDownloadCard = {}
                            )
                        }
                    }
                }

                ttle.setOnLongClickListener {
                    showGenericDeleteDialog(context, ttle.text.toString(), accepted = {
                        linearLayout.removeView(alreadyExistsItem)
                        if (linearLayout.childCount == 0){
                            dialog?.dismiss()
                        }
                    })
                    true
                }

                linearLayout.addView(alreadyExistsItem)
            }

            errDialog.setView(linearLayout)

            errDialog.setPositiveButton(downloadAnywayAction.first) { d:DialogInterface?, _:Int ->
                CoroutineScope(Dispatchers.IO).launch {
                    linearLayout.children.forEach {view ->
                        val downloadItem = downloads.first { it.id == (view.tag as String).toLong()}
                        downloadItem.id = 0
                        downloadViewModel.queueDownloads(listOf(downloadItem), true)
                    }
                }
                d?.dismiss()
            }

            errDialog.setNegativeButton(R.string.schedule) { d:DialogInterface?, _:Int ->
                showDatePicker(supportFragmentManager) { calendar ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val items = mutableListOf<DownloadItem>()
                        linearLayout.children.forEach {view ->
                            val downloadItem = downloads.first { it.id == (view.tag as String).toLong()}
                            downloadItem.downloadStartTime = calendar.timeInMillis
                            downloadItem.id = 0
                            items.add(downloadItem)
                        }

                        runBlocking {
                            val chunks = items.chunked(10)
                            for (c in chunks) {
                                downloadViewModel.queueDownloads(c, true)
                            }
                            val first = items.first()
                            val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(first.downloadStartTime)
                            withContext(Dispatchers.Main){
                                Toast.makeText(context, context.getString(R.string.download_rescheduled_to) + " " + date, Toast.LENGTH_LONG).show()
                            }
                        }
                        withContext(Dispatchers.Main){
                            d?.dismiss()
                        }
                    }
                }
            }

            dialog = errDialog.show()
        }
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

    fun showRemoveHistoryItemDialog(item: HistoryItem, context: Activity, delete: (item: HistoryItem, deleteFile: Boolean) -> Unit){
        val deleteFile = booleanArrayOf(false)
        val deleteDialog = MaterialAlertDialogBuilder(context)
        deleteDialog.setTitle(context.getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
        val path = item.downloadPath
        val file = File(path)
        if (file.exists() && path.isNotEmpty()) {
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

    fun showFilenameTemplateDialog(context: Activity, currentFilename: String, dialogTitle: String = context.getString(R.string.file_name_template), filenameSelected: (f: String) -> Unit){
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(dialogTitle)
        val view = context.layoutInflater.inflate(R.layout.filename_template_dialog, null)
        val editText = view.findViewById<EditText>(R.id.filename_edittext)
        view.findViewById<TextInputLayout>(R.id.filename).hint = context.getString(R.string.file_name_template)
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
                    if(!c.isChecked){
                        editText.setText(editText.text.toString().replace(c.text.toString(), ""))
                        editText.setSelection(editText.text.length)
                    }else{
                        editText.append(c.text)
                    }
                }

                chips.add(tmp)
            }
            withContext(Dispatchers.Main){
                view.findViewById<View>(R.id.suggested).visibility = View.VISIBLE
                chips.forEach {
                    it.isChecked = editText.text.contains(it.text)
                    chipGroup!!.addView(it)
                }
            }
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
    }
}