package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.addCallback
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.databinding.ActivitySettingsBinding
import com.deniscerri.ytdl.ui.BaseActivity
import com.google.android.material.textfield.TextInputEditText


class SettingsActivity : BaseActivity() {
    var context: Context? = null
    lateinit var binding: ActivitySettingsBinding
    private lateinit var searchEditText: TextInputEditText
    
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = baseContext
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        val navController = navHostFragment.findNavController()

        searchEditText = binding.settingsSearchEditText
        setupSearchFunctionality()

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
    
    private fun setupSearchFunctionality() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                filterSettings(query)
            }
        })
    }
    
    private fun filterSettings(query: String) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as? NavHostFragment
        val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
        
        if (currentFragment is BaseSettingsFragment) {
            currentFragment.filterPreferences(query)
        }
    }

    fun changeTopAppbarTitle(text: String) {
        if (this::binding.isInitialized) binding.collapsingToolbar.title = text
    }
}
