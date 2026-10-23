package com.deniscerri.ytdl.core.llm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Provider profiles and the active selection. Kept in a private preferences file of their own
 * (not the default preferences that settings backups export); keys live in [secrets].
 */
class LlmSettings(
    private val prefs: SharedPreferences,
    val secrets: LlmSecretStore,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        KeystoreLlmSecretStore(context),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(LlmProviderConfig.serializer())

    fun providers(): List<LlmProviderConfig> {
        val stored = prefs.getString(KEY_PROVIDERS, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, stored) }.getOrDefault(emptyList())
    }

    fun provider(id: String): LlmProviderConfig? = providers().firstOrNull { it.id == id }

    /** The selected provider, falling back to the first one so a single profile just works. */
    fun activeProvider(): LlmProviderConfig? {
        val all = providers()
        val activeId = prefs.getString(KEY_ACTIVE, null)
        return all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
    }

    fun setActive(id: String) {
        prefs.edit { putString(KEY_ACTIVE, id) }
    }

    /** Adds or replaces a profile. A non-null [apiKey] replaces the stored key; blank removes it. */
    fun save(config: LlmProviderConfig, apiKey: String?) {
        // store the key first: if the Keystore fails, no profile is left without its key
        when {
            apiKey == null -> {}
            apiKey.isBlank() -> secrets.remove(config.id)
            else -> secrets.put(config.id, apiKey.trim())
        }
        val updated = providers().filterNot { it.id == config.id } + config
        prefs.edit {
            putString(KEY_PROVIDERS, json.encodeToString(serializer, updated))
            if (prefs.getString(KEY_ACTIVE, null) == null) putString(KEY_ACTIVE, config.id)
        }
    }

    fun delete(id: String) {
        val remaining = providers().filterNot { it.id == id }
        prefs.edit {
            putString(KEY_PROVIDERS, json.encodeToString(serializer, remaining))
            if (prefs.getString(KEY_ACTIVE, null) == id) {
                remaining.firstOrNull()?.let { putString(KEY_ACTIVE, it.id) } ?: remove(KEY_ACTIVE)
            }
        }
        secrets.remove(id)
    }

    fun client(config: LlmProviderConfig): LlmClient = LlmClients.create(config, secrets.get(config.id))

    companion object {
        private const val PREFS_NAME = "llm_providers"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_ACTIVE = "active_provider"

        /** Default-preferences switch that shows the assistant button in the terminal. */
        const val PREF_TERMINAL_ASSISTANT = "terminal_ai_assistant"

        fun newId(): String = UUID.randomUUID().toString()
    }
}
