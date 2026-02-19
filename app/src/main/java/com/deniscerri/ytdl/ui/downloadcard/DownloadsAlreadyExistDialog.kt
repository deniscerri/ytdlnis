package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.AlreadyExistsItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel.AlreadyExistsIDs
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.adapter.AlreadyExistsAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.elevation.SurfaceColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class DownloadsAlreadyExistDialog : BottomSheetDialogFragment(), AlreadyExistsAdapter.OnItemClickListener {
    private var activity: Activity? = null
    private lateinit var downloadViewModel : DownloadViewModel
    private lateinit var resultViewModel : ResultViewModel
    private lateinit var historyViewModel : HistoryViewModel

    private var duplicateIDs : MutableList<AlreadyExistsIDs> = mutableListOf()
    private lateinit var duplicates: MutableList<AlreadyExistsItem>
    private lateinit var preferences: SharedPreferences
    private lateinit var adapter: AlreadyExistsAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = getActivity()
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        historyViewModel = ViewModelProvider(requireActivity())[HistoryViewModel::class.java]
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        kotlin.runCatching {
            duplicateIDs = if (Build.VERSION.SDK_INT >= 33){
                arguments?.getParcelableArrayList("duplicates", AlreadyExistsIDs::class.java)!!.toMutableList()
            }else{
                arguments?.getParcelableArrayList<AlreadyExistsIDs>("duplicates")!!.toMutableList()
            }

            if (duplicateIDs.isEmpty()){
                dismiss()
            }
        }.onFailure {
            dismiss()
        }

    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = requireActivity().layoutInflater.inflate(R.layout.fragment_already_exists_dialog, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())
        dialog.setOnShowListener {
            val behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            if(resources.getBoolean(R.bool.isTablet) || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = displayMetrics.heightPixels
            }
        }

        adapter = AlreadyExistsAdapter(this, requireActivity())
        recyclerView = view.findViewById(R.id.downloadMultipleRecyclerview)
        recyclerView.adapter = adapter
        recyclerView.enableFastScroll()

        runBlocking {
            val items = withContext(Dispatchers.IO){
                downloadViewModel.getAllByIDs(duplicateIDs.map { it.downloadItemID })
            }
            duplicates = items.map { item -> AlreadyExistsItem(item, duplicateIDs.firstOrNull { it.downloadItemID == item.id }?.historyItemID) }.toMutableList()
            adapter.submitList(duplicates.toList())
        }

        view.findViewById<MaterialButton>(R.id.bottomsheet_download_button).setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                downloadViewModel.deleteWithDuplicateStatus()
                val items = duplicates.map { it.downloadItem }
                items.forEach { it.id = 0 }
                val result = downloadViewModel.queueDownloads(items, true)
                if (result.message.isNotBlank()){
                    lifecycleScope.launch {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                withContext(Dispatchers.Main){
                    dismiss()
                }
            }
        }

    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        CoroutineScope(Dispatchers.IO).launch {
            downloadViewModel.deleteWithDuplicateStatus()
        }
    }



    override fun onEditItem(alreadyExistsItem: AlreadyExistsItem, position: Int) {
        val onItemUpdated = object: ConfigureDownloadBottomSheetDialog.OnDownloadItemUpdateListener {
            override fun onDownloadItemUpdate(
                item: DownloadItem
            ) {
                val currentIndex = duplicates.indexOf(alreadyExistsItem)
                val current = duplicates[currentIndex]
                duplicates[currentIndex] = AlreadyExistsItem(item, current.historyID)
                adapter.submitList(duplicates)
                adapter.notifyItemChanged(position)
            }
        }
        val bottomSheet = ConfigureDownloadBottomSheetDialog(alreadyExistsItem.downloadItem, onItemUpdated)
        bottomSheet.show(requireActivity().supportFragmentManager, "configureDownloadSingleSheet")
    }

    override fun onDeleteItem(alreadyExistsItem: AlreadyExistsItem, position: Int) {
        UiUtil.showGenericDeleteDialog(requireContext(), alreadyExistsItem.downloadItem.title) {
            if (alreadyExistsItem.historyID == null) {
                CoroutineScope(Dispatchers.IO).launch {
                    downloadViewModel.deleteDownload(alreadyExistsItem.downloadItem.id)
                }
            }
            duplicates.remove(alreadyExistsItem)
            if (duplicates.isEmpty()) {
                dismiss()
            }
            adapter.submitList(duplicates)
        }
    }

    override fun onShowHistoryItem(historyItemID: Long) {
        lifecycleScope.launch {
            val historyItem = withContext(Dispatchers.IO){
                downloadViewModel.getHistoryItemById(historyItemID)
            }
            UiUtil.showHistoryItemDetailsCard(historyItem, requireActivity(), isPresent = true, preferences,
                removeItem = { item, deleteFile ->
                    historyViewModel.delete(item, deleteFile)
                },
                redownloadItem = { },
                redownloadShowDownloadCard = {}
            )
        }
    }
}