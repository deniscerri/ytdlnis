package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.os.Looper
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
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
        val hasGenericFormats =  when(items.first()!!.type){
            Type.audio -> formats.first().size == resources.getStringArray(R.array.audio_formats).size
            else -> formats.first().size == resources.getStringArray(R.array.video_formats).size
        }
        if (items.size > 1){

            if (!hasGenericFormats){
                formatCollection.addAll(formats)
                val flattenFormats = formats.flatten()
                val commonFormats = flattenFormats.groupingBy { it.format_id }.eachCount().filter { it.value == items.size }.mapValues { flattenFormats.first { f -> f.format_id == it.key } }.map { it.value }
                chosenFormats = commonFormats.mapTo(mutableListOf()) {it.copy()}
                chosenFormats = when(items.first()?.type){
                    Type.audio -> chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                    else -> chosenFormats.filter { !it.format_note.contains("audio", ignoreCase = true) }
                }
                chosenFormats.forEach {
                    it.filesize =
                        flattenFormats.filter { f -> f.format_id == it.format_id }
                            .sumOf { itt -> itt.filesize }
                }
            }else{
                chosenFormats = formats.flatten()
            }
            addFormatsToView(linearLayout)
        }else{
            chosenFormats = formats.flatten()
            if(!hasGenericFormats){
                chosenFormats = when(items.first()?.type){
                    Type.audio -> chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                    else -> chosenFormats.filter { !it.format_note.contains("audio", ignoreCase = true) }
                }
            }
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
                       formatCollection.clear()
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
                    if (selectedFormats.isEmpty()) {
                        items.forEach {
                            selectedFormats.add(format)
                        }
                    }
                    listener.onFormatClick(formatCollection, selectedFormats)
                }
                dismiss()
            }
            formatItem.setOnLongClickListener {
                val bottomSheet = BottomSheetDialog(requireContext())
                bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
                bottomSheet.setContentView(R.layout.format_details_sheet)

                val formatIdParent = bottomSheet.findViewById<LinearLayout>(R.id.format_id_parent)
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
                        copyToClipboard(format.format_id)
                    }
                }


                if (format.container.isBlank()) containerParent?.visibility = View.GONE
                else {
                    containerParent?.findViewById<TextView>(R.id.container_value)?.text = format.container
                    containerParent?.setOnClickListener {
                        copyToClipboard(format.container)
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
                        copyToClipboard(codecField)
                    }
                }

                if (format.filesize != 0L) filesizeParent?.visibility = View.GONE
                else {
                    filesizeParent?.findViewById<TextView>(R.id.filesize_value)?.text = fileUtil.convertFileSize(format.filesize)
                    filesizeParent?.setOnClickListener {
                        copyToClipboard(fileUtil.convertFileSize(format.filesize))
                    }
                }

                if (format.format_note.isBlank()) formatnoteParent?.visibility = View.GONE
                else {
                    formatnoteParent?.findViewById<TextView>(R.id.format_note_value)?.text = format.format_note
                    formatnoteParent?.setOnClickListener {
                        copyToClipboard(format.format_note)
                    }
                }

                if (format.fps.isNullOrBlank()) fpsParent?.visibility = View.GONE
                else {
                    fpsParent?.findViewById<TextView>(R.id.fps_value)?.text = format.fps
                    fpsParent?.setOnClickListener {
                        copyToClipboard(format.fps!!)
                    }
                }

                if (format.asr.isNullOrBlank()) asrParent?.visibility = View.GONE
                else {
                    asrParent?.findViewById<TextView>(R.id.asr_value)?.text = format.asr
                    asrParent?.setOnClickListener {
                        copyToClipboard(format.asr!!)
                    }
                }



                bottomSheet.show()
                val displayMetrics = DisplayMetrics()
                requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
                bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
                bottomSheet.window!!.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                true
            }
            linearLayout.addView(formatItem)
        }
    }

    private fun copyToClipboard(text: String){
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(text, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, requireContext().getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
            .show()
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