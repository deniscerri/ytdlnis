package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.DBManager
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.receiver.ShareActivity
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.TabLayoutOnPageChangeListener
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*


class DownloadBottomSheetDialog(private val resultItem: ResultItem, private val type: Type) : BottomSheetDialogFragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadFragmentAdapter
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var commandTemplateDao : CommandTemplateDao
    private lateinit var uiUtil: UiUtil

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        commandTemplateDao = DBManager.getInstance(requireContext()).commandTemplateDao
        uiUtil = UiUtil(FileUtil())
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.download_bottom_sheet, null)
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

        val fragments = mutableListOf<Fragment>(DownloadAudioFragment(resultItem, null), DownloadVideoFragment(resultItem, null))
        var commandTemplateNr = 0
        lifecycleScope.launch{
            withContext(Dispatchers.IO){
                commandTemplateNr = commandTemplateDao.getTotalNumber()
                if(commandTemplateNr > 0){
                    fragments.add(DownloadCommandFragment(resultItem, null))
                }else{
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.isClickable = true
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.alpha = 0.3f
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

        when(type) {
            Type.audio -> {
                tabLayout.getTabAt(0)!!.select()
                viewPager2.setCurrentItem(0, false)
            }
            Type.video -> {
                tabLayout.getTabAt(1)!!.select()
                viewPager2.setCurrentItem(1, false)
            }
            else -> {
                tabLayout.getTabAt(2)!!.select()
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

        val scheduleBtn = view.findViewById<MaterialButton>(R.id.bottomsheet_schedule_button)
        scheduleBtn.setOnClickListener{
            uiUtil.showDatePicker(fragmentManager) {
                val item: DownloadItem = getDownloadItem();
                item.downloadStartTime = it.timeInMillis
                runBlocking {
                    downloadViewModel.queueDownloads(listOf(item))
                    val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(item.downloadStartTime)
                    Toast.makeText(context, getString(R.string.download_rescheduled_to) + " " + date, Toast.LENGTH_LONG).show()
                }
                dismiss()
            }
        }
        val download = view.findViewById<Button>(R.id.bottomsheet_download_button)
        download!!.setOnClickListener {
            val item: DownloadItem = getDownloadItem();
            runBlocking {
                downloadViewModel.queueDownloads(listOf(item))
            }
            dismiss()
        }

        val link = view.findViewById<Button>(R.id.bottom_sheet_link)
        link.text = resultItem.url
        link.setOnClickListener{
            uiUtil.openLinkIntent(requireContext(), resultItem.url, null)
        }
        link.setOnLongClickListener{
            uiUtil.copyLinkToClipBoard(requireContext(), resultItem.url, null)
            true
        }
    }

    private fun getDownloadItem() : DownloadItem{
        return when(tabLayout.selectedTabPosition){
            0 -> {
                val f = fragmentManager?.findFragmentByTag("f0") as DownloadAudioFragment
                f.downloadItem
            }
            1 -> {
                val f = fragmentManager?.findFragmentByTag("f1") as DownloadVideoFragment
                f.downloadItem
            }
            else -> {
                val f = fragmentManager?.findFragmentByTag("f2") as DownloadCommandFragment
                f.downloadItem
            }
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
            parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("downloadSingleSheet")!!).commit()
            for (i in 0 until viewPager2.adapter?.itemCount!!){
                if (parentFragmentManager.findFragmentByTag("f${i}") != null){
                    parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("f$i")!!).commit()
                }
            }
            if (activity is ShareActivity){
                (activity as ShareActivity).finish()
            }
        }
    }
}

