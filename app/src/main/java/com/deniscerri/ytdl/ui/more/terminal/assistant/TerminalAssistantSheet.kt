package com.deniscerri.ytdl.ui.more.terminal.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.llm.AssistantMode
import com.deniscerri.ytdl.core.llm.CommandSafety
import com.deniscerri.ytdl.core.llm.LlmException
import com.deniscerri.ytdl.core.llm.TerminalContext
import com.deniscerri.ytdl.ui.more.settings.SettingsActivity
import com.deniscerri.ytdl.ui.more.settings.ai.LlmErrors
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Natural-language help for yt-dlp in the terminal. The model's suggestion lands in an editable
 * preview; nothing reaches the shell until the user taps Insert (typed at the prompt, not run)
 * or Run (typed and executed, after a confirmation when the command looks risky).
 */
class TerminalAssistantSheet : BottomSheetDialogFragment() {
    private lateinit var viewModel: TerminalAssistantViewModel

    private lateinit var providerChip: Chip
    private lateinit var modeGroup: MaterialButtonToggleGroup
    private lateinit var inputLayout: TextInputLayout
    private lateinit var input: TextInputEditText
    private lateinit var includeOutput: MaterialCheckBox
    private lateinit var privacyNote: TextView
    private lateinit var askButton: Button
    private lateinit var newConversation: Button
    private lateinit var progress: LinearProgressIndicator
    private lateinit var error: TextView
    private lateinit var result: View
    private lateinit var explanation: TextView
    private lateinit var warnings: TextView
    private lateinit var commandLayout: TextInputLayout
    private lateinit var command: TextInputEditText
    private lateinit var risks: TextView
    private lateinit var insertButton: Button
    private lateinit var runButton: Button

    private val currentLine: String? get() = arguments?.getString(ARG_CURRENT_LINE)?.trim()?.ifEmpty { null }
    private val terminalOutput: String? get() = arguments?.getString(ARG_OUTPUT)
    private val workingDirectory: String? get() = arguments?.getString(ARG_WORKING_DIRECTORY)

