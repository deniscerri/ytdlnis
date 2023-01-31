package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Adapter
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.get
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.findFragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.work.DownloadWorker
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

class DownloadBottomSheetDialog(item: DownloadItem) : BottomSheetDialogFragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadFragmentAdapter
    private val downloadItem = item
    private lateinit var downloadViewModel: DownloadViewModel

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
        val view = LayoutInflater.from(context).inflate(R.layout.download_bottom_sheet, null)
        dialog.setContentView(view)

        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)

        val fragmentManager = parentFragmentManager
        fragmentAdapter = DownloadFragmentAdapter(downloadItem, fragmentManager, lifecycle)
        viewPager2.adapter = fragmentAdapter
        viewPager2.isSaveFromParentEnabled = false

        when(downloadItem.type) {
            "audio" -> {
                tabLayout.selectTab(tabLayout.getTabAt(0))
                viewPager2.setCurrentItem(0, false)
            }
            "video" -> {
                tabLayout.selectTab(tabLayout.getTabAt(1))
                viewPager2.setCurrentItem(1, false)
            }
            "command" -> {
                tabLayout.selectTab(tabLayout.getTabAt(2))
                viewPager2.setCurrentItem(2, false)
            }
        }


        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewPager2.currentItem = tab!!.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        viewPager2.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.selectTab(tabLayout.getTabAt(position))
            }
        })

        val cancelBtn = view.findViewById<MaterialButton>(R.id.bottomsheet_cancel_button)
        cancelBtn.setOnClickListener{
            dismiss()
        }

        val download = view.findViewById<Button>(R.id.bottomsheet_download_button)
        download!!.setOnClickListener {
            downloadViewModel.queueDownloads(listOf(downloadItem))
            dismiss()
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
        parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("downloadSingleSheet")!!).commit()
        for (i in 0 until viewPager2.adapter?.itemCount!!){
            if (parentFragmentManager.findFragmentByTag("f${i}") != null){
                parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("f$i")!!).commit()
            }
        }
        downloadViewModel.deleteSingleProcessing(downloadItem)
    }

}

