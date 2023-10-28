package com.deniscerri.ytdlnis.ui.more.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.addCallback
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.databinding.ActivitySettingsBinding
import com.deniscerri.ytdlnis.ui.BaseActivity


class SettingsActivity : BaseActivity() {
    var context: Context? = null
    lateinit var binding: ActivitySettingsBinding
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = baseContext
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        val navController = navHostFragment.findNavController()

        val listener = NavController.OnDestinationChangedListener { controller, destination, arguments ->
            if (destination.id == R.id.mainSettingsFragment){
                changeTopAppbarTitle(getString(R.string.settings))
            }
        }

        navController.addOnDestinationChangedListener(listener)
        binding.settingsToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (navController.currentDestination?.id == R.id.mainSettingsFragment) {
                navController.popBackStack()
                finishAndRemoveTask()
            }else{
                navController.navigateUp()
            }
        }

        if (savedInstanceState == null) navController.navigate(R.id.mainSettingsFragment)
    }

    fun changeTopAppbarTitle(text: String) {
        if (this::binding.isInitialized) binding.collapsingToolbar.title = text
    }
}