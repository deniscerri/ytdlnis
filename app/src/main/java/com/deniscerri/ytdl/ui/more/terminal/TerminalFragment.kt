package com.deniscerri.ytdl.ui.more.terminal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysConstants
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysInfo
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysListener
import com.deniscerri.ytdl.ui.more.terminal.virtualkeys.VirtualKeysView
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.slider.Slider
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.text.toInt


class TerminalFragment : Fragment() {
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var terminalViewModel: TerminalViewModel
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel

    private lateinit var topAppBar: MaterialToolbar
    private lateinit var bottomAppBar: BottomAppBar
    private lateinit var terminalView: TerminalView
    private lateinit var virtualKeysView: VirtualKeysView

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var session : TerminalSession
    private var sessionId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        terminalViewModel = ViewModelProvider(requireActivity())[TerminalViewModel::class.java]
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }

    override fun onResume() {
        arguments?.remove("new")
        arguments?.remove("share")
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        topAppBar = requireActivity().findViewById(R.id.custom_command_toolbar)
        topAppBar.setNavigationOnClickListener { requireActivity().finish() }

        terminalView = view.findViewById(R.id.terminalView)
        virtualKeysView = view.findViewById(R.id.virtualKeys)
        bottomAppBar = view.findViewById(R.id.bottomAppBar)

        if (arguments?.containsKey("id") == true) {
            sessionId = arguments?.getString("id")
        }

        terminalViewModel.setTerminalView(terminalView)
        terminalViewModel.setVirtualKeysView(virtualKeysView)


        var templateCount = 0
        var shortcutCount = 0

        lifecycleScope.launch {
            templateCount = withContext(Dispatchers.IO){
                commandTemplateViewModel.getTotalNumber()
            }
            if (templateCount == 0){
                bottomAppBar.menu.findItem(R.id.command_templates).icon?.alpha = 30
            }else{
                bottomAppBar.menu.findItem(R.id.command_templates).icon?.alpha = 255
            }

            shortcutCount = withContext(Dispatchers.IO){
                commandTemplateViewModel.getTotalShortcutNumber()
            }
            if (shortcutCount == 0) {
                bottomAppBar.menu.findItem(R.id.shortcuts).icon?.alpha = 30
            }else{
                bottomAppBar.menu.findItem(R.id.shortcuts).icon?.alpha = 255
            }

        }

        val slider = requireActivity().findViewById<Slider>(R.id.textsize_seekbar)
        slider?.apply {
            valueFrom = 10f
            valueTo = 37f
            value = sharedPreferences.getFloat("terminal_zoom", 35f)

            addOnChangeListener { _, value, _ ->
                terminalView.setTextSize(value.toInt())
                sharedPreferences.edit { putFloat("terminal_zoom", value) }
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
                                    session.write(" ${c.content} ")
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
                                    session.write(" $sh")
                                },
                                itemRemoved = {}
                            )
                        }
                    }
                }
                R.id.filename_template -> {
                    UiUtil.showFilenameTemplateDialog(requireActivity(), "") { filenameSelected ->
                        session.write(""" "$filenameSelected" """)
                    }
                }
                R.id.folder -> {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    commandPathResultLauncher.launch(intent)
                }
                R.id.text_size -> {
                    slider?.visibility = if (slider.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                }

            }
            true
        }

        notificationUtil = NotificationUtil(requireContext())
        initMenu()

        if (terminalViewModel.isBound && terminalViewModel.sessionBinder != null) {
            initSession()
        } else {
            // If not bound yet, wait for the service connection event
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    terminalViewModel.serviceConnectedEvent.collect {
                        initSession()
                    }
                }
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun initMenu() {
        topAppBar.menu?.forEach { it.isVisible = false }
        topAppBar.menu?.findItem(R.id.export_clipboard)?.isVisible = true
        topAppBar.menu?.findItem(R.id.add)?.isVisible = true
        topAppBar.menu?.findItem(R.id.delete)?.isVisible = true

        topAppBar.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.add -> {
                    findNavController().navigate(R.id.terminalFragment, bundleOf(Pair("new", true)),
                        NavOptions.Builder().setPopUpTo(R.id.terminalFragment, true).build())
                }
                R.id.exit -> {
                    sessionId?.apply {
                        terminalViewModel.sessionBinder?.terminateSession(this)
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
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
                    lifecycleScope.launch(Dispatchers.IO){
                        val clipboard: ClipboardManager = requireActivity().getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setText(session.emulator.screen.transcriptText)
                    }
                }
            }
            true
        }

    }

    private fun initSession() {
        val sessionBinder = terminalViewModel.sessionBinder ?: return
        val service = sessionBinder.getService()

        val activity = requireActivity() as TerminalActivity
        val client = TerminalBackEnd(terminalView, activity) {
            if (isAdded) {
                terminalViewModel.sessionBinder?.terminateSession(sessionId!!)
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        if (!sessionId.isNullOrBlank()) {
            terminalViewModel.changeSession(requireContext(), sessionBinder, sessionId!!)
        }

        val newSession = arguments?.getBoolean("new") ?: false
        val sessionShareURL = arguments?.getString("share")?.ifEmpty { null }

        val currentSession = sessionBinder.getSession(service.currentSession.value)
        session = if (newSession || currentSession == null) {
            sessionId = KeyShortcutHandler.generateUniqueSessionId(activity)
            sessionBinder.createSession(
                sessionId!!,
                client
            )
        } else {
            currentSession
        }

        session.updateTerminalSessionClient(client)

        terminalView.doOnLayout { view ->
            if (!isAdded) return@doOnLayout

            val termView = view as TerminalView

            termView.setTextSize(
                sharedPreferences.getFloat("terminal_zoom", 35f).toInt()
            )
            termView.setTypeface(TerminalUtils.typeface)

            termView.setTerminalViewClient(client)
            termView.attachSession(session)

            termView.requestFocus()

            val color = TerminalUtils.getViewColor(requireContext())
            val bgColor = TerminalUtils.getBackgroundColor(requireContext())
            termView.mEmulator?.mColors?.mCurrentColors?.apply {
                set(256, color)
                set(257, bgColor)
                set(258, color)
            }

            terminalViewModel.virtualKeysView?.apply {
                virtualKeysViewClient = terminalViewModel.terminalView?.mTermSession?.let {
                    VirtualKeysListener(
                        it
                    )
                }
                buttonTextColor = TerminalUtils.getViewColor(requireContext())
                reload(VirtualKeysInfo(virtualKeys, "", VirtualKeysConstants.CONTROL_CHARS_ALIASES))
            }

            sessionShareURL?.apply {
                CoroutineScope(Dispatchers.IO).launch {
                    delay(500)
                    withContext(Dispatchers.Main) {
                        session.write("yt-dlp \"$sessionShareURL\"")
                    }
                }
            }
        }
    }

    val virtualKeys = "[" +
            "\n  [\"ESC\", {\"key\": \"/\", \"popup\": \"\\\\\"}, {\"key\": \"-\", \"popup\": \"|\"}, \"HOME\", \"UP\", \"END\", \"PGUP\"]," +
            "\n  [\"TAB\", \"CTRL\", \"ALT\", \"LEFT\", \"DOWN\", \"RIGHT\", \"PGDN\"]" +
            "\n]"


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
            session.write(""" "${FileUtil.formatPath(result.data?.data.toString())}" """)
        }
    }
}