package com.deniscerri.ytdl.core.llm

import kotlinx.serialization.Serializable

@Serializable
enum class LlmProtocol { OPENAI_COMPATIBLE, ANTHROPIC }

/** Pre-fills the provider form; models are loaded from the provider, not hardcoded. */
enum class LlmProviderPreset(
    val displayName: String,
    val protocol: LlmProtocol,
    val baseUrl: String,
    val requiresApiKey: Boolean = true,
    val supportsJsonMode: Boolean = true,
    /** Request field that caps the reply length, or null to leave it to the provider. */
    val maxTokensField: String? = "max_tokens",
) {
    OPENAI("OpenAI", LlmProtocol.OPENAI_COMPATIBLE, "https://api.openai.com/v1", maxTokensField = "max_completion_tokens"),
    ANTHROPIC("Anthropic", LlmProtocol.ANTHROPIC, "https://api.anthropic.com/v1", supportsJsonMode = false),
    DEEPSEEK("DeepSeek", LlmProtocol.OPENAI_COMPATIBLE, "https://api.deepseek.com"),
    GEMINI("Google Gemini", LlmProtocol.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai"),
    OPENROUTER("OpenRouter", LlmProtocol.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1"),
    GROQ("Groq", LlmProtocol.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1"),
    MISTRAL("Mistral", LlmProtocol.OPENAI_COMPATIBLE, "https://api.mistral.ai/v1"),
    XAI("xAI", LlmProtocol.OPENAI_COMPATIBLE, "https://api.x.ai/v1"),
    OLLAMA("Ollama (this device)", LlmProtocol.OPENAI_COMPATIBLE, "http://localhost:11434/v1", requiresApiKey = false),
    CUSTOM_OPENAI("Custom (OpenAI-compatible)", LlmProtocol.OPENAI_COMPATIBLE, "", requiresApiKey = false),
    CUSTOM_ANTHROPIC("Custom (Anthropic-compatible)", LlmProtocol.ANTHROPIC, "", requiresApiKey = false, supportsJsonMode = false);

    companion object {
        fun fromName(name: String?): LlmProviderPreset =
            entries.firstOrNull { it.name == name } ?: CUSTOM_OPENAI
    }
}

/** The API key is not stored here but in [LlmSecretStore], keyed by [id]. */
@Serializable
data class LlmProviderConfig(
    val id: String,
    val name: String,
    val preset: String,
    val protocol: LlmProtocol,
    val baseUrl: String,
    val model: String,
    val jsonMode: Boolean = true,
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
) {
    val presetValue: LlmProviderPreset get() = LlmProviderPreset.fromName(preset)

    companion object {
        // leaves room for reasoning models
        const val DEFAULT_MAX_OUTPUT_TOKENS = 4096
    }
}
