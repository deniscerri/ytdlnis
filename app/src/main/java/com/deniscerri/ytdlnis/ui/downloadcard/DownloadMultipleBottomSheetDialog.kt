package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.ConfigureMultipleDownloadsAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.receiver.ShareActivity
import com.deniscerri.ytdlnis.util.FileUtil
import com.google.android.material.bottomappbar.BottomAppBar
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
import kotlin.collections.ArrayList

class DownloadMultipleBottomSheetDialog(private val items: MutableList<DownloadItem>) : BottomSheetDialogFragment(), ConfigureMultipleDownloadsAdapter.OnItemClickListener, View.OnClickListener,
    ConfigureDownloadBottomSheetDialog.OnDownloadItemUpdateListener {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var listAdapter : ConfigureMultipleDownloadsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var fileUtil: FileUtil


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        fileUtil = FileUtil()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.download_multiple_bottom_sheet, null)
        dialog.setContentView(view)
        //view.minimumHeight = resources.displayMetrics.heightPixels

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels / 2
        }

        listAdapter =
            ConfigureMultipleDownloadsAdapter(
                this,
                requireActivity()
            )

        recyclerView = view.findViewById(R.id.downloadMultipleRecyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = listAdapter
        listAdapter.submitList(items.toList())

        val scheduleBtn = view.findViewById<MaterialButton>(R.id.bottomsheet_schedule_button)
        scheduleBtn.setOnClickListener{
            val currentDate = Calendar.getInstance()
            val date = Calendar.getInstance()

            val datepicker = MaterialDatePicker.Builder.datePicker()
                .setCalendarConstraints(
                    CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointForward.now())
                        .build()
                )
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datepicker.addOnPositiveButtonClickListener{
                date.timeInMillis = it


                val timepicker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(currentDate.get(Calendar.HOUR_OF_DAY))
                    .setMinute(currentDate.get(Calendar.MINUTE))
                    .build()

                timepicker.addOnPositiveButtonClickListener{
                    date[Calendar.HOUR_OF_DAY] = timepicker.hour
                    date[Calendar.MINUTE] = timepicker.minute


                    items.forEach { item ->
                        item.downloadStartTime = date.timeInMillis
                    }
                    downloadViewModel.queueDownloads(items)
                    dismiss()
                }
                timepicker.show(parentFragmentManager, "timepicker")

            }
            datepicker.show(parentFragmentManager, "datepicker")

        }

        val download = view.findViewById<Button>(R.id.bottomsheet_download_button)
        download!!.setOnClickListener {
            downloadViewModel.queueDownloads(items)
            dismiss()
        }

        val bottomAppBar = view.findViewById<BottomAppBar>(R.id.bottomAppBar)
        bottomAppBar!!.setOnMenuItemClickListener { m: MenuItem ->
            val itemId = m.itemId
            if (itemId == R.id.folder) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                pathResultLauncher.launch(intent)
            } else if (itemId == R.id.edit){

            }
            true
        }
    }


    private var pathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                activity?.contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            items.forEach {
                it.downloadPath = result.data?.data.toString()
            }
            Toast.makeText(requireContext(), "Changed every item's download path to: ${fileUtil.formatPath(result.data!!.data.toString())}", Toast.LENGTH_LONG).show()
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
        parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("downloadMultipleSheet")!!).commit()
        if (parentFragmentManager.fragments.size == 1){
            (activity as ShareActivity).finish()
        }
    }

    override fun onButtonClick(videoURL: String, type: String?) {
        TODO("Not yet implemented")
    }

    override fun onCardClick(itemURL: String) {
        lifecycleScope.launch{
            val downloadItem = items.find { it.url == itemURL }
            val resultItem = withContext(Dispatchers.IO){
                resultViewModel.getItemByURL(downloadItem!!.url)
            }
            Log.e("aa", resultItem.toString())
            val bottomSheet = ConfigureDownloadBottomSheetDialog(resultItem, downloadItem!!, this@DownloadMultipleBottomSheetDialog)
            bottomSheet.show(parentFragmentManager, "configureDownloadSingleSheet")
        }
    }

    override fun onClick(p0: View?) {
        TODO("Not yet implemented")
    }

    override fun onDownloadItemUpdate(resultItemID: Long, item: DownloadItem) {
        val i = items.indexOf(items.find { it.url == item.url })
        items[i] = item
        Log.e("aa", items.toList().toString())
        listAdapter.submitList(items.toList())
    }

}

