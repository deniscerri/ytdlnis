package com.deniscerri.ytdl.ui.more.downloadLogs

import android.annotation.SuppressLint
import android.app.ActionBar
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.view.children
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.LogViewModel
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
    private lateinit var content: TextView
    private lateinit var contentScrollView : ScrollView
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var copyLog : ExtendedFloatingActionButton
    private lateinit var mainActivity: MainActivity
    private lateinit var logViewModel: LogViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private var logID: Long? = null

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

        content = view.findViewById(R.id.content)
        content.setTextIsSelectable(true)
        content.layoutParams!!.width = ActionBar.LayoutParams.WRAP_CONTENT
        contentScrollView = view.findViewById(R.id.content_scrollview)
        val bottomAppBar = view.findViewById<BottomAppBar>(R.id.bottomAppBar)

        topAppBar.setOnClickListener {
            contentScrollView.scrollTo(0,0)
            bottomAppBar?.menu?.get(1)?.isVisible = true
        }


        copyLog = view.findViewById(R.id.copy_log)
        copyLog.setOnClickListener {
            val clipboard: ClipboardManager =
                mainActivity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setText(content.text)
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

        lifecycleScope.launch(Dispatchers.IO){
            content.isFocusable = true
            if (sharedPreferences.getBoolean("use_code_color_highlighter", true)) {
                content.enableTextHighlight()
            }
        }

        contentScrollView.enableFastScroll()

        scrollDownBtn = bottomAppBar?.menu?.children?.first { it.itemId == R.id.scroll_down }

        val slider = view.findViewById<Slider>(R.id.textsize_seekbar)
        bottomAppBar?.setOnMenuItemClickListener { m: MenuItem ->
            when(m.itemId){
                R.id.wrap -> {
                    var scrollView = requireView().findViewById<HorizontalScrollView>(R.id.horizontalscroll_output)
                    if(scrollView != null){
                        val parent = (scrollView.parent as ViewGroup)
                        scrollView.removeAllViews()
                        parent.removeView(scrollView)
                        parent.addView(content, 0)
//                        contentScrollView.setPadding(0,0,0,
//                            (requireContext().resources.displayMetrics.density * 150).toInt()
//                        )
                        sharedPreferences.edit().putBoolean("wrap_text_log", true).apply()
                        updateAutoScrollState()
                    }else{
                        val parent = content.parent as ViewGroup
                        parent.removeView(content)
                        scrollView = HorizontalScrollView(requireContext())
//                        scrollView.setPadding(0,0,0,
//                            (requireContext().resources.displayMetrics.density * 150).toInt()
//                        )
                        contentScrollView.setPadding(0,0,0,0)
                        scrollView.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scrollView.addView(content)
                        scrollView.id = R.id.horizontalscroll_output
                        parent.addView(scrollView, 0)
                        updateAutoScrollState()
                        sharedPreferences.edit().putBoolean("wrap_text_log", false).apply()
                    }
                }

                R.id.scroll_down -> {
                    m.isVisible = false
                    autoScroll = true
                    contentScrollView.fullScroll(View.FOCUS_DOWN)
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
            content.setCustomTextSize(this.value + 13f)
            this.addOnChangeListener { slider, value, fromUser ->
                content.setCustomTextSize(value + 13f)
                sharedPreferences.edit(true){
                    putFloat("log_zoom", value)
                }
            }
        }

        contentScrollView.setOnScrollChangeListener { view, sx, sy, osx, osy ->
            updateAutoScrollState()
        }

        contentScrollView.setOnTouchListener { view, motionEvent ->
            autoScroll = false
            false
        }

        sharedPreferences.getBoolean("wrap_text_log", false).apply {
            if (this){
                bottomAppBar.menu.performIdentifierAction(R.id.wrap, 0)
            }
        }

        logViewModel.getLogFlowByID(logID!!).observe(viewLifecycleOwner){logItem ->
            kotlin.runCatching {
                requireActivity().runOnUiThread{
                    if (logItem != null){
                        if (logItem.content.isNotBlank()) {
                            content.setText(logItem.content, TextView.BufferType.SPANNABLE)
                            bottomAppBar?.menu?.get(1)?.isVisible = contentScrollView.canScrollVertically(1)
                        }
                        if (autoScroll){
                            //content.scrollTo(0, content.height)
                            content.post {
                                contentScrollView.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateAutoScrollState() {
        val canVerticallyScroll = contentScrollView.canScrollVertically(1)
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

    companion object {
        private const val TAG = "DownloadLogActivity"
    }
}