package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.get
import androidx.core.view.size
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class FormatSelectionBottomSheetDialog(private val item: DownloadItem, private var formats: List<Format>, private val listener: OnFormatClickListener) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var fileUtil: FileUtil
    private lateinit var infoUtil: InfoUtil
    private lateinit var uiUtil: UiUtil

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        infoUtil = InfoUtil(requireActivity().applicationContext)
    }


    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.format_select_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels / 2
        }

        val linearLayout = view.findViewById<LinearLayout>(R.id.format_list_linear_layout)
        addFormatsToView(linearLayout)

        val refreshBtn = view.findViewById<Button>(R.id.format_refresh)
        if (formats.none { it.filesize == 0L }) refreshBtn.visibility = View.GONE
        refreshBtn.setOnClickListener {
           lifecycleScope.launch {
               try {
                   refreshBtn.isEnabled = false
                   val res = withContext(Dispatchers.IO){
                       infoUtil.getFormats(item.url)
                   }
                   formats = res.formats.filter { it.filesize != 0L }
                   formats = when(item.type){
                       Type.audio -> formats.filter { it.format_note.contains("audio", ignoreCase = true) }
                       else -> formats.filter { !it.format_note.contains("audio", ignoreCase = true) }
                   }
                   addFormatsToView(linearLayout)
                   refreshBtn.visibility = View.GONE
               }catch (e: Exception){
                   refreshBtn.isEnabled = true
                   e.printStackTrace()
                   Toast.makeText(context, getString(R.string.error_updating_formats), Toast.LENGTH_SHORT).show()
               }
           }
        }
    }

    private fun addFormatsToView(linearLayout: LinearLayout){
        linearLayout.removeAllViews()
        for (i in formats.lastIndex downTo 0){
            val it = formats[i]
            val formatItem = LayoutInflater.from(context).inflate(R.layout.format_item, null)
            uiUtil.populateFormatCard(formatItem as ConstraintLayout, it)
            formatItem.setOnClickListener{_ ->
                listener.onFormatClick(formats, it)
                dismiss()
            }
            linearLayout.addView(formatItem)
        }
    }



    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        cleanUp()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanUp()
    }


    private fun cleanUp(){
        parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("formatSheet")!!).commit()
    }
}

interface OnFormatClickListener{
    fun onFormatClick(allFormats: List<Format>, item: Format)
}