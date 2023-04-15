package com.deniscerri.ytdlnis.ui.downloads

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout


class DownloadQueueActivity : AppCompatActivity(){
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var workManager: WorkManager
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadListFragmentAdapter
    var context: Context? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_queue)
        context = baseContext
        val view : View = window.decorView.findViewById(android.R.id.content)
        workManager = WorkManager.getInstance(this)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]

        topAppBar = findViewById(R.id.logs_toolbar)
        topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)

        (viewPager2.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val fragments = mutableListOf(ActiveDownloadsFragment(), QueuedDownloadsFragment(), CancelledDownloadsFragment(), ErroredDownloadsFragment())

        fragmentAdapter = DownloadListFragmentAdapter(
            supportFragmentManager,
            lifecycle,
            fragments
        )

        viewPager2.adapter = fragmentAdapter
        viewPager2.isSaveFromParentEnabled = false

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

        initMenu()
    }

    private fun initMenu() {
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            try{
                when(m.itemId){
                    R.id.clear_queue -> {
                        cancelAllDownloads()
                        downloadViewModel.deleteQueued()
                    }
                    R.id.clear_cancelled -> {
                        downloadViewModel.deleteCancelled()
                    }
                    R.id.clear_errored -> {
                        downloadViewModel.deleteErrored()
                    }
                }
            }catch (e: Exception){
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }

            true
        }
    }

    private fun cancelAllDownloads() {
        workManager.cancelAllWork()
    }


    companion object {
        private const val TAG = "DownloadQueueActivity"
    }
}