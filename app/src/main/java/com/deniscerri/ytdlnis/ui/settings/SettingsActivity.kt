package com.deniscerri.ytdlnis.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.deniscerri.ytdlnis.R
import com.google.android.material.appbar.MaterialToolbar

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
}