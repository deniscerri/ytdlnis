package com.deniscerri.ytdl.ui.more.settings.ai

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.core.llm.LlmClients
import com.deniscerri.ytdl.core.llm.LlmException
import com.deniscerri.ytdl.core.llm.LlmMessage
import com.deniscerri.ytdl.core.llm.LlmProviderConfig
import com.deniscerri.ytdl.core.llm.LlmProviderPreset
import com.deniscerri.ytdl.core.llm.LlmRequest
import com.deniscerri.ytdl.core.llm.LlmSettings
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Add/edit form for an LLM provider profile, shared by the settings page and the terminal. */
object LlmProviderDialog {

    /** Lets the user pick a preset, then opens the form for a new profile. */
    fun showAdd(context: Context, scope: LifecycleCoroutineScope, settings: LlmSettings, onSaved: (LlmProviderConfig) -> Unit) {
        val presets = LlmProviderPreset.entries
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.ai_choose_provider_type)
            .setItems(presets.map { it.displayName }.toTypedArray()) { _, which ->
                show(context, scope, settings, null, presets[which], onSaved)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showEdit(context: Context, scope: LifecycleCoroutineScope, settings: LlmSettings, existing: LlmProviderConfig, onSaved: (LlmProviderConfig) -> Unit) {
        show(context, scope, settings, existing, existing.presetValue, onSaved)
    }

    private fun show(
        context: Context,
        scope: LifecycleCoroutineScope,
        settings: LlmSettings,
        existing: LlmProviderConfig?,
        initialPreset: LlmProviderPreset,
        onSaved: (LlmProviderConfig) -> Unit,
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_llm_provider, null)
        val presetField = view.findViewById<AutoCompleteTextView>(R.id.preset)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.name_layout)
        val name = view.findViewById<TextInputEditText>(R.id.name)
        val baseUrlLayout = view.findViewById<TextInputLayout>(R.id.base_url_layout)
        val baseUrl = view.findViewById<TextInputEditText>(R.id.base_url)
        val apiKeyLayout = view.findViewById<TextInputLayout>(R.id.api_key_layout)
        val apiKey = view.findViewById<TextInputEditText>(R.id.api_key)
        val modelLayout = view.findViewById<TextInputLayout>(R.id.model_layout)
        val model = view.findViewById<MaterialAutoCompleteTextView>(R.id.model)
        val maxTokens = view.findViewById<TextInputEditText>(R.id.max_tokens)
        val jsonMode = view.findViewById<MaterialSwitch>(R.id.json_mode)
        val status = view.findViewById<TextView>(R.id.status)

        var preset = initialPreset
        val hasStoredKey = existing != null && settings.secrets.has(existing.id)
        fun refreshKeyHelper() {
            apiKeyLayout.helperText = when {
                hasStoredKey -> context.getString(R.string.ai_api_key_saved)
                !preset.requiresApiKey -> context.getString(R.string.ai_api_key_optional)
                else -> null
            }
        }