    private val mode: AssistantMode
        get() = when (modeGroup.checkedButtonId) {
            R.id.mode_explain -> AssistantMode.EXPLAIN
            R.id.mode_troubleshoot -> AssistantMode.TROUBLESHOOT
            else -> AssistantMode.GENERATE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // activity-scoped so the conversation survives closing and reopening the sheet
        viewModel = ViewModelProvider(requireActivity())[TerminalAssistantViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.terminal_assistant_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        providerChip = view.findViewById(R.id.provider_chip)
        modeGroup = view.findViewById(R.id.mode_group)
        inputLayout = view.findViewById(R.id.input_layout)
        input = view.findViewById(R.id.input)
        includeOutput = view.findViewById(R.id.include_output)
        privacyNote = view.findViewById(R.id.privacy_note)
        askButton = view.findViewById(R.id.ask)
        newConversation = view.findViewById(R.id.new_conversation)
        progress = view.findViewById(R.id.progress)
        error = view.findViewById(R.id.error)
        result = view.findViewById(R.id.result)
        explanation = view.findViewById(R.id.explanation)
        warnings = view.findViewById(R.id.warnings)
        commandLayout = view.findViewById(R.id.command_layout)
        command = view.findViewById(R.id.command)
        risks = view.findViewById(R.id.risks)
        insertButton = view.findViewById(R.id.insert)
        runButton = view.findViewById(R.id.run)

        val provider = viewModel.activeProvider()
        providerChip.text = provider?.let { "${it.name} · ${it.model}" } ?: getString(R.string.ai_manage_providers)
        providerChip.setOnClickListener {
            startActivity(
                Intent(requireContext(), SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_OPEN_PAGE, SettingsActivity.PAGE_AI_ASSISTANT)
            )
            dismiss()
        }
        privacyNote.text = getString(R.string.ai_privacy_note, provider?.name ?: "")

        if (savedInstanceState == null) modeGroup.check(R.id.mode_generate)
        modeGroup.addOnButtonCheckedListener { _, _, isChecked -> if (isChecked) onModeChanged() }
        onModeChanged()

        askButton.setOnClickListener { ask() }
        newConversation.setOnClickListener {
            viewModel.reset()
            input.setText("")
            command.setText("")
        }

        command.doAfterTextChanged { updateRisks() }
        commandLayout.setEndIconOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.ai_command_preview), command.text.toString()))
            Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
        insertButton.setOnClickListener { deliver(ACTION_INSERT) }
        runButton.setOnClickListener { confirmRun() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun onModeChanged() {
        inputLayout.hint = getString(
            when (mode) {
                AssistantMode.GENERATE -> R.string.ai_hint_generate
                AssistantMode.EXPLAIN -> R.string.ai_hint_explain
                AssistantMode.TROUBLESHOOT -> R.string.ai_hint_troubleshoot
            }
        )
        includeOutput.isVisible = mode == AssistantMode.TROUBLESHOOT && !terminalOutput.isNullOrBlank()
        if (mode == AssistantMode.EXPLAIN && input.text.isNullOrBlank()) {
            input.setText(currentLine ?: command.text?.toString().orEmpty())
        }
    }

    private fun ask() {
        val text = input.text?.toString().orEmpty()
        if (text.isBlank() && mode != AssistantMode.TROUBLESHOOT) {
            inputLayout.error = getString(R.string.ai_required)
            return
        }
        inputLayout.error = null
        val output = if (mode == AssistantMode.TROUBLESHOOT && includeOutput.isChecked) terminalOutput else null
        val troubleshootCommand = if (mode == AssistantMode.TROUBLESHOOT) currentLine else null
        viewModel.ask(mode, text, troubleshootCommand, output, terminalContext())
    }

    private fun terminalContext(): TerminalContext {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val locale = resources.configuration.locales.get(0) ?: Locale.getDefault()
        return TerminalContext(
            workingDirectory = workingDirectory,
            ytdlpVersion = prefs.getString("ytdl-version", null),
            availableTools = listOf("ffmpeg", "python"),
            language = locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" },
        )
    }

    private fun render(state: TerminalAssistantViewModel.State) {
        progress.isVisible = state is TerminalAssistantViewModel.State.Loading
        askButton.isEnabled = state !is TerminalAssistantViewModel.State.Loading
        newConversation.isVisible = viewModel.hasHistory
        error.isVisible = state is TerminalAssistantViewModel.State.Failure
        when (state) {
            is TerminalAssistantViewModel.State.Failure -> {
                val e = state.error
                error.text = if (e is LlmException) LlmErrors.describe(requireContext(), e) else (e.message ?: e.javaClass.simpleName)
            }
            is TerminalAssistantViewModel.State.Success -> {
                val reply = state.reply
                result.isVisible = true
                Markwon.create(requireContext()).setMarkdown(explanation, reply.explanation)
                val notes = buildList {
                    addAll(reply.warnings)
                    if (reply.removedCharacters) add(getString(R.string.ai_removed_characters))
                    if (reply.truncated) add(getString(R.string.ai_truncated))
                }
                warnings.isVisible = notes.isNotEmpty()
                warnings.text = notes.joinToString("\n") { "⚠ $it" }
                // keep the user's edits when the same reply is rendered again (e.g. after rotation)
                if (command.tag !== reply) {
                    command.tag = reply
                    command.setText(reply.command)
                }
                commandLayout.isVisible = reply.command.isNotEmpty() || !command.text.isNullOrEmpty()
                updateRisks()
            }
            TerminalAssistantViewModel.State.Idle -> {
                result.isVisible = false
                command.tag = null
            }
            TerminalAssistantViewModel.State.Loading -> {}
        }
    }

    private fun updateRisks() {
        val text = command.text?.toString().orEmpty()
        val analysis = CommandSafety.analyze(text)
        val lines = buildList {
            when (analysis.blockedReason) {
                CommandSafety.BlockedReason.MULTIPLE_LINES -> add(getString(R.string.ai_blocked_multiline))
                CommandSafety.BlockedReason.CONTROL_CHARACTERS -> add(getString(R.string.ai_blocked_control))
                CommandSafety.BlockedReason.EMPTY, null -> {}
            }
            analysis.risks.forEach { add(riskText(it)) }
        }
        risks.isVisible = lines.isNotEmpty()
        risks.text = lines.joinToString("\n") { "• $it" }
        insertButton.isEnabled = analysis.canWrite
        runButton.isEnabled = analysis.canWrite
    }

    private fun riskText(risk: CommandSafety.Risk) = getString(
        when (risk) {
            CommandSafety.Risk.NOT_YTDLP -> R.string.ai_risk_not_ytdlp
            CommandSafety.Risk.SHELL_OPERATORS -> R.string.ai_risk_shell_operators
            CommandSafety.Risk.RUNS_PROGRAMS -> R.string.ai_risk_runs_programs
            CommandSafety.Risk.UPDATES_YTDLP -> R.string.ai_risk_updates
            CommandSafety.Risk.CONTAINS_CREDENTIALS -> R.string.ai_risk_credentials
            CommandSafety.Risk.UNTERMINATED_QUOTE -> R.string.ai_risk_unterminated_quote
        }
    )

    private fun confirmRun() {
        val text = command.text?.toString().orEmpty().trim()
        val analysis = CommandSafety.analyze(text)
        if (!analysis.canWrite) return
        if (analysis.risks.isEmpty()) {
            deliver(ACTION_RUN)
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ai_run_confirm_title)
            .setMessage(text + "\n\n" + analysis.risks.joinToString("\n") { "• ${riskText(it)}" })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ai_run_anyway) { _, _ -> deliver(ACTION_RUN) }
            .show()
    }

    private fun deliver(action: String) {
        val text = command.text?.toString().orEmpty().trim()
        if (!CommandSafety.analyze(text).canWrite) return
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_ACTION to action, RESULT_COMMAND to text))
        dismiss()
    }

    companion object {
        const val TAG = "TerminalAssistantSheet"
        const val REQUEST_KEY = "terminal_assistant"
        const val RESULT_ACTION = "action"
        const val RESULT_COMMAND = "command"
        const val ACTION_INSERT = "insert"
        const val ACTION_RUN = "run"

        private const val ARG_CURRENT_LINE = "current_line"
        private const val ARG_OUTPUT = "terminal_output"
        private const val ARG_WORKING_DIRECTORY = "working_directory"

        fun newInstance(currentLine: String?, terminalOutput: String?, workingDirectory: String?) =
            TerminalAssistantSheet().apply {
                arguments = bundleOf(
                    ARG_CURRENT_LINE to currentLine,
                    ARG_OUTPUT to terminalOutput,
                    ARG_WORKING_DIRECTORY to workingDirectory,
                )
            }
    }
}
