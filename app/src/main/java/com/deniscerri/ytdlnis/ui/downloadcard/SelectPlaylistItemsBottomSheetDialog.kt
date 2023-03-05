package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.ConfigureMultipleDownloadsAdapter
import com.deniscerri.ytdlnis.adapter.PlaylistAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.receiver.ShareActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SelectPlaylistItemsBottomSheetDialog(private val items: List<ResultItem>, private val type: DownloadViewModel.Type) : BottomSheetDialogFragment(), PlaylistAdapter.OnItemClickListener {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var listAdapter : PlaylistAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var selectedText: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.select_playlist_items, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels / 2
        }

        listAdapter =
            PlaylistAdapter(
                this,
                requireActivity()
            )

        recyclerView = view.findViewById(R.id.downloadMultipleRecyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = listAdapter
        listAdapter.submitList(items)

        selectedText = view.findViewById<Button>(R.id.selected)
        selectedText.text = "0 ${resources.getString(R.string.selected)}"

        val checkAll = view.findViewById<Button>(R.id.check_all)
        checkAll!!.setOnClickListener {
            if (listAdapter.getCheckedItems().isEmpty()){
                listAdapter.checkAll()
                selectedText.text = resources.getString(R.string.all_items_selected)

            }else{
                listAdapter.clearCheckeditems()
                selectedText.text = "0 ${resources.getString(R.string.selected)}"
            }
        }


        val ok = view.findViewById<Button>(R.id.bottomsheet_ok)
        ok!!.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO){
                val checkedItems = listAdapter.getCheckedItems()
                val checkedResultItems = items.filter { checkedItems.contains(it.id) }
                val downloadItems = mutableListOf<DownloadItem>()
                checkedResultItems.forEach {c ->
                    val i = downloadViewModel.createDownloadItemFromResult(c,type)
                    i.format = downloadViewModel.getLatestCommandTemplateAsFormat()
                    downloadItems.add(i)
                }
                if (downloadItems.size == 1){
                    val resultItem = resultViewModel.getItemByURL(items[0].url)
                    val bottomSheet = DownloadBottomSheetDialog(resultItem, type)
                    bottomSheet.show(parentFragmentManager, "downloadSingleSheet")
                }else{
                    val bottomSheet = DownloadMultipleBottomSheetDialog(downloadItems)
                    bottomSheet.show(parentFragmentManager, "downloadMultipleSheet")
                }
                dismiss()
                resultViewModel.deleteAll()
                resultViewModel.insert(ArrayList(checkedResultItems))
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        cleanup()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanup()
    }

    private fun cleanup(){
        parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("downloadPlaylistSheet")!!).commit()
        if (parentFragmentManager.fragments.size == 1){
            (activity as ShareActivity).finish()
        }
    }


    override fun onCardSelect(itemID: Long, isChecked: Boolean, checkedItems: List<Long>) {
        if (checkedItems.size == items.size){
            selectedText.text = resources.getString(R.string.all_items_selected)
        }else{
            selectedText.text = "${checkedItems.size} ${resources.getString(R.string.selected)}"
        }
    }

}

