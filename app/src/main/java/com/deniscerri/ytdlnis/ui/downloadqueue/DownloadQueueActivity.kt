package com.deniscerri.ytdlnis.ui.downloadqueue

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.adapter.ActiveDownloadAdapter
import com.deniscerri.ytdlnis.adapter.QueuedDownloadAdapter
import com.deniscerri.ytdlnis.database.dao.CommandTemplateDao
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.ui.downloadcard.*
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class DownloadQueueActivity : AppCompatActivity(){
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var topAppBar: MaterialToolbar

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadListFragmentAdapter
    var context: Context? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_queue)
        context = baseContext
        val view : View = window.decorView.findViewById(android.R.id.content)


        topAppBar = findViewById(R.id.logs_toolbar)
        topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)

        (viewPager2.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val fragments = mutableListOf(ActiveDownloadsFragment(), QueuedDownloadsFragment(), CancelledDownloadsFragment(), ErroredDownloadsFragment())
//
//        lifecycleScope.launch{
//            withContext(Dispatchers.IO){
//                val active = commandTemplateDao.getTotalNumber()
//                if(nr > 0){
//                    fragments.add(DownloadCommandFragment(resultItem))
//                }else{
//                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.isEnabled = false
//                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.alpha = 0.3f
//                }
//            }
//        }


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

        //initMenu()
    }

//    private fun initMenu() {
//        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
//            val itemId = m.itemId
//            if (itemId == R.id.remove_logs) {
//                try{
//                    logFolder.listFiles()!!.forEach {
//                        it.delete()
//                    }
//                }catch (e: Exception){
//                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
//                }
//            }
//            true
//        }
//    }


    companion object {
        private const val TAG = "DownloadQueueActivity"
    }
}