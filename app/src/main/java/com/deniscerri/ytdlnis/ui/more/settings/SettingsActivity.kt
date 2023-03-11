package com.deniscerri.ytdlnis.ui.more.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.deniscerri.ytdlnis.R
import com.google.android.material.appbar.MaterialToolbar


class SettingsActivity : AppCompatActivity() {
    private var fm: FragmentManager? = null
    private var topAppBar: MaterialToolbar? = null
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
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
}