package com.deniscerri.ytdl.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtdlpAssistantTest {
    private fun reply(text: String, truncated: Boolean = false) =
        YtdlpAssistant.parseReply(LlmCompletion(text, "m", truncated))

    @Test
    fun parsesPlainJson() {
        val r = reply("""{"command":"yt-dlp -x \"URL\"","explanation":"Extracts audio","warnings":["needs ffmpeg"]}""")
        assertEquals("yt-dlp -x \"URL\"", r.command)
        assertEquals("Extracts audio", r.explanation)
        assertEquals(listOf("needs ffmpeg"), r.warnings)
        assertFalse(r.removedCharacters)
    }

    @Test
    fun parsesFencedJsonWithProseAndThinking() {
        val text = """
            <think>The user wants audio.</think>
            Sure! Here you go:
            ```json
            {"command": "yt-dlp -f ba URL", "explanation": "Best audio {only}", "warnings": "single warning"}
            ```
        """.trimIndent()
        val r = reply(text)
        assertEquals("yt-dlp -f ba URL", r.command)
        assertEquals("Best audio {only}", r.explanation)
        assertEquals(listOf("single warning"), r.warnings)
    }

    @Test
    fun fallsBackToProseAndExtractsCommandLine() {
        val r = reply("You can run:\n```\n$ yt-dlp --list-formats URL\n```\nThis lists formats.")
        assertEquals("yt-dlp --list-formats URL", r.command)
        assertTrue(r.explanation.contains("lists formats"))
    }

    @Test
    fun clarifyingQuestionHasNoCommand() {
        val r = reply("""{"command":"","explanation":"Which site?","warnings":[]}""")
        assertEquals("", r.command)
        assertEquals("Which site?", r.explanation)
    }

    @Test
    fun modelCommandIsCleaned() {
        val r = reply("{\"command\":\"yt-dlp \\\\\\n -x URL\\u001b[0m\",\"explanation\":\"x\"}", truncated = true)
        assertEquals("yt-dlp -x URL[0m", r.command)
        assertTrue(r.removedCharacters)
        assertTrue(r.truncated)
    }

    @Test
    fun userMessagesAreRedacted() {
        val generate = YtdlpAssistant.userMessage(AssistantMode.GENERATE, "login with -u bob -p hunter2 and download URL")
        assertFalse(generate.contains("hunter2"))

        val troubleshoot = YtdlpAssistant.userMessage(
            AssistantMode.TROUBLESHOOT,
            "",
            command = "yt-dlp --password secret123 URL",
            terminalOutput = "\u001b[01;32m~\u001b[00m $ yt-dlp URL\nERROR: Sign in to confirm\nAuthorization: Bearer abcdefghijklmnop",
        )
        assertFalse(troubleshoot.contains("secret123"))
        assertFalse(troubleshoot.contains("abcdefghijklmnop"))
        assertFalse(troubleshoot.contains("\u001b"))
        assertTrue(troubleshoot.contains("ERROR: Sign in to confirm"))
    }

    @Test
    fun terminalOutputKeepsNewestText() {
        val output = "old line\n" + "x".repeat(YtdlpAssistant.MAX_OUTPUT_CHARS) + "\nnewest line"
        val prepared = YtdlpAssistant.prepareTerminalOutput(output)
        assertTrue(prepared.endsWith("newest line"))
        assertFalse(prepared.contains("old line"))
        assertTrue(prepared.length <= YtdlpAssistant.MAX_OUTPUT_CHARS + 1)
    }

    @Test
    fun requestCarriesContextAndBoundedHistory() {
        val config = LlmProviderConfig("id", "DeepSeek", LlmProviderPreset.DEEPSEEK.name, LlmProtocol.OPENAI_COMPATIBLE, "https://api.deepseek.com", "m")
        val context = TerminalContext("/storage/emulated/0/Download", "2026.10.01", listOf("ffmpeg"), "German")
        val history = (1..20).map { LlmMessage(if (it % 2 == 1) LlmMessage.Role.USER else LlmMessage.Role.ASSISTANT, "m$it") }
        val request = YtdlpAssistant.request(context, history, "Task: x", config)

        assertTrue(request.system.contains("2026.10.01"))
        assertTrue(request.system.contains("/storage/emulated/0/Download"))
        assertTrue(request.system.contains("German"))
        assertTrue(request.system.contains("JSON"))
        assertEquals("Task: x", request.messages.last().content)
        assertTrue(request.messages.size <= 9)
        assertEquals(LlmMessage.Role.USER, request.messages.first().role)
        assertTrue(request.jsonMode)
    }
}
