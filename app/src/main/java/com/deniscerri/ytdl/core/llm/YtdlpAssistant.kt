package com.deniscerri.ytdl.core.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What the terminal knows about itself, given to the model so its commands fit this app. */
data class TerminalContext(
    val workingDirectory: String?,
    val ytdlpVersion: String?,
    val availableTools: List<String>,
    /** English name of the app language, e.g. "German"; explanations are written in it. */
    val language: String,
)

enum class AssistantMode { GENERATE, EXPLAIN, TROUBLESHOOT }

data class AssistantReply(
    /** Suggested command, cleaned for the preview; empty when the model asks a question instead. */
    val command: String,
    val explanation: String,
    val warnings: List<String>,
    /** Hidden or control characters were removed from the model's command. */
    val removedCharacters: Boolean,
    val truncated: Boolean,
)

/** Builds requests for, and parses replies from, the yt-dlp command assistant. Provider-agnostic. */
object YtdlpAssistant {
    /** Terminal output sent for troubleshooting is cut to this many characters (newest kept). */
    const val MAX_OUTPUT_CHARS = 6000
    private const val MAX_HISTORY_MESSAGES = 8

    fun systemPrompt(context: TerminalContext): String = buildString {
        appendLine("You are the command assistant built into YTDLnis, an Android app that bundles yt-dlp.")
        appendLine("The user works in an interactive bash terminal on Android.")
        appendLine("- `yt-dlp` is a shell function that already passes --ffmpeg-location, the JavaScript runtimes and, when enabled in the app, --cookies. Never add these options yourself.")
        context.ytdlpVersion?.takeIf { it.isNotBlank() }?.let { appendLine("- yt-dlp version: $it") }
        if (context.availableTools.isNotEmpty()) appendLine("- Other commands available: ${context.availableTools.joinToString(", ")}")
        context.workingDirectory?.takeIf { it.isNotBlank() }?.let {
            appendLine("- Working directory, as shown in the prompt: $it (files are saved here unless -P or -o says otherwise)")
        }
        appendLine()
        appendLine("Rules for the command you suggest:")
        appendLine("- Exactly one command on a single line, starting with `yt-dlp`.")
        appendLine("- No pipes, redirections, `;`, `&&`, subshells, backticks or environment variables.")
        appendLine("- Quote URLs, output templates and format selectors with double quotes.")
        appendLine("- Prefer documented, stable options. Do not invent options.")
        appendLine("- Never include real credentials. If a login is needed, use placeholders such as <USERNAME> and say so in warnings.")
        appendLine("- Do not use --exec, --exec-before-download, --netrc-cmd, --plugin-dirs or -U/--update unless the user explicitly asks; if you do, explain the risk in warnings.")
        appendLine("- If the request is unclear or not about yt-dlp, leave command empty and ask one short question in explanation.")
        appendLine()
        appendLine("Answer with one JSON object and nothing else:")
        appendLine("""{"command": "<the command or empty>", "explanation": "<short, plain-language explanation>", "warnings": ["<caveats, may be empty>"]}""")
        append("Write explanation and warnings in ${context.language}. Keep the explanation brief; use short bullet points for option breakdowns.")
    }

    fun userMessage(mode: AssistantMode, input: String, command: String? = null, terminalOutput: String? = null): String {
        val text = SecretRedactor.redact(input.trim())
        return when (mode) {
            AssistantMode.GENERATE -> "Task: $text"
            AssistantMode.EXPLAIN ->
                "Explain what this command does, option by option. Put the command in \"command\" unchanged, " +
                    "or corrected if it has mistakes (say what you changed).\n\nCommand: $text"
            AssistantMode.TROUBLESHOOT -> buildString {
                appendLine("A yt-dlp command did not work as expected. Find the cause and suggest a corrected command.")
                if (text.isNotEmpty()) appendLine("User's note: $text")
                command?.takeIf { it.isNotBlank() }?.let { appendLine("Command: ${SecretRedactor.redact(it.trim())}") }
                terminalOutput?.takeIf { it.isNotBlank() }?.let {
                    appendLine("Recent terminal output (secrets redacted):")
                    appendLine("```")
                    appendLine(prepareTerminalOutput(it))
                    append("```")
                }
            }.trim()
        }
    }

    fun request(
        context: TerminalContext,
        history: List<LlmMessage>,
        userMessage: String,
        config: LlmProviderConfig,
    ): LlmRequest = LlmRequest(
        system = systemPrompt(context),
        messages = history.takeLast(MAX_HISTORY_MESSAGES) + LlmMessage(LlmMessage.Role.USER, userMessage),
        jsonMode = config.jsonMode,
        maxOutputTokens = config.maxOutputTokens,
    )

    /** Keeps the newest output, strips terminal escape sequences and redacts secrets. */
    fun prepareTerminalOutput(output: String): String {
        val plain = output
            .replace(Regex("""\u001B\[[0-?]*[ -/]*[@-~]"""), "")
            .replace(Regex("""\u001B][^\u0007\u001B]*(\u0007|\u001B\\)"""), "")
            .lines().joinToString("\n") { it.trimEnd() }
            .trim('\n')
        val tail = if (plain.length > MAX_OUTPUT_CHARS) "…" + plain.takeLast(MAX_OUTPUT_CHARS) else plain
        return SecretRedactor.redact(tail)
    }

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseReply(completion: LlmCompletion): AssistantReply {
        val text = completion.text
            .replace(Regex("""(?s)<think>.*?</think>"""), "")
            .trim()
        val parsed = extractJsonObject(text)
        val rawCommand: String
        val explanation: String
        val warnings: List<String>
        if (parsed != null) {
            rawCommand = parsed.stringField("command")
            explanation = parsed.stringField("explanation")
            warnings = when (val w = parsed["warnings"]) {
                is JsonArray -> w.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.filter { it.isNotEmpty() }
                is JsonPrimitive -> listOfNotNull(w.contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
                else -> emptyList()
            }
        } else {
            // Not JSON: keep the prose as the explanation and pick a yt-dlp line out of it if there is one.
            rawCommand = Regex("""(?m)^\s*(?:\$\s*)?(yt-dlp\s.*)$""").find(stripFences(text))?.groupValues?.get(1).orEmpty()
            explanation = text
            warnings = emptyList()
        }
        val (command, removed) = CommandSafety.clean(rawCommand)
        return AssistantReply(command, explanation.trim(), warnings, removed, completion.truncated)
    }

    private fun JsonObject.stringField(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun stripFences(text: String) = text.replace(Regex("""```[a-zA-Z]*"""), "")

    /** Finds the first parseable JSON object, tolerating code fences and prose around it. */
    private fun extractJsonObject(text: String): JsonObject? {
        val candidates = sequence {
            yield(text)
            yield(stripFences(text).trim())
            var start = text.indexOf('{')
            while (start >= 0) {
                val end = matchingBrace(text, start)
                if (end > start) yield(text.substring(start, end + 1))
                start = text.indexOf('{', start + 1)
            }
        }
        return candidates.firstNotNullOfOrNull { candidate ->
            runCatching { lenientJson.parseToJsonElement(candidate) as? JsonObject }.getOrNull()
                ?.takeIf { it.containsKey("command") || it.containsKey("explanation") }
        }
    }

    private fun matchingBrace(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> {}
                c == '{' -> depth++
                c == '}' -> if (--depth == 0) return i
            }
        }
        return -1
    }
}
