package com.deniscerri.ytdlnis.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.CommandTemplate
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.TemplateShortcut
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.io.File
import java.util.*

class UiUtil(private val fileUtil: FileUtil) {
    fun populateFormatCard(formatCard : ConstraintLayout, chosenFormat: Format){
        formatCard.findViewById<TextView>(R.id.container).text = chosenFormat.container.uppercase()
        formatCard.findViewById<TextView>(R.id.format_note).text = chosenFormat.format_note.uppercase()
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
        formatCard.findViewById<TextView>(R.id.file_size).text = fileUtil.convertFileSize(chosenFormat.filesize)

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
        }else{
            ok.isEnabled = false
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

        commandTemplateViewModel.shortcuts.observe(lifeCycle){ it ->
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
                commandTemplateViewModel.update(item)
                newTemplate(item)
            }
            bottomSheet.cancel()
        }

        bottomSheet.show()
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

        commandTemplateViewModel.shortcuts.observe(lifeCycle){ it ->
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
        val mime = fragmentContext.contentResolver.getType(uri)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, mime)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        fragmentContext.startActivity(i)
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
}