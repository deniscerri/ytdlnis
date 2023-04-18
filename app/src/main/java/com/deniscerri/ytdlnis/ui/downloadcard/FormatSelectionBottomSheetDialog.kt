package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.os.Looper
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
import kotlin.collections.ArrayList


class FormatSelectionBottomSheetDialog(private val items: List<DownloadItem?>, private var formats: List<List<Format>>, private val listener: OnFormatClickListener) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var fileUtil: FileUtil
    private lateinit var infoUtil: InfoUtil
    private lateinit var uiUtil: UiUtil
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var formatCollection: MutableList<List<Format>>
    private lateinit var chosenFormats: List<Format>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileUtil = FileUtil()
        uiUtil = UiUtil(fileUtil)
        infoUtil = InfoUtil(requireActivity().applicationContext)
        formatCollection = mutableListOf()
        chosenFormats = listOf()
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

        if (items.size > 1){
            val hasGenericFormats =  when(items.first()!!.type){
                Type.audio -> formats.flatten().size == resources.getStringArray(R.array.audio_formats).size
                else -> formats.flatten().size == resources.getStringArray(R.array.video_formats).size
            }
            if (!hasGenericFormats){
                formatCollection.addAll(formats)
                val flattenFormats = formats.flatten()
                val commonFormats = flattenFormats.groupingBy { it.format_id }.eachCount().filter { it.value == items.size }.mapValues { flattenFormats.first { f -> f.format_id == it.key } }.map { it.value }
                commonFormats.forEach {
                    it.filesize =
                        flattenFormats.filter { f -> f.format_id == it.format_id }
                            .sumOf { itt -> itt.filesize }
                }
                chosenFormats = commonFormats
            }else{
                chosenFormats = formats.flatten()
            }
            addFormatsToView(linearLayout)
        }else{
            chosenFormats = formats.flatten()
            addFormatsToView(linearLayout)
        }

        val refreshBtn = view.findViewById<Button>(R.id.format_refresh)
        if (formats.flatten().none { it.filesize == 0L } || items.isEmpty()) refreshBtn.visibility = View.GONE


        refreshBtn.setOnClickListener {
           lifecycleScope.launch {
               try {
                   refreshBtn.isEnabled = false
                   linearLayout.visibility = View.GONE
                   shimmers.visibility = View.VISIBLE
                   shimmers.startShimmer()

                   //simple download
                   if (items.size == 1){
                       val res = withContext(Dispatchers.IO){
                           infoUtil.getFormats(items.first()!!.url)
                       }
                       chosenFormats = res.formats.filter { it.filesize != 0L }
                       chosenFormats = when(items.first()?.type){
                           Type.audio -> chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                           else -> chosenFormats.filter { !it.format_note.contains("audio", ignoreCase = true) }
                       }
                       if (chosenFormats.isEmpty()) throw Exception()
                   //playlist format filtering
                   }else{
                       var progress = "0/${items.size}"
                       refreshBtn.text = progress
                       withContext(Dispatchers.IO){
                           infoUtil.getFormatsMultiple(items.map { it!!.url }) {
                               lifecycleScope.launch(Dispatchers.Main){
                                   progress = "${formatCollection.size}/${items.size}"
                                   refreshBtn.text = progress
                               }
                               formatCollection.add(it)
                           }
                       }
                       val flatFormatCollection = formatCollection.flatten()
                       val commonFormats = flatFormatCollection.groupingBy { it.format_id }.eachCount().filter { it.value == items.size }.mapValues { flatFormatCollection.first { f -> f.format_id == it.key } }.map { it.value }
                       chosenFormats = commonFormats.filter { it.filesize != 0L }.mapTo(mutableListOf()) {it.copy()}
                       chosenFormats = when(items.first()?.type){
                           Type.audio -> chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                           else -> chosenFormats.filter { !it.format_note.contains("audio", ignoreCase = true) }
                       }
                       if (chosenFormats.isEmpty()) throw Exception()
                       chosenFormats.forEach {
                           it.filesize =
                               flatFormatCollection.filter { f -> f.format_id == it.format_id }
                                   .sumOf { itt -> itt.filesize }
                       }
                   }

                   addFormatsToView(linearLayout)
                   refreshBtn.visibility = View.GONE

                   linearLayout.visibility = View.VISIBLE
                   shimmers.visibility = View.GONE
                   shimmers.stopShimmer()
               }catch (e: Exception){
                   refreshBtn.isEnabled = true
                   refreshBtn.text = getString(R.string.update_formats)
                   linearLayout.visibility = View.VISIBLE
                   shimmers.visibility = View.GONE
                   shimmers.stopShimmer()

                   e.printStackTrace()
                   Toast.makeText(context, getString(R.string.error_updating_formats), Toast.LENGTH_SHORT).show()
               }
           }
        }
        if (sharedPreferences.getBoolean("update_formats", false) && refreshBtn.isVisible && items.size == 1){
            refreshBtn.performClick()
        }
    }
    private fun addFormatsToView(linearLayout: LinearLayout){
        linearLayout.removeAllViews()
        Log.e("aa", chosenFormats.toString())
        for (i in chosenFormats.lastIndex downTo 0){
            val format = chosenFormats[i]
            val formatItem = LayoutInflater.from(context).inflate(R.layout.format_item, null)
            uiUtil.populateFormatCard(formatItem as ConstraintLayout, format)
            formatItem.setOnClickListener{_ ->
                if (items.size == 1){
                    listener.onFormatClick(List(items.size){chosenFormats}, listOf(format))
                }else{
                    val selectedFormats = mutableListOf<Format>()
                    formatCollection.forEach {
                        selectedFormats.add(it.first{ f -> f.format_id == format.format_id})
                    }
                    listener.onFormatClick(formatCollection, selectedFormats)
                }
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
        kotlin.runCatching {
            parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("formatSheet")!!).commit()
        }
    }
}

interface OnFormatClickListener{
    fun onFormatClick(allFormats: List<List<Format>>, item: List<Format>)
}