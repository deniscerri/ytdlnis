package com.deniscerri.ytdl.ui.more.downloadLogs

import android.annotation.SuppressLint
import android.app.ActionBar
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.view.ScrollingView
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.LogViewModel
import com.deniscerri.ytdl.ui.adapter.LogAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.deniscerri.ytdl.util.Extensions.enableTextHighlight
import com.deniscerri.ytdl.util.Extensions.setCustomTextSize
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.work.DownloadWorker
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class DownloadLogFragment : Fragment() {
    private lateinit var logRecyclerView: RecyclerView
    private lateinit var horizontalScrollingView: HorizontalScrollView
    private lateinit var logAdapter: LogAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var copyLog : ExtendedFloatingActionButton
    private lateinit var mainActivity: MainActivity
    private lateinit var logViewModel: LogViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private var logID: Long? = null
    private var contentText: String = ""
    private var contentLineCount: Int = 0

    private var autoScroll : Boolean = true
    private var scrollDownBtn : MenuItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        mainActivity.hideBottomNavigation()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return inflater.inflate(R.layout.fragment_download_log, container, false)
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        topAppBar = view.findViewById(R.id.title)
        topAppBar.setNavigationOnClickListener {
            mainActivity.onBackPressedDispatcher.onBackPressed()
        }

        logRecyclerView = view.findViewById(R.id.log_recycler_view)
        logAdapter = LogAdapter()
        logRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        logRecyclerView.adapter = logAdapter

        logAdapter.isWrapped = sharedPreferences.getBoolean("wrap_text_log", false)
        logAdapter.textSize = sharedPreferences.getFloat("log_zoom", 2f) + 13f
        logAdapter.highlight = sharedPreferences.getBoolean("use_code_color_highlighter", true)

        horizontalScrollingView = view.findViewById(R.id.horizontal_container)

        val bottomAppBar = view.findViewById<BottomAppBar>(R.id.bottomAppBar)
        topAppBar.setOnClickListener {
            logRecyclerView.scrollTo(0,0)
            bottomAppBar?.menu?.get(1)?.isVisible = true
        }

        copyLog = view.findViewById(R.id.copy_log)
        copyLog.setOnClickListener {
            val clipboard: ClipboardManager =
                mainActivity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setText(contentText)
            Snackbar.make(bottomAppBar, getString(R.string.copied_to_clipboard), Snackbar.LENGTH_LONG)
                .setAnchorView(bottomAppBar)
                .show()
        }

        logID = arguments?.getLong("logID")
        if (logID == null || logID == 0L) {
            mainActivity.onBackPressedDispatcher.onBackPressed()
        }


        logViewModel = ViewModelProvider(this)[LogViewModel::class.java]

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val logItem = logViewModel.getItemById(logID!!) ?: throw Exception()
                withContext(Dispatchers.Main){
                    topAppBar.title = logItem.title
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Log is deleted!", Toast.LENGTH_SHORT).show()
                    mainActivity.onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        //logRecyclerView.enableFastScroll()
        scrollDownBtn = bottomAppBar?.menu?.children?.first { it.itemId == R.id.scroll_down }

        val slider = view.findViewById<Slider>(R.id.textsize_seekbar)
        bottomAppBar?.setOnMenuItemClickListener { m: MenuItem ->
            when(m.itemId){
                R.id.wrap -> {
                    val newState = !logAdapter.isWrapped
                    logAdapter.isWrapped = newState
                    sharedPreferences.edit(commit = true) { putBoolean("wrap_text_log", newState) }
                    logAdapter.notifyDataSetChanged()
                }

                R.id.scroll_down -> {
                    m.isVisible = false
                    autoScroll = true
                    logRecyclerView.smoothScrollToPosition(contentLineCount)
                }

                R.id.text_size -> {
                    slider!!.isVisible = !slider.isVisible
                }

                R.id.export_file -> {
                    logViewModel.exportToFile(logID!!) {f ->
                        if (f == null){
                            Snackbar.make(bottomAppBar, getString(R.string.couldnt_parse_file), Snackbar.LENGTH_LONG)
                                .setAnchorView(bottomAppBar)
                                .show()
                        }else{
                            val snack = Snackbar.make(bottomAppBar, getString(R.string.backup_created_successfully), Snackbar.LENGTH_LONG)
                            snack.setAnchorView(bottomAppBar)
                            snack.setAction(R.string.share) {
                                FileUtil.shareFileIntent(requireContext(), listOf(f.absolutePath))
                            }
                            snack.show()
                        }
                    }
                }
            }
            true
        }

        slider?.apply {
            this.valueFrom = 0f
            this.valueTo = 10f
            this.value = sharedPreferences.getFloat("log_zoom", 2f)

            logAdapter.textSize = this.value + 13f
            logAdapter.notifyDataSetChanged()

            this.addOnChangeListener { slider, value, fromUser ->
                logAdapter.textSize = value + 13f
                logAdapter.notifyDataSetChanged()
                sharedPreferences.edit(true){
                    putFloat("log_zoom", value)
                }
            }
        }

        logRecyclerView.setOnScrollChangeListener { view, sx, sy, osx, osy ->
            updateAutoScrollState()
        }

        logRecyclerView.setOnTouchListener { view, motionEvent ->
            autoScroll = false
            false
        }

        logRecyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            private var startX = 0f
            private var startY = 0f

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        // Prevent parent from stealing the initial touch
                        rv.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(e.x - startX)
                        val dy = Math.abs(e.y - startY)

                        // If moving more vertically than horizontally, lock vertical scroll
                        if (dy > dx) {
                            rv.parent.requestDisallowInterceptTouchEvent(true)
                        } else if (dx > dy && !logAdapter.isWrapped) {
                            // If moving horizontally and wrap is OFF, let the parent take it
                            rv.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(
                rv: RecyclerView,
                e: MotionEvent
            ) {
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            }
        })

        logViewModel.getLogFlowByID(logID!!).observe(viewLifecycleOwner){logItem ->
            kotlin.runCatching {
                requireActivity().runOnUiThread{
                    if (logItem != null){
                        if (logItem.content.isNotBlank()) {
                            contentText = logItem.content
                            val lines = contentText.split("\n")
                            contentLineCount = lines.size
                            logAdapter.submitList(lines)
                            bottomAppBar?.menu?.get(1)?.isVisible = logRecyclerView.canScrollVertically(1)
                        }
                        if (autoScroll && contentLineCount > 0){
                            logRecyclerView.post {
                                logRecyclerView.smoothScrollToPosition(contentLineCount)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateAutoScrollState() {
        val canVerticallyScroll = logRecyclerView.canScrollVertically(1)
        scrollDownBtn?.isVisible = canVerticallyScroll
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    //dont remove
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadProgressEvent(event: DownloadWorker.WorkerProgress) {
        val progressBar = requireView().findViewById<LinearProgressIndicator>(R.id.progress)
        if (event.logItemID == logID) {
            progressBar.isVisible = event.progress < 100
            progressBar.setProgressCompat(event.progress, true)
        }
    }
}