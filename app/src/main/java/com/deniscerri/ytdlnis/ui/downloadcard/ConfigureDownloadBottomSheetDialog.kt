package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.findFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigureDownloadBottomSheetDialog(private val downloadItem: DownloadItem) : BottomSheetDialogFragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadFragmentAdapter
    private lateinit var currentItem : DownloadItem
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        currentItem = downloadViewModel.cloneDownloadItem(downloadItem)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val behavior = BottomSheetBehavior.from(view.parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.configure_download_bottom_sheet, null)
        dialog.setContentView(view)
        //view.minimumHeight = resources.displayMetrics.heightPixels

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

        val download = view.findViewById<Button>(R.id.bottom_sheet_ok)
        download!!.setOnClickListener {
            downloadViewModel.updateDownload(downloadItem)
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        returnPreviousState();
        cleanUp()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanUp()
    }

    private fun returnPreviousState(){
        CoroutineScope(Dispatchers.IO).launch {
            val result =  resultViewModel.getItemByURL(currentItem.url)
            result.title = currentItem.title
            result.author = currentItem.author
            Log.e("TAG, ", currentItem.toString())
            resultViewModel.update(result)
            downloadViewModel.updateDownload(currentItem)
        }
    }

    private fun cleanUp(){
        parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("configureDownloadSingleSheet")!!).commit()
        for (i in 0 until viewPager2.adapter?.itemCount!!){
            if (parentFragmentManager.findFragmentByTag("f${i}") != null){
                parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("f$i")!!).commit()
            }
        }
    }

}

