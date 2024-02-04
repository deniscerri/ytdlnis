package com.deniscerri.ytdlnis.ui.downloads

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        return inflater.inflate(R.layout.fragment_download_queue_main_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        workManager = WorkManager.getInstance(requireContext())
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]

        topAppBar = view.findViewById(R.id.logs_toolbar)
        topAppBar.setNavigationOnClickListener { mainActivity.onBackPressedDispatcher.onBackPressed() }

        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)

        (viewPager2.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val fragments = mutableListOf(ActiveDownloadsFragment(), QueuedDownloadsFragment(), CancelledDownloadsFragment(), ErroredDownloadsFragment(), SavedDownloadsFragment())

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
                2 -> tab.text = getString(R.string.cancelled)
                3 -> tab.text = getString(R.string.errored)
                4 -> tab.text = getString(R.string.saved)
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
            }
        })
        mainActivity.hideBottomNavigation()
        initMenu()

        if (arguments?.getString("tab") != null){
            tabLayout.getTabAt(3)!!.select()
            viewPager2.postDelayed( {
                viewPager2.setCurrentItem(3, false)
            }, 200)
        }
    }

    private fun initMenu() {
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            try{
                when(m.itemId){
                    R.id.clear_queue -> {
                        showDeleteDialog {
                            cancelAllDownloads()
                        }
                    }
                    R.id.clear_cancelled -> {
                        showDeleteDialog {
                            downloadViewModel.deleteCancelled()
                        }
                    }
                    R.id.clear_errored -> {
                        showDeleteDialog {
                            downloadViewModel.deleteErrored()
                        }
                    }
                    R.id.clear_saved -> {
                        showDeleteDialog {
                            downloadViewModel.deleteSaved()
                        }
                    }
                    R.id.copy_urls -> {
                        lifecycleScope.launch {
                            val tabStatus = mapOf(
                                0 to listOf(DownloadRepository.Status.Active, DownloadRepository.Status.ActivePaused, DownloadRepository.Status.PausedReQueued),
                                1 to listOf(DownloadRepository.Status.Queued, DownloadRepository.Status.Queued),
                                2 to listOf(DownloadRepository.Status.Cancelled),
                                3 to listOf(DownloadRepository.Status.Error),
                                4 to listOf(DownloadRepository.Status.Saved),
                            )
                            tabStatus[tabLayout.selectedTabPosition]?.apply {
                                val urls = withContext(Dispatchers.IO){
                                    downloadViewModel.getURLsByStatus(this@apply)
                                }
                                UiUtil.copyToClipboard(urls.joinToString("\n"), requireActivity())
                            }
                        }

                    }
                }
            }catch (e: Exception){
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }

            true
        }
    }

    private fun showDeleteDialog (deleteClicked: (deleteClicked: Boolean) -> Unit){
        val deleteDialog = MaterialAlertDialogBuilder(requireContext())
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete_multiple_items))
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            deleteClicked(true)
        }
        deleteDialog.show()
    }

    private fun cancelAllDownloads() {
        workManager.cancelAllWorkByTag("download")
        lifecycleScope.launch {
            val notificationUtil = NotificationUtil(requireContext())
            val activeAndQueued = withContext(Dispatchers.IO){
                downloadViewModel.getActiveAndQueuedDownloadIDs()
            }
            downloadViewModel.cancelActiveQueued()
            activeAndQueued.forEach { id ->
                YoutubeDL.getInstance().destroyProcessById(id.toString())
                notificationUtil.cancelDownloadNotification(id.toInt())
            }
        }
    }


    companion object {
        private const val TAG = "DownloadQueueActivity"
    }
}