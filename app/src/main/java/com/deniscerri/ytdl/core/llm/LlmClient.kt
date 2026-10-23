package com.deniscerri.ytdl.core.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

data class LlmMessage(val role: Role, val content: String) {
    enum class Role { USER, ASSISTANT }
}

data class LlmRequest(
    val system: String,
    val messages: List<LlmMessage>,
    val jsonMode: Boolean,
    val maxOutputTokens: Int,
)

data class LlmCompletion(
    val text: String,
    val model: String?,
    /** The provider stopped because it hit the output token limit. */
    val truncated: Boolean,
)

class LlmException(
    val kind: Kind,
    message: String,
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        MISSING_API_KEY, INVALID_URL, INSECURE_URL, MISSING_MODEL,
        UNAUTHORIZED, INSUFFICIENT_BALANCE, RATE_LIMITED, BAD_REQUEST, NOT_FOUND, SERVER_ERROR,
        NETWORK, TIMEOUT, EMPTY_RESPONSE, INVALID_RESPONSE,
    }
}

interface LlmClient {
    suspend fun complete(request: LlmRequest): LlmCompletion
    suspend fun listModels(): List<String>
}

object LlmClients {
    val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // reasoning models can think for a while before the first byte arrives
            .readTimeout(150, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            // APIs don't redirect, and following one could hand the key to another host
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    fun create(config: LlmProviderConfig, apiKey: String?, http: OkHttpClient = defaultHttpClient): LlmClient {
        val key = apiKey?.trim().orEmpty()
        if (key.isEmpty() && config.presetValue.requiresApiKey) {
            throw LlmException(LlmException.Kind.MISSING_API_KEY, "No API key saved for ${config.name}")
        }
        val baseUrl = validateBaseUrl(config.baseUrl)
        return when (config.protocol) {
            LlmProtocol.OPENAI_COMPATIBLE -> OpenAiCompatibleClient(config, key, baseUrl, http)
            LlmProtocol.ANTHROPIC -> AnthropicClient(config, key, baseUrl, http)
        }
    }

    /** HTTPS only; plain HTTP is accepted for loopback hosts (a model server running on the phone). */
    fun validateBaseUrl(raw: String): HttpUrl {
        val url = raw.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw LlmException(LlmException.Kind.INVALID_URL, "Invalid base URL: $raw")
        if (!url.isHttps && url.host !in LOOPBACK_HOSTS) {
            throw LlmException(LlmException.Kind.INSECURE_URL, "Base URL must use https:// (http:// is only allowed for localhost)")
        }
        return url
    }

    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "ip6-localhost")
}

