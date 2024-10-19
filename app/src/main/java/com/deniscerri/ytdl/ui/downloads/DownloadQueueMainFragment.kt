package com.deniscerri.ytdl.ui.downloads

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.work.WorkManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.util.Extensions.createBadge
import com.deniscerri.ytdl.util.NavbarUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class DownloadQueueMainFragment : Fragment(){
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var workManager: WorkManager
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadListFragmentAdapter
    private lateinit var mainActivity: MainActivity
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        notificationUtil = NotificationUtil(mainActivity)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return inflater.inflate(R.layout.fragment_download_queue_main_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        workManager = WorkManager.getInstance(requireContext())
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]

        topAppBar = view.findViewById(R.id.downloads_toolbar)
        val isInNavBar = NavbarUtil.getNavBarItems(requireActivity()).any { n -> n.itemId == R.id.downloadQueueMainFragment && n.isVisible }
        if (isInNavBar) {
            topAppBar.navigationIcon = null
        }else{
            mainActivity.hideBottomNavigation()
        }
        topAppBar.setNavigationOnClickListener {
            mainActivity.onBackPressedDispatcher.onBackPressed()
        }

        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)

        (viewPager2.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val fragments = mutableListOf(ActiveDownloadsFragment(), QueuedDownloadsFragment(), ScheduledDownloadsFragment(), CancelledDownloadsFragment(), ErroredDownloadsFragment(), SavedDownloadsFragment())

        fragmentAdapter = DownloadListFragmentAdapter(
            childFragmentManager,
            lifecycle,
            fragments
        )

        viewPager2.adapter = fragmentAdapter
        viewPager2.isSaveFromParentEnabled = false

        TabLayoutMediator(tabLayout, viewPager2) { tab, position ->
            when (position) {
                0 -> tab.text = getString(R.string.running)
                1 -> tab.text = getString(R.string.in_queue)
                2 -> tab.text = getString(R.string.scheduled)
                3 -> tab.text = getString(R.string.cancelled)
                4 -> tab.text = getString(R.string.errored)
                5 -> tab.text = getString(R.string.saved)
            }
        }.attach()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewPager2.setCurrentItem(tab!!.position, true)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        viewPager2.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.selectTab(tabLayout.getTabAt(position))
                initMenu()
            }
        })
        initMenu()

        if (arguments?.getString("tab") != null){
            tabLayout.getTabAt(4)!!.select()
            viewPager2.postDelayed( {
                viewPager2.setCurrentItem(4, false)
                val reconfigureID = arguments?.getLong("reconfigure")
                reconfigureID?.apply {
                    notificationUtil.cancelErroredNotification(this.toInt())
                    lifecycleScope.launch {
                        kotlin.runCatching {
                            val item = withContext(Dispatchers.IO){
                                downloadViewModel.getItemByID(reconfigureID)
                            }
                            findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                                Pair("downloadItem", item),
                                Pair("result", downloadViewModel.createResultItemFromDownload(item)),
                                Pair("type", item.type)
                            )
                            )
                        }
                    }

                }
            }, 200)
        }

        arguments?.clear()

        if (sharedPreferences.getBoolean("show_count_downloads", false)){
            lifecycleScope.launch {
                downloadViewModel.activeDownloadsCount.collectLatest {
                    tabLayout.getTabAt(0)?.apply {
                        createBadge(it)
                    }
                }
            }
            lifecycleScope.launch {
                downloadViewModel.queuedDownloadsCount.collectLatest {
                    tabLayout.getTabAt(1)?.apply {
                        createBadge(it)
                    }
                }
            }
            lifecycleScope.launch {
                downloadViewModel.scheduledDownloadsCount.collectLatest {
                    tabLayout.getTabAt(2)?.apply {
                        createBadge(it)
                    }
                }
            }
            lifecycleScope.launch {
                downloadViewModel.cancelledDownloadsCount.collectLatest {
                    tabLayout.getTabAt(3)?.apply {
                        createBadge(it)
                    }
                }
            }
            lifecycleScope.launch {
                downloadViewModel.erroredDownloadsCount.collectLatest {
                    tabLayout.getTabAt(4)?.apply {
                        removeBadge()
                        if (it > 0) createBadge(it)
                    }
                }
            }
            lifecycleScope.launch {
                downloadViewModel.savedDownloadsCount.collectLatest {
                    tabLayout.getTabAt(5)?.apply {
                        removeBadge()
                        if (it > 0) createBadge(it)
                    }
                }
            }
        }

    }

    private fun initMenu() {

        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            try{
                when(m.itemId){
                    R.id.clear_all -> {
                        UiUtil.showGenericDeleteAllDialog(requireContext()) {
                            downloadViewModel.deleteAll()
                        }
                    }
                    R.id.clear_queue -> {
                        UiUtil.showGenericDeleteAllDialog(requireContext()) {
                            downloadViewModel.cancelAllDownloads()
                        }
                    }
                }
            }catch (e: Exception){
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }

            true
        }
    }

    fun scrollToActive(){
        tabLayout.getTabAt(0)!!.select()
        viewPager2.setCurrentItem(0, true)
    }
}