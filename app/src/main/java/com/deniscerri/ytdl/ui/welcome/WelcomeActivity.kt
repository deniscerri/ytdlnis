package com.deniscerri.ytdl.ui.welcome

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.MainActivity
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.WelcomeViewModel
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.work.background.UpdateCheckWorker
import com.google.android.material.button.MaterialButton
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * First-run flow: intro (with backup restore), guide, permissions and update preferences.
 * [MainActivity] hands over to it until [PREF_COMPLETED] is set.
 */
class WelcomeActivity : AppCompatActivity() {
    private lateinit var viewModel: WelcomeViewModel
    private lateinit var bottomBar: View
    private lateinit var backButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var progress: LinearProgressIndicator

    private val preferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtil.updateTheme(this)
        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        viewModel = ViewModelProvider(this)[WelcomeViewModel::class.java]
        bottomBar = findViewById(R.id.bottom_bar)
        backButton = findViewById(R.id.welcome_back)
        nextButton = findViewById(R.id.welcome_next)
        progress = findViewById(R.id.welcome_progress)

        backButton.setOnClickListener { goTo(viewModel.page - 1) }
        nextButton.setOnClickListener {
            if (viewModel.page == LAST_PAGE) finishWelcome() else goTo(viewModel.page + 1)
        }
        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.page > 0) {
                goTo(viewModel.page - 1)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.welcome_container, createPage(viewModel.page))
                .commit()
        }
        updateChrome()
    }

    private fun createPage(page: Int): Fragment = when (page) {
        0 -> WelcomeIntroFragment()
        1 -> WelcomeGuideFragment()
        2 -> WelcomePermissionsFragment()
        else -> WelcomeUpdatesFragment()
    }

    fun goTo(page: Int) {
        if (page !in 0..LAST_PAGE) return
        viewModel.page = page
        supportFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .replace(R.id.welcome_container, createPage(page))
            .commit()
        updateChrome()
    }

    private fun updateChrome() {
        val page = viewModel.page
        // the first page has its own buttons, so only the progress bar stays visible there
        backButton.visibility = if (page > 0) View.VISIBLE else View.INVISIBLE
        nextButton.visibility = if (page > 0) View.VISIBLE else View.INVISIBLE
        progress.setProgressCompat(page + 1, true)
        nextButton.setText(if (page == LAST_PAGE) R.string.welcome_finish else R.string.welcome_next)
        // pages that need something first (permissions) turn this off again in onResume
        nextButton.isEnabled = true
    }

    fun setNextEnabled(enabled: Boolean) {
        nextButton.isEnabled = enabled
    }

    /** Saves the choices from the last page, marks the flow as done and opens the app. */
    private fun finishWelcome() {
        val channels = resources.getStringArray(R.array.ytdlp_source)
        val channelValues = resources.getStringArray(R.array.ytdlp_source_values)
        val builtIn = channelValues.indexOf(viewModel.ytdlpSource)
        val label = if (builtIn >= 0) channels[builtIn] else viewModel.ytdlpSourceLabel

        preferences.edit(commit = true) {
            putBoolean("update_app", viewModel.updateApp)
            putBoolean("auto_update_ytdlp", viewModel.autoUpdateYtdlp)
            putString("ytdlp_source", viewModel.ytdlpSource)
            putString("ytdlp_source_label", label)
            putBoolean("asked_auto_update_preferences", true)
            putBoolean(PREF_COMPLETED, true)
        }
        if (viewModel.updateApp) UpdateCheckWorker.schedule(applicationContext)
        launchMain()
    }

    /** Called after a backup was restored from the first page. The backup replaces all settings. */
    fun onBackupRestored(settingsRestored: Boolean) {
        preferences.edit(commit = true) {
            putBoolean("asked_auto_update_preferences", true)
            putBoolean(PREF_COMPLETED, true)
        }
        if (settingsRestored) {
            val language = preferences.getString("app_language", "en")
            AppCompatDelegate.setApplicationLocales(
                if (language == "system") LocaleListCompat.forLanguageTags(null)
                else LocaleListCompat.forLanguageTags(language.toString())
            )
        }
        if (preferences.getBoolean("update_app", false)) UpdateCheckWorker.schedule(applicationContext)
        launchMain()
    }

    private fun launchMain() {
        // resume whatever launched the app (a shortcut, a link) instead of dropping it
        val next = IntentCompat.getParcelableExtra(intent, EXTRA_NEXT_INTENT, Intent::class.java)
            ?: Intent(this, MainActivity::class.java)
        startActivity(Intent(next).putExtra(EXTRA_FROM_WELCOME, true))
        finish()
    }

    companion object {
        const val PREF_COMPLETED = "welcome_completed"
        const val EXTRA_NEXT_INTENT = "welcome_next_intent"
        const val EXTRA_FROM_WELCOME = "from_welcome"
        private const val LAST_PAGE = 3

        /** Users who already answered the old update dialog have been through setup, so they skip this. */
        fun isNeeded(preferences: SharedPreferences): Boolean =
            !preferences.getBoolean(PREF_COMPLETED, false) &&
                    !preferences.getBoolean("asked_auto_update_preferences", false)
    }
}
