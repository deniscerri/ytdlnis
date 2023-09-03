package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ConfigureDownloadBottomSheetDialog(private val resultItem: ResultItem, private val downloadItem: DownloadItem, private val listener: OnDownloadItemUpdateListener) : BottomSheetDialogFragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadFragmentAdapter
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var commandTemplateDao: CommandTemplateDao
    private lateinit var behavior: BottomSheetBehavior<View>
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

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels - 400
        }

        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)

        (viewPager2.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val fragments = mutableListOf(
            DownloadAudioFragment(resultItem, downloadItem),
            DownloadVideoFragment(resultItem, downloadItem)
        )
        var commandTemplateNr = 0
        lifecycleScope.launch{
            withContext(Dispatchers.IO){
                commandTemplateNr = commandTemplateDao.getTotalNumber()
                if(commandTemplateNr > 0){
                    fragments.add(DownloadCommandFragment(resultItem, downloadItem))
                }else{
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.isClickable = true
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.alpha = 0.3f
                }
            }
        }

        //check if the item has formats and its audio-only
        val isAudioOnly = resultItem.formats.isNotEmpty() && resultItem.formats.none { !it.format_note.contains("audio") }
        if (isAudioOnly){
            (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.isClickable = true
            (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(1)?.alpha = 0.3f
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
                if (isAudioOnly){
                    tabLayout.getTabAt(0)!!.select()
                    viewPager2.setCurrentItem(0, false)
                    Toast.makeText(context, getString(R.string.audio_only_item), Toast.LENGTH_SHORT).show()
                }else{
                    tabLayout.getTabAt(1)!!.select()
                    viewPager2.setCurrentItem(1, false)
                }
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
                if (tab!!.position == 2 && commandTemplateNr == 0){
                    tabLayout.selectTab(tabLayout.getTabAt(1))
                    Toast.makeText(context, getString(R.string.add_template_first), Toast.LENGTH_SHORT).show()
                }else if (tab.position == 1 && isAudioOnly){
                    tabLayout.selectTab(tabLayout.getTabAt(0))
                    Toast.makeText(context, getString(R.string.audio_only_item), Toast.LENGTH_SHORT).show()
                }else{
                    viewPager2.setCurrentItem(tab.position, false)
                }
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

        val link = view.findViewById<Button>(R.id.bottom_sheet_link)
        link.text = resultItem.url
        link.setOnClickListener{
            UiUtil.openLinkIntent(requireContext(), resultItem.url, null)
        }
        link.setOnLongClickListener{
            UiUtil.copyLinkToClipBoard(requireContext(), resultItem.url, null)
            true
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
        kotlin.runCatching {
            parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("configureDownloadSingleSheet")!!).commit()
            for (i in 0 until viewPager2.adapter?.itemCount!!){
                if (parentFragmentManager.findFragmentByTag("f${i}") != null){
                    parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("f$i")!!).commit()
                }
            }
        }
    }

}
