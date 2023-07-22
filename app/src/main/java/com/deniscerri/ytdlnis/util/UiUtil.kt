package com.deniscerri.ytdlnis.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
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
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.TemplateShortcut
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

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
        if (audioFormats.isNullOrEmpty()){
            formatCard.findViewById<TextView>(R.id.format_note).text = formatNote.uppercase()
        }else{
            val title = "${formatNote.uppercase()} + [${audioFormats.joinToString("/") { it.format_note }}]"
            formatCard.findViewById<TextView>(R.id.format_note).text = title
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

     fun showCommandTemplateCreationOrUpdatingSheet(item: CommandTemplate?, context: Activity, lifeCycle: LifecycleOwner, commandTemplateViewModel: CommandTemplateViewModel, newTemplate: (newTemplate: CommandTemplate) -> Unit){
        val bottomSheet = BottomSheetDialog(context)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.create_command_template)

        val ok : Button = bottomSheet.findViewById(R.id.template_create)!!
        val title : TextInputLayout = bottomSheet.findViewById(R.id.title)!!
        val content : TextInputLayout = bottomSheet.findViewById(R.id.content)!!
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
                val t = CommandTemplate(0, title.editText!!.text.toString(), content.editText!!.text.toString())
                commandTemplateViewModel.insert(t)
                newTemplate(t)
            }else{
                item.title = title.editText!!.text.toString()
                item.content = content.editText!!.text.toString()
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
    fun copyLinkToClipBoard(context: Context, url: String, bottomSheet: BottomSheetDialog?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.url), url)
        clipboard.setPrimaryClip(clip)
        bottomSheet?.hide()
        Toast.makeText(context, context.getString(R.string.link_copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }

    fun openLinkIntent(context: Context, url: String, bottomSheet: BottomSheetDialog?) {
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        bottomSheet?.hide()
        context.startActivity(i)
    }


    fun openFileIntent(fragmentContext: Context, downloadPath: String) {
        val file = File(downloadPath)
        val uri = FileProvider.getUriForFile(
            fragmentContext,
            fragmentContext.packageName + ".fileprovider",
            file
        )
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, mime)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            fragmentContext.startActivity(i)
        }catch (e: Exception){
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            fragmentContext.startActivity(i)
        }
    }

    fun shareFileIntent(fragmentContext: Context, paths: List<String>){
        val uris : ArrayList<Uri> = arrayListOf()
        paths.forEach {
            val file = File(it)
            if (! file.exists()) return@forEach
            val uri = FileProvider.getUriForFile(
                fragmentContext,
                "com.deniscerri.ytdl.fileprovider",
                file
            )
            uris.add(uri)
        }


        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            fragmentContext.startActivity(Intent.createChooser(shareIntent, fragmentContext.getString(R.string.share)))
        }catch (e: Exception){
            val intent = Intent.createChooser(shareIntent, fragmentContext.getString(R.string.share))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            fragmentContext.startActivity(intent)
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

    fun showFormatDetails(format: Format, activity: Activity){
        val bottomSheet = BottomSheetDialog(activity)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.format_details_sheet)

        val formatIdParent = bottomSheet.findViewById<LinearLayout>(R.id.format_id_parent)
        val formatURLParent = bottomSheet.findViewById<LinearLayout>(R.id.format_url_parent)
        val containerParent = bottomSheet.findViewById<LinearLayout>(R.id.container_parent)
        val codecParent = bottomSheet.findViewById<LinearLayout>(R.id.codec_parent)
        val filesizeParent = bottomSheet.findViewById<LinearLayout>(R.id.filesize_parent)
        val formatnoteParent = bottomSheet.findViewById<LinearLayout>(R.id.format_note_parent)
        val fpsParent = bottomSheet.findViewById<LinearLayout>(R.id.fps_parent)
        val asrParent = bottomSheet.findViewById<LinearLayout>(R.id.asr_parent)

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

    fun showSubtitleLanguagesDialog(context: Activity, currentValue: String, ok: (newValue: String) -> Unit){
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(context.getString(R.string.subtitle_languages))
        val inputLayout = context.layoutInflater.inflate(R.layout.textinput, null)
        val editText = inputLayout.findViewById<EditText>(R.id.url_edittext)
        inputLayout.findViewById<TextInputLayout>(R.id.url_textinput).hint = context.getString(R.string.subtitle_languages)
        editText.setText(currentValue)
        editText.setSelection(editText.text.length)
        builder.setView(inputLayout)
        builder.setPositiveButton(
            context.getString(R.string.ok)
        ) { dialog: DialogInterface?, which: Int ->
            ok(editText.text.toString())
        }

        // handle the negative button of the alert dialog
        builder.setNegativeButton(
            context.getString(R.string.cancel)
        ) { dialog: DialogInterface?, which: Int -> }

        builder.setNeutralButton("?")  { dialog: DialogInterface?, which: Int ->
            val browserIntent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yt-dlp/yt-dlp#subtitle-options"))
            context.startActivity(browserIntent)
        }


        val dialog = builder.create()
        editText.doOnTextChanged { text, start, before, count ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = editText.text.isNotEmpty()
        }
        dialog.show()
        val imm = context.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
        editText.setSelection(editText.text!!.length)
        editText!!.postDelayed({
            editText.requestFocus()
            imm.showSoftInput(editText, 0)
        }, 300)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = editText.text.isNotEmpty()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
    }


    private fun copyToClipboard(text: String, activity: Activity){
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(text, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(activity, activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
    }


    suspend fun showCommandTemplates(activity: Activity, commandTemplateViewModel: CommandTemplateViewModel, itemSelected: (itemSelected: CommandTemplate) -> Unit) {
        val bottomSheet = BottomSheetDialog(activity)
        bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheet.setContentView(R.layout.command_template_list)

        val linearLayout = bottomSheet.findViewById<LinearLayout>(R.id.command_list_linear_layout)
        val list = withContext(Dispatchers.IO){
            commandTemplateViewModel.getAll()
        }

        linearLayout!!.removeAllViews()
        list.forEach {template ->
            val item = activity.layoutInflater.inflate(R.layout.command_template_item, linearLayout, false) as ConstraintLayout
            item.findViewById<TextView>(R.id.title).text = template.title
            item.findViewById<TextView>(R.id.content).text = template.content
            item.setOnClickListener {
                itemSelected(template)
                bottomSheet.cancel()
            }
            linearLayout.addView(item)
        }

        bottomSheet.show()
        bottomSheet.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}