internal abstract class HttpLlmClient(
    protected val config: LlmProviderConfig,
    protected val apiKey: String,
    private val baseUrl: HttpUrl,
    private val http: OkHttpClient,
) : LlmClient {
    protected val json = Json { ignoreUnknownKeys = true; isLenient = true }

    protected abstract fun Request.Builder.authorize(): Request.Builder

    internal fun endpoint(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = baseUrl.newBuilder()
        path.split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        query.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    protected suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonObject = execute(
        Request.Builder().url(endpoint(path, query)).get()
    )

    protected suspend fun post(path: String, body: JsonObject): JsonObject = execute(
        Request.Builder().url(endpoint(path)).post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
    )

    private suspend fun execute(builder: Request.Builder): JsonObject = withContext(Dispatchers.IO) {
        val request = builder.header("Accept", "application/json").authorize().build()
        val response = try {
            http.newCall(request).await()
        } catch (e: InterruptedIOException) {
            throw LlmException(LlmException.Kind.TIMEOUT, "The provider did not answer in time", cause = e)
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, "Network error: ${scrub(e.message ?: e.javaClass.simpleName)}", cause = e)
        }
        response.use {
            val text = try {
                // bounded: an oversized body fails to parse instead of exhausting memory
                it.peekBody(MAX_RESPONSE_BYTES).string()
            } catch (e: IOException) {
                throw LlmException(LlmException.Kind.NETWORK, "Network error: ${scrub(e.message ?: "")}", cause = e)
            }
            if (!it.isSuccessful) throw httpError(it.code, text)
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw LlmException(LlmException.Kind.INVALID_RESPONSE, "The provider returned a response that is not JSON", it.code, e)
            }
        }
    }

    private fun httpError(code: Int, body: String): LlmException {
        val detail = scrub(providerErrorMessage(body) ?: body.take(200)).take(300)
        val kind = when (code) {
            401, 403 -> LlmException.Kind.UNAUTHORIZED
            402 -> LlmException.Kind.INSUFFICIENT_BALANCE
            429 -> LlmException.Kind.RATE_LIMITED
            404, in 300..399 -> LlmException.Kind.NOT_FOUND
            in 400..499 -> LlmException.Kind.BAD_REQUEST
            else -> LlmException.Kind.SERVER_ERROR
        }
        return LlmException(kind, "HTTP $code${if (detail.isBlank()) "" else ": $detail"}", code)
    }

    private fun providerErrorMessage(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        when (val error = root["error"]) {
            is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> error.contentOrNull
            else -> null
        } ?: root["message"]?.jsonPrimitive?.contentOrNull
            ?: root["detail"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** Never let the key travel into an error message, even if a provider echoes it back. */
    protected fun scrub(text: String): String =
        if (apiKey.length >= 8) text.replace(apiKey, "<api-key>") else text

    protected fun requireModel() {
        if (config.model.isBlank()) throw LlmException(LlmException.Kind.MISSING_MODEL, "No model selected for ${config.name}")
    }

    protected fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    protected fun modelIds(root: JsonObject): List<String> =
        (root["data"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.string("id") }
            .distinct()
            .sorted()

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        internal const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
    }
}

internal class OpenAiCompatibleClient(
    config: LlmProviderConfig,
    apiKey: String,
    baseUrl: HttpUrl,
    http: OkHttpClient,
) : HttpLlmClient(config, apiKey, baseUrl, http) {

    override fun Request.Builder.authorize(): Request.Builder =
        if (apiKey.isEmpty()) this else header("Authorization", "Bearer $apiKey")

    override suspend fun complete(request: LlmRequest): LlmCompletion {
        requireModel()
        val root = try {
            post("chat/completions", body(request, withOptionalFields = true))
        } catch (e: LlmException) {
            // Providers differ on which optional fields they accept (response_format, the
            // token-limit field name). Retry once with the bare minimum before giving up.
            if (e.kind != LlmException.Kind.BAD_REQUEST || !hasOptionalFields(request)) throw e
            post("chat/completions", body(request, withOptionalFields = false))
        }
        return parse(root)
    }

    override suspend fun listModels(): List<String> = modelIds(get("models"))

    private fun hasOptionalFields(request: LlmRequest) =
        request.jsonMode || (config.presetValue.maxTokensField != null && request.maxOutputTokens > 0)

    internal fun body(request: LlmRequest, withOptionalFields: Boolean): JsonObject = buildJsonObject {
        put("model", config.model)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", request.system)
            }
            request.messages.forEach { message ->
                addJsonObject {
                    put("role", if (message.role == LlmMessage.Role.USER) "user" else "assistant")
                    put("content", message.content)
                }
            }
        }
        put("stream", false)
        if (withOptionalFields) {
            if (request.jsonMode) putJsonObject("response_format") { put("type", "json_object") }
            val maxTokensField = config.presetValue.maxTokensField
            if (maxTokensField != null && request.maxOutputTokens > 0) put(maxTokensField, request.maxOutputTokens)
        }
    }

    private fun parse(root: JsonObject): LlmCompletion {
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw LlmException(LlmException.Kind.INVALID_RESPONSE, "The provider returned no choices")
        val message = choice["message"] as? JsonObject
        val text = when (val content = message?.get("content")) {
            is JsonPrimitive -> content.contentOrNull.orEmpty()
            // some providers return content as a list of typed parts
            is JsonArray -> content.mapNotNull { (it as? JsonObject)?.string("text") }.joinToString("")
            else -> ""
        }
        val truncated = choice.string("finish_reason") == "length"
        if (text.isBlank()) {
            throw LlmException(
                LlmException.Kind.EMPTY_RESPONSE,
                if (truncated) "The model used its whole output budget before answering. Raise the max output tokens."
                else "The model returned an empty answer"
            )
        }
        return LlmCompletion(text, root.string("model"), truncated)
    }
}

internal class AnthropicClient(
    config: LlmProviderConfig,
    apiKey: String,
    baseUrl: HttpUrl,
    http: OkHttpClient,
) : HttpLlmClient(config, apiKey, baseUrl, http) {

    override fun Request.Builder.authorize(): Request.Builder {
        header("anthropic-version", ANTHROPIC_VERSION)
        return if (apiKey.isEmpty()) this else header("x-api-key", apiKey)
    }

    override suspend fun complete(request: LlmRequest): LlmCompletion {
        requireModel()
        return parse(post("messages", body(request)))
    }

    override suspend fun listModels(): List<String> = modelIds(get("models", mapOf("limit" to "1000")))

    internal fun body(request: LlmRequest): JsonObject = buildJsonObject {
        put("model", config.model)
        put("max_tokens", if (request.maxOutputTokens > 0) request.maxOutputTokens else LlmProviderConfig.DEFAULT_MAX_OUTPUT_TOKENS)
        put("system", request.system)
        putJsonArray("messages") {
            request.messages.forEach { message ->
                addJsonObject {
                    put("role", if (message.role == LlmMessage.Role.USER) "user" else "assistant")
                    put("content", message.content)
                }
            }
        }
    }

    private fun parse(root: JsonObject): LlmCompletion {
        val text = (root["content"] as? JsonArray).orEmpty()
            .mapNotNull { block -> (block as? JsonObject)?.takeIf { it.string("type") == "text" }?.string("text") }
            .joinToString("")
        val truncated = root.string("stop_reason") == "max_tokens"
        if (text.isBlank()) {
            throw LlmException(
                LlmException.Kind.EMPTY_RESPONSE,
                if (truncated) "The model used its whole output budget before answering. Raise the max output tokens."
                else "The model returned an empty answer"
            )
        }
        return LlmCompletion(text, root.string("model"), truncated)
    }

    companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}

/** Suspends until the call completes; cancelling the coroutine cancels the HTTP call. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}
