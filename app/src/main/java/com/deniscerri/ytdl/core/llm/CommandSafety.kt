package com.deniscerri.ytdl.core.llm

/** Checks a model-suggested (untrusted) command before it is written into the terminal. */
object CommandSafety {

    enum class Risk {
        NOT_YTDLP,
        SHELL_OPERATORS,
        RUNS_PROGRAMS,
        UPDATES_YTDLP,
        CONTAINS_CREDENTIALS,
        UNTERMINATED_QUOTE,
    }

    data class Analysis(
        val risks: Set<Risk>,
        val blockedReason: BlockedReason?,
    ) {
        val canWrite: Boolean get() = blockedReason == null
    }

    enum class BlockedReason { EMPTY, MULTIPLE_LINES, CONTROL_CHARACTERS }

    private val RUNS_PROGRAMS_OPTIONS = setOf(
        "--exec", "--exec-before-download", "--netrc-cmd", "--plugin-dirs",
        "--config-location", "--config-locations",
        // raw arguments for ffmpeg/aria2c and other external tools
        "--postprocessor-args", "--ppa", "--downloader-args", "--external-downloader-args",
    )
    private val UPDATE_OPTIONS = setOf("-U", "--update", "--update-to")
    private val CREDENTIAL_OPTIONS = setOf(
        "-u", "--username", "-p", "--password", "--video-password",
        "--ap-username", "--ap-password", "-2", "--twofactor", "--client-certificate-password",
    )

    /** Joins line continuations and strips invisible/control characters; returns whether any were removed. */
    fun clean(raw: String): Pair<String, Boolean> {
        var text = raw.trim()
            .replace(Regex("""\\\r?\n\s*"""), " ")
            .replace(Regex("""\s*\r?\n\s*"""), " ")
            .removePrefix("$ ")
        val stripped = text.filterNot { isUnsafeChar(it) }
        val removed = stripped.length != text.length
        text = stripped.replace(Regex(" {2,}"), " ").trim()
        return text to removed
    }

    fun analyze(command: String): Analysis {
        val blocked = when {
            command.isBlank() -> BlockedReason.EMPTY
            command.any { it == '\n' || it == '\r' } -> BlockedReason.MULTIPLE_LINES
            command.any { isUnsafeChar(it) } -> BlockedReason.CONTROL_CHARACTERS
            else -> null
        }
        val risks = mutableSetOf<Risk>()
        val scan = scanShell(command)
        val words = scan.words
        if (words.firstOrNull() != "yt-dlp") risks.add(Risk.NOT_YTDLP)
        if (scan.hasOperators) risks.add(Risk.SHELL_OPERATORS)
        if (scan.unterminatedQuote) risks.add(Risk.UNTERMINATED_QUOTE)
        words.drop(1).forEach { word ->
            val option = word.substringBefore('=')
            when {
                option in RUNS_PROGRAMS_OPTIONS -> risks.add(Risk.RUNS_PROGRAMS)
                option in UPDATE_OPTIONS -> risks.add(Risk.UPDATES_YTDLP)
                option in CREDENTIAL_OPTIONS -> risks.add(Risk.CONTAINS_CREDENTIALS)
            }
        }
        return Analysis(risks, blocked)
    }

    /** C0/C1 controls (a newline or ESC typed into a terminal acts as a key press) and bidi overrides. */
    private fun isUnsafeChar(c: Char): Boolean =
        c < ' ' || c in '\u007f'..'\u009f' || c in '‪'..'‮' || c in '⁦'..'⁩' ||
            c == '‎' || c == '‏' || c == '؜'

    private class ShellScan(val words: List<String>, val hasOperators: Boolean, val unterminatedQuote: Boolean)

    /** A small POSIX-shell lexer: enough to find words and unquoted operators. */
    private fun scanShell(command: String): ShellScan {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        var inWord = false
        var operators = false
        var quote: Char? = null
        var i = 0
        fun endWord() {
            if (inWord) words.add(current.toString())
            current.clear()
            inWord = false
        }
        while (i < command.length) {
            val c = command[i]
            when {
                quote == '\'' -> if (c == '\'') quote = null else current.append(c)
                quote == '"' -> when {
                    c == '"' -> quote = null
                    c == '\\' && i + 1 < command.length -> { current.append(command[i + 1]); i++ }
                    c == '`' || (c == '$' && command.getOrNull(i + 1) == '(') -> { operators = true; current.append(c) }
                    else -> current.append(c)
                }
                c == '\\' && i + 1 < command.length -> { current.append(command[i + 1]); inWord = true; i++ }
                c == '\'' || c == '"' -> { quote = c; inWord = true }
                c.isWhitespace() -> endWord()
                c in ";&|<>`" || (c == '$' && command.getOrNull(i + 1) == '(') -> { operators = true; endWord() }
                else -> { current.append(c); inWord = true }
            }
            i++
        }
        endWord()
        return ShellScan(words, operators, quote != null)
    }
}