        val presets = LlmProviderPreset.entries
        presetField.setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, presets.map { it.displayName }))
        presetField.setText(preset.displayName, false)
        presetField.setOnItemClickListener { _, _, position, _ ->
            val previous = preset
            preset = presets[position]
            // follow the preset unless the user already typed their own values
            if (baseUrl.text.isNullOrBlank() || baseUrl.text.toString() == previous.baseUrl) baseUrl.setText(preset.baseUrl)
            if (name.text.isNullOrBlank() || name.text.toString() == previous.displayName) name.setText(preset.displayName)
            jsonMode.isChecked = preset.supportsJsonMode
            refreshKeyHelper()
        }

        name.setText(existing?.name ?: preset.displayName)
        baseUrl.setText(existing?.baseUrl ?: preset.baseUrl)
        model.setText(existing?.model.orEmpty())
        maxTokens.setText((existing?.maxOutputTokens ?: LlmProviderConfig.DEFAULT_MAX_OUTPUT_TOKENS).toString())
        jsonMode.isChecked = existing?.jsonMode ?: preset.supportsJsonMode
        refreshKeyHelper()

        fun showStatus(text: String, isError: Boolean) {
            status.isVisible = true
            status.text = text
            // the status sits below the form; bring it into view above the dialog buttons
            (view as? ScrollView)?.let { scroll -> scroll.post { scroll.smoothScrollTo(0, status.bottom) } }
            status.setTextColor(
                MaterialColors.getColor(
                    status,
                    if (isError) com.google.android.material.R.attr.colorError else com.google.android.material.R.attr.colorOnSurface
                )
            )
        }

        fun typedKey(): String? = apiKey.text?.toString()?.trim()?.ifEmpty { null }
        fun effectiveKey(): String? = typedKey() ?: existing?.let { settings.secrets.get(it.id) }

        /** Builds the profile from the form, or marks the invalid fields and returns null. */
        fun formConfig(requireModel: Boolean): LlmProviderConfig? {
            listOf(nameLayout, baseUrlLayout, apiKeyLayout, modelLayout).forEach { it.error = null }
            var valid = true
            val url = baseUrl.text.toString().trim().trimEnd('/')
            try {
                LlmClients.validateBaseUrl(url)
            } catch (e: LlmException) {
                baseUrlLayout.error = context.getString(R.string.ai_error_url)
                valid = false
            }
            if (preset.requiresApiKey && typedKey() == null && !hasStoredKey) {
                apiKeyLayout.error = context.getString(R.string.ai_required)
                valid = false
            }
            if (requireModel && model.text.isNullOrBlank()) {
                modelLayout.error = context.getString(R.string.ai_required)
                valid = false
            }
            if (!valid) return null
            return LlmProviderConfig(
                id = existing?.id ?: LlmSettings.newId(),
                name = name.text.toString().trim().ifEmpty { preset.displayName },
                preset = preset.name,
                protocol = preset.protocol,
                baseUrl = url,
                model = model.text.toString().trim(),
                jsonMode = jsonMode.isChecked,
                maxOutputTokens = maxTokens.text.toString().toIntOrNull()?.coerceIn(256, 65536)
                    ?: LlmProviderConfig.DEFAULT_MAX_OUTPUT_TOKENS,
            )
        }

        var job: Job? = null
        fun runRequest(block: suspend () -> String) {
            job?.cancel()
            showStatus(context.getString(R.string.ai_testing), false)
            job = scope.launch {
                try {
                    showStatus(block(), false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LlmException) {
                    showStatus(LlmErrors.describe(context, e), true)
                } catch (e: Exception) {
                    showStatus(e.message ?: e.javaClass.simpleName, true)
                }
            }
        }

        modelLayout.setEndIconOnClickListener {
            val config = formConfig(requireModel = false) ?: return@setEndIconOnClickListener
            runRequest {
                val models = withContext(Dispatchers.IO) { LlmClients.create(config, effectiveKey()).listModels() }
                model.setSimpleItems(models.toTypedArray())
                if (models.isNotEmpty()) model.showDropDown()
                context.getString(R.string.ai_models_loaded, models.size)
            }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) R.string.ai_add_provider else R.string.ai_edit_provider)
            .setView(view)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.ai_test, null)
            .create()

        dialog.setOnShowListener {
            // replace the default listeners so validation errors keep the dialog open
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val config = formConfig(requireModel = true) ?: return@setOnClickListener
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { settings.save(config, typedKey()) }
                        dialog.dismiss()
                        onSaved(config)
                    } catch (e: Exception) {
                        showStatus(e.message ?: e.javaClass.simpleName, true)
                    }
                }
            }
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                val config = formConfig(requireModel = true) ?: return@setOnClickListener
                runRequest {
                    val completion = withContext(Dispatchers.IO) {
                        LlmClients.create(config, effectiveKey()).complete(
                            LlmRequest(
                                system = "You are a connectivity check. Reply with the JSON object {\"ok\": true} and nothing else.",
                                messages = listOf(LlmMessage(LlmMessage.Role.USER, "ping")),
                                jsonMode = config.jsonMode,
                                maxOutputTokens = config.maxOutputTokens,
                            )
                        )
                    }
                    context.getString(R.string.ai_test_ok, completion.model ?: config.model)
                }
            }
        }
        dialog.setOnDismissListener { job?.cancel() }
        dialog.show()
    }
}

object LlmErrors {
    fun describe(context: Context, e: LlmException): String {
        val summary = context.getString(
            when (e.kind) {
                LlmException.Kind.MISSING_API_KEY -> R.string.ai_error_missing_key
                LlmException.Kind.MISSING_MODEL -> R.string.ai_error_missing_model
                LlmException.Kind.INVALID_URL, LlmException.Kind.INSECURE_URL -> R.string.ai_error_url
                LlmException.Kind.UNAUTHORIZED -> R.string.ai_error_unauthorized
                LlmException.Kind.INSUFFICIENT_BALANCE -> R.string.ai_error_balance
                LlmException.Kind.RATE_LIMITED -> R.string.ai_error_rate_limited
                LlmException.Kind.NOT_FOUND -> R.string.ai_error_not_found
                LlmException.Kind.BAD_REQUEST -> R.string.ai_error_bad_request
                LlmException.Kind.SERVER_ERROR -> R.string.ai_error_server
                LlmException.Kind.NETWORK -> R.string.ai_error_network
                LlmException.Kind.TIMEOUT -> R.string.ai_error_timeout
                LlmException.Kind.EMPTY_RESPONSE, LlmException.Kind.INVALID_RESPONSE -> R.string.ai_error_response
            }
        )
        val detail = e.message.orEmpty()
        return if (detail.isBlank()) summary else "$summary\n$detail"
    }
}
