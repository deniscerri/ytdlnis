package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.findFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.ConfigureMultipleDownloadsAdapter
import com.deniscerri.ytdlnis.adapter.HomeAdapter
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadMultipleBottomSheetDialog(private val items: List<DownloadItem>) : BottomSheetDialogFragment(), ConfigureMultipleDownloadsAdapter.OnItemClickListener, View.OnClickListener {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var listAdapter : ConfigureMultipleDownloadsAdapter
    private lateinit var recyclerView: RecyclerView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
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
        view.minimumHeight = resources.displayMetrics.heightPixels

        listAdapter =
            ConfigureMultipleDownloadsAdapter(
                this,
                requireActivity()
            )

        recyclerView = view.findViewById(R.id.downloadMultipleRecyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = listAdapter

        downloadViewModel.processingDownloads.observe(this) {
            listAdapter.submitList(it)
            if (it.isEmpty()){
                dismiss()
            }
        }

        val cancelBtn = view.findViewById<MaterialButton>(R.id.bottomsheet_cancel_button)
        cancelBtn.setOnClickListener{
            dismiss()
        }

        val download = view.findViewById<Button>(R.id.bottomsheet_download_button)
        download!!.setOnClickListener {
            downloadViewModel.queueDownloads(items)
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        cleanDownloads()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanDownloads()
    }

    private fun cleanDownloads(){
        parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("downloadMultipleSheet")!!).commit()
        downloadViewModel.deleteProcessing()
    }

    override fun onButtonClick(videoURL: String, type: String?) {
        TODO("Not yet implemented")
    }

    override fun onCardClick(itemID: Long) {
        lifecycleScope.launch{
            val downloadItem = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }
            val bottomSheet = ConfigureDownloadBottomSheetDialog(downloadItem)
            bottomSheet.show(parentFragmentManager, "configureDownloadSingleSheet")
        }
    }

    override fun onClick(p0: View?) {
        TODO("Not yet implemented")
    }

}

