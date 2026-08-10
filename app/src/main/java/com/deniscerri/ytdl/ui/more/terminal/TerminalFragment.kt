package com.deniscerri.ytdl.ui.more.terminal

import android.annotation.SuppressLint
import android.app.ActionBar.LayoutParams
import android.app.Activity
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
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
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.TerminalItem
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdl.util.Extensions.enableTextHighlight
import com.deniscerri.ytdl.util.Extensions.setCustomTextSize
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.termux.shared.terminal.io.extrakeys.ExtraKeysInfo
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates


class TerminalFragment : Fragment() {
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var terminalViewModel: TerminalViewModel
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel

    private lateinit var topAppBar: MaterialToolbar
    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var terminalView: TerminalView
    private lateinit var extraKeysView: ExtraKeysView

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var imm : InputMethodManager
    private lateinit var metrics: DisplayMetrics

    private var terminalService: TerminalService? = null
    private var activeSession: TerminalSession? = null
    private var downloadID: Long = 0L
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TerminalService.LocalBinder
            terminalService = binder.getService()
            isBound = true
            attachOrCreateSession()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            terminalService = null
            isBound = false
        }
    }

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
        outState.putLong("downloadID", downloadID)
    }

    override fun onResume() {
        arguments?.remove("id")
        arguments?.remove("share")
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        topAppBar = requireActivity().findViewById(R.id.custom_command_toolbar)
        topAppBar.setNavigationOnClickListener { requireActivity().finish() }

        terminalView = view.findViewById(R.id.terminalView)
        extraKeysView = view.findViewById(R.id.extra_keys)
        bottomAppBar = view.findViewById(R.id.bottomAppBar)
        downloadID = arguments?.getLong("id") ?: 0L

        var bundle = savedInstanceState
        if (arguments?.containsKey("share") == true){
            if (bundle == null){
                bundle = Bundle()
            }
            bundle.putString("input", arguments?.getString("share"))
        }

        initMenu()

        val serviceIntent = Intent(requireContext(), TerminalService::class.java)
        requireContext().startService(serviceIntent)
        requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)


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
                bottomAppBar.menu[0].icon?.alpha = 30
            }else{
                bottomAppBar.menu[0].icon?.alpha = 255
            }

            shortcutCount = withContext(Dispatchers.IO){
                commandTemplateViewModel.getTotalShortcutNumber()
            }
            if (shortcutCount == 0) {
                bottomAppBar.menu[1].icon?.alpha = 30
            }else{
                bottomAppBar.menu[1].icon?.alpha = 255
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
                                    activeSession?.write(c.content + " ")
                                    terminalView.requestFocus()
                                }
                            }
                        }
                    }
                }
                R.id.shortcuts -> {
                    lifecycleScope.launch {
                        if (shortcutCount > 0){
                            UiUtil.showShortcuts(requireActivity(), commandTemplateViewModel,
                                itemSelected = {sh ->
                                    activeSession?.write(sh)
                                },
                                itemRemoved = { removed ->
//                                    input.setText(input.text.replace("(${Regex.escape(removed)})(?!.*\\1)".toRegex(), "").trim())
//                                    input.setSelection(input.text.length)
                                })
                        }
                    }
                }
                R.id.filename_template -> {
                    UiUtil.showFilenameTemplateDialog(requireActivity(), "") { filenameSelected ->
                        activeSession?.write(" -o $filenameSelected")
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

        notificationUtil = NotificationUtil(requireContext())
        initMenu()
    }

    private fun attachOrCreateSession() {
        lifecycleScope.launch {
            var session: TerminalSession? = TerminalSessionManager.getSession(downloadID)

            if (session == null || !session.isRunning) {
                val initialCommand = arguments?.getString("share") ?: ""

                if (downloadID == 0L) {
                    downloadID = withContext(Dispatchers.IO) {
                        terminalViewModel.insert(TerminalItem(command = initialCommand, log = ""))
                    }
                }

                session = terminalService?.startNewSession(downloadID, initialCommand)
            }

            session?.let {
                activeSession = it
                terminalView.setTerminalViewClient(createTerminalViewClient())
                val zoomLevel = sharedPreferences.getFloat("terminal_zoom", 14f)
                terminalView.setTextSize(zoomLevel.toInt())
                terminalView.attachSession(it)
                terminalView.requestFocus()

                it.write(arguments?.getString("input") ?: "yt-dlp")
            }
        }
    }

    private fun createTerminalViewClient(): com.termux.view.TerminalViewClient {
        return object : com.termux.view.TerminalViewClient {
            override fun onScale(scale: Float): Float = scale
            override fun onSingleTapUp(e: android.view.MotionEvent) {
                terminalView.requestFocus()
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }

            override fun shouldBackButtonBeMappedToEscape(): Boolean {
                return false
            }

            override fun shouldEnforceCharBasedInput(): Boolean {
                return true
            }

            override fun shouldUseCtrlSpaceWorkaround(): Boolean {
                return true
            }

            override fun isTerminalViewSelected(): Boolean {
                return true
            }

            override fun copyModeChanged(copyMode: Boolean) {

            }

            override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent, session: TerminalSession): Boolean = false
            override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent): Boolean = false
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean = false
            override fun readShiftKey(): Boolean {
                TODO("Not yet implemented")
            }

            override fun readFnKey(): Boolean {
                TODO("Not yet implemented")
            }

            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
            override fun onLongPress(event: android.view.MotionEvent): Boolean = false
            override fun onEmulatorSet() {}
            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
    }

    @SuppressLint("UseKtx")
    private fun initMenu() {
        topAppBar.menu?.findItem(R.id.wrap)?.isVisible = false
        topAppBar.menu?.findItem(R.id.export_clipboard)?.isVisible = true
        topAppBar.menu?.findItem(R.id.text_size)?.isVisible = true

        val slider = requireActivity().findViewById<Slider>(R.id.textsize_seekbar)
        topAppBar.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.wrap -> {
//                    var scrollView = requireView().findViewById<HorizontalScrollView>(R.id.horizontalscroll_output)
//                    if(scrollView != null){
//                        val parent = (scrollView.parent as ViewGroup)
//                        scrollView.removeAllViews()
//                        parent.removeView(scrollView)
//                        parent.addView(output, 0)
//                        sharedPreferences.edit().putBoolean("wrap_text_terminal", true).apply()
//                    }else{
//                        val parent = output.parent as ViewGroup
//                        parent.removeView(output)
//                        scrollView = HorizontalScrollView(requireContext())
//                        scrollView.layoutParams = LinearLayout.LayoutParams(
//                            ViewGroup.LayoutParams.MATCH_PARENT,
//                            ViewGroup.LayoutParams.MATCH_PARENT
//                        )
//                        scrollView.addView(output)
//                        scrollView.id = R.id.horizontalscroll_output
//                        parent.addView(scrollView, 0)
//                        sharedPreferences.edit().putBoolean("wrap_text_terminal", false).apply()
//                    }
                }
                R.id.export_clipboard -> {
//                    lifecycleScope.launch(Dispatchers.IO){
//                        val clipboard: ClipboardManager = requireActivity().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
//                        clipboard.setText(output.text)
//                    }
                }
                R.id.text_size -> {
                    slider?.visibility = if (slider.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }
            }
            true
        }

        slider?.apply {
            valueFrom = 10f
            valueTo = 30f
            value = sharedPreferences.getFloat("terminal_zoom", 14f).coerceIn(10f, 30f)

            addOnChangeListener { _, value, _ ->
                terminalView.setTextSize(value.toInt())
                sharedPreferences.edit { putFloat("terminal_zoom", value) }
            }
        }

        sharedPreferences.getBoolean("wrap_text_terminal", false).apply {
            if (this){
                bottomAppBar.menu.performIdentifierAction(R.id.wrap, 0)
            }
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
            activeSession?.write(FileUtil.formatPath(result.data?.data.toString()))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }
    }
}