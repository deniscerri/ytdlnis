package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class FormatSelectionBottomSheetDialog(private val item: DownloadItem, private var formats: List<Format>, private val listener: OnFormatClickListener) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var fileUtil: FileUtil
    private lateinit var infoUtil: InfoUtil
    private lateinit var uiUtil: UiUtil
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        infoUtil = InfoUtil(requireActivity().applicationContext)
        sharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
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
        val shimmers = view.findViewById<ShimmerFrameLayout>(R.id.format_list_shimmer)
        shimmers.visibility = View.GONE
        addFormatsToView(linearLayout)

        val refreshBtn = view.findViewById<Button>(R.id.format_refresh)
        if (formats.none { it.filesize == 0L }) refreshBtn.visibility = View.GONE
        refreshBtn.setOnClickListener {
           lifecycleScope.launch {
               try {
                   refreshBtn.isEnabled = false
                   linearLayout.visibility = View.GONE
                   shimmers.visibility = View.VISIBLE
                   shimmers.startShimmer()

                   val res = withContext(Dispatchers.IO){
                       infoUtil.getFormats(item.url)
                   }
                   formats = res.formats.filter { it.filesize != 0L }
                   formats = when(item.type){
                       Type.audio -> formats.filter { it.format_note.contains("audio", ignoreCase = true) }
                       else -> formats.filter { !it.format_note.contains("audio", ignoreCase = true) }
                   }
                   if (formats.isEmpty()) throw Exception()
                   addFormatsToView(linearLayout)
                   refreshBtn.visibility = View.GONE

                   linearLayout.visibility = View.VISIBLE
                   shimmers.visibility = View.GONE
                   shimmers.stopShimmer()
               }catch (e: Exception){
                   refreshBtn.isEnabled = true
                   linearLayout.visibility = View.VISIBLE
                   shimmers.visibility = View.GONE
                   shimmers.stopShimmer()

                   e.printStackTrace()
                   Toast.makeText(context, getString(R.string.error_updating_formats), Toast.LENGTH_SHORT).show()
               }
           }
        }
        if (sharedPreferences.getBoolean("update_formats", false) && refreshBtn.isVisible){
            refreshBtn.performClick()
        }
    }

    private fun addFormatsToView(linearLayout: LinearLayout){
        linearLayout.removeAllViews()
        Log.e("aa", formats.toString())
        for (i in formats.lastIndex downTo 0){
            val format = formats[i]
            val formatItem = LayoutInflater.from(context).inflate(R.layout.format_item, null)
            uiUtil.populateFormatCard(formatItem as ConstraintLayout, format)
            formatItem.setOnClickListener{_ ->
                listener.onFormatClick(formats, format)
                dismiss()
            }
            formatItem.setOnLongClickListener {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("format_id", format.format_id)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, requireContext().getString(R.string.formatid_copied_to_clipboard), Toast.LENGTH_SHORT)
                    .show()
                true
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