package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import android.content.res.Configuration
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.SearchSettingsItem
import com.deniscerri.ytdl.ui.more.settings.advanced.AdvancedSettingsModule
import com.deniscerri.ytdl.ui.more.settings.downloading.DownloadSettingsModule
import com.deniscerri.ytdl.ui.more.settings.folder.FolderSettingsModule
import com.deniscerri.ytdl.ui.more.settings.folder.temporary.TemporaryFilesModule
import com.deniscerri.ytdl.ui.more.settings.general.GeneralSettingsModule
import com.deniscerri.ytdl.ui.more.settings.processing.ProcessingSettingsModule
import com.deniscerri.ytdl.ui.more.settings.updating.app.AppUpdateSettingsModule
import com.deniscerri.ytdl.ui.more.settings.updating.UpdateSettingsModule
import com.deniscerri.ytdl.ui.more.settings.updating.ytdlp.YtdlpUpdateSettingsModule
import java.util.Locale

object SettingsRegistry {
    private val xmlToModule = mapOf(
        R.xml.general_preferences to GeneralSettingsModule,
        R.xml.folders_preference to FolderSettingsModule,
        R.xml.temporary_files_preferences to TemporaryFilesModule,
        R.xml.downloading_preferences to DownloadSettingsModule,
        R.xml.processing_preferences to ProcessingSettingsModule,
        R.xml.updating_preferences to UpdateSettingsModule,
        R.xml.ytdlp_update_preferences to YtdlpUpdateSettingsModule,
        R.xml.app_update_preferences to AppUpdateSettingsModule,
        R.xml.advanced_preferences to AdvancedSettingsModule
    )

    fun getModuleForXml(xmlRes: Int) = xmlToModule[xmlRes]

    fun bindFragment(fragment: BaseSettingsFragment, xmlRes: Int) {
        val module = getModuleForXml(xmlRes) ?: return
        val allPrefs = mutableListOf<Preference>()
        fragment.getPreferences(fragment.preferenceScreen, allPrefs).forEach {
            module.bindLogic(it, fragment)
        }
    }

    fun indexAll(context: Context): List<SearchSettingsItem> {
        val manager = PreferenceManager(context)
        val enContext = if (context.resources.configuration.locales[0].language == Locale.ENGLISH.language) null
            else context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        val results = mutableListOf<SearchSettingsItem>()

        xmlToModule.forEach { (xmlRes, module) ->
            val screen = manager.inflateFromResource(context, xmlRes, null)
            val enScreen = enContext?.let { runCatching { PreferenceManager(it).inflateFromResource(it, xmlRes, null) }.getOrNull() }
            results.addAll(crawl(screen, enScreen, xmlRes, module))
        }
        return results
    }

    private fun crawl(
        group: PreferenceGroup,
        enGroup: PreferenceGroup?,
        xmlId: Int,
        module: SettingModule?,
        parentTitle: String? = null,
        enParentTitle: String? = null
    ): List<SearchSettingsItem> {
        val list = mutableListOf<SearchSettingsItem>()

        val groupTitle = group.title?.toString()
        val enGroupTitle = enGroup?.title?.toString() ?: enParentTitle

        if (!groupTitle.isNullOrBlank()) {
            list.add(SearchSettingsItem(
                preference = group,
                xmlId = xmlId,
                module = module,
                groupTitle = groupTitle,
                isHeader = true,
                canRebind = true,
                englishGroupTitle = enGroupTitle
            ))
        }

        val preferencesWithoutRebindingLogic = listOf("ytdl-version")

        for (i in 0 until group.preferenceCount) {
            val p = group.getPreference(i)
            val enP = enGroup?.takeIf { i < it.preferenceCount }?.getPreference(i)

            // custom widgets like the update status card can't be shown as a search result
            if (p is NonSearchablePreference) continue
            if (p !is PreferenceGroup && p.title.isNullOrBlank()) continue

            if (p is PreferenceGroup) {
                list.addAll(crawl(
                    p,
                    enP as? PreferenceGroup,
                    xmlId,
                    module,
                    group.title?.toString(),
                    enGroupTitle
                ))
            } else if (p.key != null && p.key != "reset_preferences") {
                list.add(SearchSettingsItem(
                    preference = p,
                    xmlId = xmlId,
                    module = module,
                    groupTitle = group.title?.toString() ?: parentTitle,
                    isHeader = false,
                    canRebind = !preferencesWithoutRebindingLogic.contains(p.key),
                    englishTitle = enP?.title?.toString(),
                    englishSummary = enP?.summary?.toString(),
                    englishGroupTitle = enGroupTitle
                ))
            }
        }
        return list
    }
}