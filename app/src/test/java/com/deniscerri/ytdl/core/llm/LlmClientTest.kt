package com.deniscerri.ytdl.core.llm

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LlmClientTest {

    private class FakeServer(vararg responses: Pair<Int, String>) : Interceptor {
        private val queue = ArrayDeque(responses.toList())
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            bodies += request.body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }.orEmpty()
            val (code, body) = queue.removeFirst()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fake")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }

        fun client() = OkHttpClient.Builder().addInterceptor(this).build()
        fun jsonBody(index: Int): JsonObject = Json.parseToJsonElement(bodies[index]).jsonObject
    }

    private val request = LlmRequest(
        system = "system prompt with JSON",
        messages = listOf(LlmMessage(LlmMessage.Role.USER, "hello")),
        jsonMode = true,
        maxOutputTokens = 1000,
    )

    private fun config(preset: LlmProviderPreset, baseUrl: String = preset.baseUrl, model: String = "test-model") =
        LlmProviderConfig(
            id = "id",
            name = preset.displayName,
            preset = preset.name,
            protocol = preset.protocol,
            baseUrl = baseUrl,
            model = model,
            jsonMode = preset.supportsJsonMode,
        )

    private val openAiReply = """{"model":"test-model","choices":[{"message":{"role":"assistant","content":"{\"command\":\"yt-dlp x\"}"},"finish_reason":"stop"}]}"""

    @Test
    fun openAiCompatibleRequestShape() = runBlocking {
        val server = FakeServer(200 to openAiReply)
        val client = LlmClients.create(config(LlmProviderPreset.DEEPSEEK), "sk-test-key-123456", server.client())

        val completion = client.complete(request)

        assertEquals("{\"command\":\"yt-dlp x\"}", completion.text)
        assertFalse(completion.truncated)
        val sent = server.requests.single()
        assertEquals("https://api.deepseek.com/chat/completions", sent.url.toString())
        assertEquals("Bearer sk-test-key-123456", sent.header("Authorization"))
        val body = server.jsonBody(0)
        assertEquals("test-model", body["model"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(1000, body["max_tokens"]!!.jsonPrimitive.int)
    }

    @Test
    fun openAiPresetUsesMaxCompletionTokens() = runBlocking {
        val server = FakeServer(200 to openAiReply)
        LlmClients.create(config(LlmProviderPreset.OPENAI), "sk-test-key-123456", server.client()).complete(request)

        val body = server.jsonBody(0)
        assertEquals(1000, body["max_completion_tokens"]!!.jsonPrimitive.int)
        assertNull(body["max_tokens"])
        assertEquals("https://api.openai.com/v1/chat/completions", server.requests.single().url.toString())
    }

    @Test
    fun badRequestIsRetriedWithoutOptionalFields() = runBlocking {
        val server = FakeServer(
            400 to """{"error":{"message":"response_format is not supported"}}""",
            200 to openAiReply,
        )
        LlmClients.create(config(LlmProviderPreset.GROQ), "gsk_test_key_123456", server.client()).complete(request)

        assertEquals(2, server.requests.size)
        assertTrue(server.jsonBody(0).containsKey("response_format"))
        assertFalse(server.jsonBody(1).containsKey("response_format"))
        assertFalse(server.jsonBody(1).containsKey("max_tokens"))
    }

    @Test
    fun httpErrorsMapToKindsAndNeverLeakTheKey() = runBlocking {
        val key = "sk-secret-key-abcdef123456"
        val cases = listOf(
            401 to LlmException.Kind.UNAUTHORIZED,
            402 to LlmException.Kind.INSUFFICIENT_BALANCE,
            429 to LlmException.Kind.RATE_LIMITED,
            404 to LlmException.Kind.NOT_FOUND,
            503 to LlmException.Kind.SERVER_ERROR,
        )
        for ((code, kind) in cases) {
            val server = FakeServer(code to """{"error":{"message":"bad key $key"}}""")
            try {
                LlmClients.create(config(LlmProviderPreset.DEEPSEEK), key, server.client()).complete(request)
                fail("expected $kind")
            } catch (e: LlmException) {
                assertEquals(kind, e.kind)
                assertEquals(code, e.httpCode)
                assertFalse(e.message!!.contains(key))
            }
        }
    }

    @Test
    fun redirectsAreNotFollowed() = runBlocking {
        assertFalse(LlmClients.defaultHttpClient.followRedirects)
        assertFalse(LlmClients.defaultHttpClient.followSslRedirects)
        val server = FakeServer(307 to "")
        try {
            LlmClients.create(config(LlmProviderPreset.CUSTOM_ANTHROPIC, "https://llm.example.com/v1"), "key-123456789", server.client()).complete(request)
            fail()
        } catch (e: LlmException) {
            assertEquals(LlmException.Kind.NOT_FOUND, e.kind)
        }
    }

    @Test
    fun oversizedResponseIsRejected() = runBlocking {
        val huge = "{\"choices\":[{\"message\":{\"content\":\"" + "x".repeat(HttpLlmClient.MAX_RESPONSE_BYTES.toInt()) + "\"}}]}"
        val server = FakeServer(200 to huge)
        try {
            LlmClients.create(config(LlmProviderPreset.DEEPSEEK), "sk-test-key-123456", server.client()).complete(request)
            fail()
        } catch (e: LlmException) {
            assertEquals(LlmException.Kind.INVALID_RESPONSE, e.kind)
        }
    }

    @Test
    fun truncatedEmptyReplyIsReported() = runBlocking {
        val server = FakeServer(200 to """{"choices":[{"message":{"content":"","reasoning_content":"thinking"},"finish_reason":"length"}]}""")
        try {
            LlmClients.create(config(LlmProviderPreset.DEEPSEEK), "sk-test-key-123456", server.client()).complete(request)
            fail()
        } catch (e: LlmException) {
            assertEquals(LlmException.Kind.EMPTY_RESPONSE, e.kind)
        }
    }

    @Test
    fun contentPartsAreJoined() = runBlocking {
        val server = FakeServer(200 to """{"choices":[{"message":{"content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]},"finish_reason":"stop"}]}""")
        val completion = LlmClients.create(config(LlmProviderPreset.MISTRAL), "key-123456789", server.client()).complete(request)
        assertEquals("ab", completion.text)
    }

    @Test
    fun anthropicRequestShape() = runBlocking {
        val server = FakeServer(200 to """{"model":"claude-x","content":[{"type":"text","text":"{\"command\":\"\"}"}],"stop_reason":"end_turn"}""")
        val completion = LlmClients.create(config(LlmProviderPreset.ANTHROPIC), "sk-ant-test-123456", server.client()).complete(request)

        assertEquals("{\"command\":\"\"}", completion.text)
        assertEquals("claude-x", completion.model)
        val sent = server.requests.single()
        assertEquals("https://api.anthropic.com/v1/messages", sent.url.toString())
        assertEquals("sk-ant-test-123456", sent.header("x-api-key"))
        assertEquals(AnthropicClient.ANTHROPIC_VERSION, sent.header("anthropic-version"))
        assertNull(sent.header("Authorization"))
        val body = server.jsonBody(0)
        assertEquals("system prompt with JSON", body["system"]!!.jsonPrimitive.content)
        assertEquals(1000, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(1, body["messages"]!!.jsonArray.size)
        assertFalse(body.containsKey("response_format"))
    }

    @Test
    fun listModelsSortsIds() = runBlocking {
        val server = FakeServer(200 to """{"object":"list","data":[{"id":"b-model"},{"id":"a-model"}]}""")
        val models = LlmClients.create(config(LlmProviderPreset.OPENROUTER), "key-123456789", server.client()).listModels()
        assertEquals(listOf("a-model", "b-model"), models)
        assertEquals("https://openrouter.ai/api/v1/models", server.requests.single().url.toString())
    }

    @Test
    fun anthropicListModelsAsksForAllModels() = runBlocking {
        val server = FakeServer(200 to """{"data":[{"id":"claude-b"},{"id":"claude-a"}],"has_more":false}""")
        val models = LlmClients.create(config(LlmProviderPreset.ANTHROPIC), "sk-ant-test-123456", server.client()).listModels()
        assertEquals(listOf("claude-a", "claude-b"), models)
        assertEquals("https://api.anthropic.com/v1/models?limit=1000", server.requests.single().url.toString())
    }

    @Test
    fun baseUrlValidation() {
        LlmClients.validateBaseUrl("https://api.example.com/v1/")
        LlmClients.validateBaseUrl("http://localhost:11434/v1")
        LlmClients.validateBaseUrl("http://127.0.0.1:8080")
        for (bad in listOf("http://192.168.1.10:11434/v1", "http://api.example.com", "not a url", "")) {
            try {
                LlmClients.validateBaseUrl(bad)
                fail("accepted $bad")
            } catch (e: LlmException) {
                assertTrue(e.kind == LlmException.Kind.INSECURE_URL || e.kind == LlmException.Kind.INVALID_URL)
            }
        }
    }

    @Test
    fun missingKeyIsRejectedOnlyWhenRequired() {
        try {
            LlmClients.create(config(LlmProviderPreset.DEEPSEEK), "  ")
            fail()
        } catch (e: LlmException) {
            assertEquals(LlmException.Kind.MISSING_API_KEY, e.kind)
        }
        // local servers usually need no key
        LlmClients.create(config(LlmProviderPreset.OLLAMA), null)
    }

    @Test
    fun ollamaSendsNoAuthorizationHeader() = runBlocking {
        val server = FakeServer(200 to openAiReply)
        LlmClients.create(config(LlmProviderPreset.OLLAMA), null, server.client()).complete(request)
        assertNull(server.requests.single().header("Authorization"))
        assertEquals("http://localhost:11434/v1/chat/completions", server.requests.single().url.toString())
    }
}
