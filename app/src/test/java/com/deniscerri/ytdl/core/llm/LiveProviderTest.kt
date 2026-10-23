package com.deniscerri.ytdl.core.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Talks to a real provider. Skipped unless YTDLNIS_LLM_KEY_FILE names a file holding an API key,
 * so the key never appears on a command line, in the repository or in Gradle output.
 *
 *   YTDLNIS_LLM_KEY_FILE=/path/to/key YTDLNIS_LLM_PRESET=DEEPSEEK [YTDLNIS_LLM_MODEL=...] \
 *     ./gradlew testGithubDebugUnitTest --tests '*LiveProviderTest*'
 */
class LiveProviderTest {
    private lateinit var config: LlmProviderConfig
    private lateinit var client: LlmClient

    @Before
    fun setUp() {
        val keyFile = System.getenv("YTDLNIS_LLM_KEY_FILE")?.let(::File)
        assumeTrue("YTDLNIS_LLM_KEY_FILE not set", keyFile?.isFile == true)
        val key = keyFile!!.readText().trim()
        val preset = LlmProviderPreset.fromName(System.getenv("YTDLNIS_LLM_PRESET") ?: LlmProviderPreset.DEEPSEEK.name)
        val baseConfig = LlmProviderConfig("live", preset.displayName, preset.name, preset.protocol, preset.baseUrl, model = "")
        val model = System.getenv("YTDLNIS_LLM_MODEL")?.takeIf { it.isNotBlank() }
            ?: runBlocking { LlmClients.create(baseConfig, key).listModels() }.let { models ->
                println("Models: $models")
                // prefer a fast general chat model when the provider lists several
                models.firstOrNull { "flash" in it || "chat" in it } ?: models.first()
            }
        println("Using ${preset.displayName} / $model")
        config = baseConfig.copy(model = model, jsonMode = preset.supportsJsonMode)
        client = LlmClients.create(config, key)
    }

    private fun ask(mode: AssistantMode, input: String, command: String? = null, output: String? = null): AssistantReply {
        val context = TerminalContext("~", "2026.08.19", listOf("ffmpeg", "python"), "English")
        val request = YtdlpAssistant.request(context, emptyList(), YtdlpAssistant.userMessage(mode, input, command, output), config)
        val completion = runBlocking { client.complete(request) }
        val reply = YtdlpAssistant.parseReply(completion)
        println("[$mode] command: ${reply.command}\nexplanation: ${reply.explanation}\nwarnings: ${reply.warnings}")
        return reply
    }

    @Test
    fun generatesASafeYtdlpCommand() {
        val reply = ask(AssistantMode.GENERATE, "Download only the audio of https://www.youtube.com/watch?v=dQw4w9WgXcQ as an mp3 file")
        val analysis = CommandSafety.analyze(reply.command)
        assertTrue(reply.command, reply.command.startsWith("yt-dlp "))
        assertTrue(reply.command, analysis.canWrite)
        assertTrue(analysis.risks.toString(), analysis.risks.isEmpty())
        assertTrue(reply.command, "-x" in reply.command || "--extract-audio" in reply.command)
        assertTrue(reply.explanation.isNotBlank())
    }

    @Test
    fun explainsACommand() {
        val reply = ask(AssistantMode.EXPLAIN, "yt-dlp -f \"bv*[height<=720]+ba/b[height<=720]\" --embed-subs URL")
        assertTrue(reply.explanation.contains("720"))
    }

    @Test
    fun troubleshootsAnError() {
        val reply = ask(
            AssistantMode.TROUBLESHOOT,
            "",
            output = """
                ~ $ yt-dlp -f 22 https://www.youtube.com/watch?v=dQw4w9WgXcQ
                [youtube] Extracting URL: https://www.youtube.com/watch?v=dQw4w9WgXcQ
                ERROR: [youtube] dQw4w9WgXcQ: Requested format is not available. Use --list-formats for a list of available formats
            """.trimIndent(),
        )
        assertTrue(reply.explanation.isNotBlank())
        assertTrue(reply.command.isEmpty() || reply.command.startsWith("yt-dlp"))
    }
}
