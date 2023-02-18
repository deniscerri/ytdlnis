package com.deniscerri.ytdlnis.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.deniscerri.ytdlnis.R
import com.google.android.material.appbar.MaterialToolbar
import java.util.*


class SettingsActivity : AppCompatActivity() {
    private var fm: FragmentManager? = null
    private var topAppBar: MaterialToolbar? = null
    var context: Context? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        context = baseContext
        topAppBar = findViewById(R.id.settings_toolbar)
        topAppBar!!.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        fm = supportFragmentManager
        fm!!.beginTransaction()
            .replace(R.id.settings_frame_layout, SettingsFragment())
            .commit()
    }

    fun setLocale(l: String){

        val locale = Locale(l)
        Locale.setDefault(locale)
        val config: Configuration = this.resources.configuration
        if (Build.VERSION.SDK_INT >= 24){
            Locale.setDefault(locale)
            config.setLocales(LocaleList(locale))
            this.resources.updateConfiguration(config, this.resources.displayMetrics)
            onConfigurationChanged(config)
        }else{
            Locale.setDefault(locale)
            config.locale = locale
            config.setLayoutDirection(locale)
            this.resources.updateConfiguration(config, this.resources.displayMetrics)
            onConfigurationChanged(config)
        }
        restartActivity()

    }

    private fun restartActivity() {
//        val intent = intent
//        finish()
//        startActivity(Intent(this, MainActivity::class.java))
        recreate()

    }

}