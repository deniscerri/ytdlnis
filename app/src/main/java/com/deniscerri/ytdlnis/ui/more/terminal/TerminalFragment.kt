package com.deniscerri.ytdlnis.ui.more.terminal

import android.app.ActionBar.LayoutParams
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.TerminalItem
import com.deniscerri.ytdlnis.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdlnis.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.util.Extensions.enableTextHighlight
import com.deniscerri.ytdlnis.util.Extensions.setCustomTextSize
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates


class TerminalFragment : Fragment() {
    private var topAppBar: MaterialToolbar? = null
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var terminalViewModel: TerminalViewModel
    private var output: TextView? = null
    private var input: EditText? = null
    private var fab: ExtendedFloatingActionButton? = null
    private var scrollView: ScrollView? = null
    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private var downloadID by Delegates.notNull<Long>()
    private lateinit var imm : InputMethodManager
    private lateinit var metrics: DisplayMetrics

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        terminalViewModel = ViewModelProvider(this)[TerminalViewModel::class.java]
        downloadID = 0
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("input", input?.text.toString())
        outState.putString("output", output?.text.toString())
        outState.putBoolean("run", fab!!.text == requireActivity().getString(R.string.run_command))
        outState.putLong("downloadID", downloadID)
    }

    override fun onResume() {
        arguments?.remove("id")
        arguments?.remove("share")
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var bundle = savedInstanceState
        imm = requireActivity().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        scrollView = view.findViewById(R.id.custom_command_scrollview)
        topAppBar = requireActivity().findViewById(R.id.custom_command_toolbar)
        topAppBar!!.setNavigationOnClickListener { requireActivity().finish() }
        topAppBar!!.setOnClickListener { scrollView?.scrollTo(0,0) }

        input = view.findViewById(R.id.command_edittext)
        fab = view.findViewById(R.id.command_fab)

        if (arguments?.getLong("id") != null){
            downloadID = requireArguments().getLong("id")
            if(downloadID != 0L){
                input!!.visibility = View.GONE
                showCancelFab()
            }
        }

        if (arguments?.containsKey("share") == true){
            if (bundle == null){
                bundle = Bundle()
            }
            bundle.putString("input", arguments?.getString("share"))
        }

        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        metrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(metrics)


        bottomAppBar = view.findViewById(R.id.bottomAppBar)
        var templateCount = 0
        var shortcutCount = 0

        lifecycleScope.launch {
            templateCount = withContext(Dispatchers.IO){
                commandTemplateViewModel.getTotalNumber()
            }
            if (templateCount == 0){
                bottomAppBar.menu.getItem(0).icon?.alpha = 30
            }else{
                bottomAppBar.menu.getItem(0).icon?.alpha = 255
            }

            shortcutCount = withContext(Dispatchers.IO){
                commandTemplateViewModel.getTotalShortcutNumber()
            }
            if (shortcutCount == 0) {
                bottomAppBar.menu.getItem(1).icon?.alpha = 30
            }else{
                bottomAppBar.menu.getItem(1).icon?.alpha = 255
            }

        }
        bottomAppBar.setOnMenuItemClickListener {
            when(it.itemId){
                R.id.command_templates -> {
                    if (templateCount == 0){
                        Toast.makeText(requireContext(), requireActivity().getString(R.string.add_template_first), Toast.LENGTH_SHORT).show()
                    }else{
                        lifecycleScope.launch {
                            UiUtil.showCommandTemplates(requireActivity(), commandTemplateViewModel){ templates ->
                                templates.forEach {c ->
                                    input!!.text.insert(input!!.selectionStart, c.content + " ")
                                }
                                input!!.postDelayed({
                                    input!!.requestFocus()
                                    imm.showSoftInput(input!!, 0)
                                }, 200)
                            }
                        }
                    }
                }
                R.id.shortcuts -> {
                    lifecycleScope.launch {
                        if (shortcutCount > 0){
                            UiUtil.showShortcuts(requireActivity(), commandTemplateViewModel,
                                itemSelected = {sh ->
                                    input!!.setText("${input!!.text} $sh")
                                },
                                itemRemoved = {removed ->
                                    input!!.setText(input!!.text.replace("(${Regex.escape(removed)})(?!.*\\1)".toRegex(), "").trimEnd())
                                    input!!.setSelection(input!!.text.length)
                                })
                        }
                    }
                }
                R.id.folder -> {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    commandPathResultLauncher.launch(intent)
                }

            }
            true
        }

        output = view.findViewById(R.id.custom_command_output)
        output!!.setTextIsSelectable(true)
        output!!.layoutParams!!.width = LayoutParams.WRAP_CONTENT
        input!!.requestFocus()
        fab!!.setOnClickListener {
            if (fab!!.text == requireActivity().getString(R.string.run_command)){
                input!!.visibility = View.GONE
                output!!.text = "${output!!.text}\n~ $ ${input!!.text}\n"
                showCancelFab()
                imm.hideSoftInputFromWindow(input?.windowToken, 0)
                lifecycleScope.launch {
                    val command = input!!.text.toString().replaceFirst("yt-dlp", "")
                    downloadID = withContext(Dispatchers.IO){
                        terminalViewModel.insert(TerminalItem(command = command, log = output!!.text.toString()))
                    }
                    terminalViewModel.startTerminalDownloadWorker(TerminalItem(downloadID, command))
                    input!!.visibility = View.GONE
                    showCancelFab()
                    runWorkerListener()
                }
            }else {
                terminalViewModel.cancelTerminalDownload(downloadID)
                input!!.visibility = View.VISIBLE
                hideCancelFab()
            }
        }
        notificationUtil = NotificationUtil(requireContext())
        initMenu()

        input?.enableTextHighlight()

        input?.append(bundle?.getString("input") ?: "")
        input!!.requestFocus()
        input!!.setSelection(input!!.text.length)
        output?.text = bundle?.getString("output") ?: output?.text
        if (bundle?.getBoolean("run") == true){
            showCancelFab()
        }
        runWorkerListener()
    }

    private fun initMenu() {
        topAppBar?.menu?.get(0)?.isVisible = false
        topAppBar?.menu?.get(1)?.isVisible = true
        topAppBar?.menu?.get(2)?.isVisible = true
        topAppBar?.menu?.get(3)?.isVisible = true
        val slider = requireActivity().findViewById<Slider>(R.id.textsize_seekbar)
        topAppBar?.setOnMenuItemClickListener { m: MenuItem ->
            when(m.itemId){
                R.id.wrap -> {
                    var scrollView = requireView().findViewById<HorizontalScrollView>(R.id.horizontalscroll_output)
                    if(scrollView != null){
                        val parent = (scrollView.parent as ViewGroup)
                        scrollView.removeAllViews()
                        parent.removeView(scrollView)
                        parent.addView(output, 0)
                    }else{
                        val parent = output?.parent as ViewGroup
                        parent.removeView(output)
                        scrollView = HorizontalScrollView(requireContext())
                        scrollView.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scrollView.addView(output)
                        scrollView.id = R.id.horizontalscroll_output
                        parent.addView(scrollView, 0)
                    }
                }
                R.id.export_clipboard -> {
                    lifecycleScope.launch(Dispatchers.IO){
                        val clipboard: ClipboardManager = requireActivity().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setText(output?.text)
                    }
                }

                R.id.text_size -> {
                    slider?.isVisible = !slider.isVisible
                }
            }
            true
        }

        slider?.apply {
            this.valueFrom = 0f
            this.valueTo = 10f
            this.value = sharedPreferences.getFloat("terminal_zoom", 2f)
            output?.setCustomTextSize(this.value + 13f)
            input?.setCustomTextSize(this.value + 13f)
            this.addOnChangeListener { slider, value, fromUser ->
                output?.setCustomTextSize(value + 13f)
                input?.setCustomTextSize(value + 13f)
                sharedPreferences.edit(true){
                    putFloat("terminal_zoom", value)
                }
            }
        }
    }
    private fun hideCancelFab() {
        kotlin.runCatching {
            fab!!.text = getString(R.string.run_command)
            fab!!.setIconResource(R.drawable.ic_baseline_keyboard_arrow_right_24)
        }
    }
    private fun showCancelFab() {
        kotlin.runCatching {
            fab!!.text = getString(R.string.cancel_task)
            fab!!.setIconResource(R.drawable.ic_cancel)
        }
    }

    private var commandPathResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                requireActivity().contentResolver?.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            input!!.text.insert(input!!.selectionStart, FileUtil.formatPath(result.data?.data.toString()))
        }
    }

    private fun runWorkerListener(){
        CoroutineScope(Dispatchers.IO).launch {
            terminalViewModel.getTerminal(downloadID).collectLatest {
                kotlin.runCatching {
                    requireActivity().runOnUiThread{
                        if (it != null){
                            if (!it.log.isNullOrBlank()) {
                                output?.text = it.log
                            }
                            output?.scrollTo(0, output!!.height)
                            scrollView?.fullScroll(View.FOCUS_DOWN)
                            input!!.visibility = View.GONE
                            showCancelFab()
                        }
                    }
                }

            }
        }

        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(downloadID.toString())
            .removeObserver(workerObserver)

        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(downloadID.toString())
            .observe(viewLifecycleOwner, workerObserver)
    }

    private val workerObserver = object: Observer<MutableList<WorkInfo>> {
        override fun onChanged(value: MutableList<WorkInfo>) {
            value.forEach { work ->
                if (listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED).contains(work.state)) {
                    requireActivity().runOnUiThread {
                        kotlin.runCatching {
                            input!!.setText("yt-dlp ")
                            input!!.visibility = View.VISIBLE
                            input!!.requestFocus()
                            input!!.setSelection(input!!.text.length)
                            hideCancelFab()
                        }
                    }
                    return@forEach
                }
            }
        }

    }



}