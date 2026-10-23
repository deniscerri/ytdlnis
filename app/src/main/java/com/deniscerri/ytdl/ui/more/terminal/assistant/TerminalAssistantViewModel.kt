package com.deniscerri.ytdl.ui.more.terminal.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deniscerri.ytdl.core.llm.AssistantMode
import com.deniscerri.ytdl.core.llm.AssistantReply
import com.deniscerri.ytdl.core.llm.LlmException
import com.deniscerri.ytdl.core.llm.LlmMessage
import com.deniscerri.ytdl.core.llm.LlmProviderConfig
import com.deniscerri.ytdl.core.llm.LlmSettings
import com.deniscerri.ytdl.core.llm.TerminalContext
import com.deniscerri.ytdl.core.llm.YtdlpAssistant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalAssistantViewModel(application: Application) : AndroidViewModel(application) {
    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Success(val reply: AssistantReply) : State
        data class Failure(val error: Exception) : State
    }

    val settings = LlmSettings(application)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** Earlier turns of this conversation, so follow-ups like "now only audio" work. */
    private val history = mutableListOf<LlmMessage>()
    val hasHistory: Boolean get() = history.isNotEmpty()

    private var job: Job? = null

    fun activeProvider(): LlmProviderConfig? = settings.activeProvider()

    fun ask(mode: AssistantMode, input: String, command: String?, terminalOutput: String?, context: TerminalContext) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = State.Loading
            try {
                val config = settings.activeProvider()
                    ?: throw LlmException(LlmException.Kind.MISSING_MODEL, "No provider configured")
                val userMessage = YtdlpAssistant.userMessage(mode, input, command, terminalOutput)
                val request = YtdlpAssistant.request(context, history, userMessage, config)
                val completion = withContext(Dispatchers.IO) { settings.client(config).complete(request) }
                val reply = YtdlpAssistant.parseReply(completion)
                history += LlmMessage(LlmMessage.Role.USER, userMessage)
                history += LlmMessage(LlmMessage.Role.ASSISTANT, completion.text)
                _state.value = State.Success(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = State.Failure(e)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        if (_state.value == State.Loading) _state.value = State.Idle
    }

    fun reset() {
        cancel()
        history.clear()
        _state.value = State.Idle
    }
}
