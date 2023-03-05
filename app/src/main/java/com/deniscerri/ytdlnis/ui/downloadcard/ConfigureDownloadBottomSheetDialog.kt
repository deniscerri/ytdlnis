package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.google.android.exoplayer2.Player
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ConfigureDownloadBottomSheetDialog(private val resultItem: ResultItem, private val downloadItem: DownloadItem, private val listener: OnDownloadItemUpdateListener) : BottomSheetDialogFragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadFragmentAdapter
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var commandTemplateDao: CommandTemplateDao
    private lateinit var onDownloadItemUpdateListener: OnDownloadItemUpdateListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        commandTemplateDao = DBManager.getInstance(requireContext()).commandTemplateDao
        onDownloadItemUpdateListener = listener

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

        val fragments = mutableListOf<Fragment>(DownloadAudioFragment(resultItem, downloadItem), DownloadVideoFragment(resultItem, downloadItem))

        lifecycleScope.launch{
            withContext(Dispatchers.IO){
                val nr = commandTemplateDao.getTotalNumber()
                if(nr > 0){
                    fragments.add(DownloadCommandFragment(resultItem, downloadItem))
                }else{
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.isEnabled = false
                }
            }
        }


        val fragmentManager = parentFragmentManager
        fragmentAdapter = DownloadFragmentAdapter(
            resultItem,
            fragmentManager,
            lifecycle,
            fragments
        )
        viewPager2.adapter = fragmentAdapter
        viewPager2.isSaveFromParentEnabled = false

        when(downloadItem.type) {
            Type.audio -> {
                tabLayout.selectTab(tabLayout.getTabAt(0))
                viewPager2.setCurrentItem(0, false)
            }
            Type.video -> {
                tabLayout.selectTab(tabLayout.getTabAt(1))
                viewPager2.setCurrentItem(1, false)
            }
            else -> {
                tabLayout.selectTab(tabLayout.getTabAt(2))
                viewPager2.postDelayed( {
                    viewPager2.setCurrentItem(2, false)
                }, 200)
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

        viewPager2.setPageTransformer(BackgroundToForegroundPageTransformer())

        val ok = view.findViewById<Button>(R.id.bottom_sheet_ok)
        ok!!.setOnClickListener {

            val item : DownloadItem = when(tabLayout.selectedTabPosition){
                0 -> {
                    val f = fragmentManager.findFragmentByTag("f0") as DownloadAudioFragment
                    f.downloadItem
                }
                1 -> {
                    val f = fragmentManager.findFragmentByTag("f1") as DownloadVideoFragment
                    f.downloadItem
                }
                else -> {
                    val f = fragmentManager.findFragmentByTag("f2") as DownloadCommandFragment
                    f.downloadItem
                }
            }
            Log.e("aa", item.toString())
            onDownloadItemUpdateListener.onDownloadItemUpdate(resultItem.id, item)
            dismiss()
        }

    }


    interface OnDownloadItemUpdateListener {
        fun onDownloadItemUpdate(resultItemID: Long, item: DownloadItem)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        //returnPreviousState();
        cleanUp()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanUp()
    }

//    private fun returnPreviousState(){
//        CoroutineScope(Dispatchers.IO).launch {
//            val result =  resultViewModel.getItemByURL(currentItem.url)
//            result.title = currentItem.title
//            result.author = currentItem.author
//            Log.e("TAG, ", currentItem.toString())
//            resultViewModel.update(result)
//            downloadViewModel.updateDownload(currentItem)
//        }
//    }

    private fun cleanUp(){
        parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("configureDownloadSingleSheet")!!).commit()
        for (i in 0 until viewPager2.adapter?.itemCount!!){
            if (parentFragmentManager.findFragmentByTag("f${i}") != null){
                parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("f$i")!!).commit()
            }
        }
    }

}
