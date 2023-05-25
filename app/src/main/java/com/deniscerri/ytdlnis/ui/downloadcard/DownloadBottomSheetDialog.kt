package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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
import com.deniscerri.ytdlnis.receiver.ShareActivity
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*


class DownloadBottomSheetDialog(private val resultItem: ResultItem, private val type: Type, private val downloadItem: DownloadItem?,private val quickDownload: Boolean) : BottomSheetDialogFragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadFragmentAdapter
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var commandTemplateDao : CommandTemplateDao
    private lateinit var uiUtil: UiUtil
    private lateinit var infoUtil: InfoUtil

    private lateinit var downloadAudioFragment: DownloadAudioFragment
    private lateinit var downloadVideoFragment: DownloadVideoFragment
    private lateinit var downloadCommandFragment: DownloadCommandFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        commandTemplateDao = DBManager.getInstance(requireContext()).commandTemplateDao
        uiUtil = UiUtil(FileUtil())
        infoUtil = InfoUtil(requireContext())
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
            if(resources.getBoolean(R.bool.isTablet)){
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }


        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)

        (viewPager2.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        downloadAudioFragment = DownloadAudioFragment(resultItem, downloadItem)
        downloadVideoFragment = DownloadVideoFragment(resultItem, downloadItem)
        val fragments = mutableListOf(downloadAudioFragment, downloadVideoFragment)
        var commandTemplateNr = 0
        lifecycleScope.launch{
            withContext(Dispatchers.IO){
                commandTemplateNr = commandTemplateDao.getTotalNumber()
                if(commandTemplateNr > 0){
                    downloadCommandFragment = DownloadCommandFragment(resultItem, downloadItem)
                    fragments.add(downloadCommandFragment)
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
        val download = view.findViewById<Button>(R.id.bottomsheet_download_button)


        scheduleBtn.setOnClickListener{
            uiUtil.showDatePicker(fragmentManager) {
                scheduleBtn.isEnabled = false
                download.isEnabled = false
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
        download!!.setOnClickListener {
            scheduleBtn.isEnabled = false
            download.isEnabled = false
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

        val updateItem = view.findViewById<Button>(R.id.update_item)
        if (quickDownload) {
            (updateItem.parent as LinearLayout).visibility = View.VISIBLE
            updateItem.setOnClickListener {
                if (activity is ShareActivity) {
                    dismiss()
                    val intent = Intent(context, ShareActivity::class.java)
                    intent.action = Intent.ACTION_SEND
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_TEXT, resultItem.url)
                    intent.putExtra("quick_download", false)
                    startActivity(intent)
                }else{
                    dismiss()
                    val bundle = Bundle()
                    bundle.putString("url", resultItem.url)
                    findNavController().popBackStack(R.id.homeFragment, true)
                    findNavController().navigate(
                        R.id.homeFragment,
                        bundle
                    )
                }
            }
        }else{
            (updateItem.parent as LinearLayout).visibility = View.GONE
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
            parentFragmentManager.beginTransaction().remove(downloadVideoFragment).commit()
            parentFragmentManager.beginTransaction().remove(downloadVideoFragment).commit()
            if (this::downloadCommandFragment.isInitialized){
                parentFragmentManager.beginTransaction().remove(downloadCommandFragment).commit()
            }
            if (activity is ShareActivity){
                (activity as ShareActivity).finish()
            }
        }
    }
}

