package com.deniscerri.ytdl.ui.more.settings.ai

import androidx.preference.Preference
import com.deniscerri.ytdl.ui.more.settings.SettingHost
import com.deniscerri.ytdl.ui.more.settings.SettingModule

/** Only makes the page searchable; [AiAssistantSettingsFragment] handles the provider list. */
object AiAssistantSettingsModule : SettingModule {
    override fun bindLogic(pref: Preference, host: SettingHost) {}
}
