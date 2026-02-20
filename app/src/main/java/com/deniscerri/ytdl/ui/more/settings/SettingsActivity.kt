package com.deniscerri.ytdl.ui.more.settings

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

// Activity that hosts all settings fragments and provides a search bar
// that filters preferences across the currently visible settings screen.
class SettingsActivity : BaseActivity() {
    lateinit var binding: ActivitySettingsBinding
    private lateinit var searchEditText: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        val navController = navHostFragment.findNavController()

        searchEditText = binding.settingsSearchEditText
        setupSearchFunctionality()

        // Update toolbar title when destination changes; clear search focus when leaving main settings.
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.mainSettingsFragment) {
                changeTopAppbarTitle(getString(R.string.settings))
            } else {
                clearSearchFocus()
            }
        }
        navController.addOnDestinationChangedListener(listener)

        // Toolbar back button – just dispatch back press.
        binding.settingsToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Handle back press: if in main settings and in search mode, exit search first.
        // Otherwise, let the system handle normally.
        onBackPressedDispatcher.addCallback(this) {
            if (navController.currentDestination?.id == R.id.mainSettingsFragment) {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as? NavHostFragment
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()

                if (currentFragment is MainSettingsFragment && currentFragment.handleBackPressed()) {
                    // Search mode was active – clear the search field and hide keyboard.
                    searchEditText.text?.clear()
                    hideKeyboard()
                    return@addCallback
                }

                // Not in search mode – pop the stack and finish the activity if at root.
                navController.popBackStack()
                finishAndRemoveTask()
            } else {
                navController.navigateUp()
            }
        }

        // Start with the main settings fragment.
        if (savedInstanceState == null) {
            navController.navigate(R.id.mainSettingsFragment)
        }
    }

    // Attach a text watcher to the search field so that every keystroke filters the current fragment.
    private fun setupSearchFunctionality() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSettings(s?.toString() ?: "")
            }
        })
    }

    // Pass the query to the currently visible settings fragment if it supports filtering.
    private fun filterSettings(query: String) {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as? NavHostFragment
        val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()

        if (currentFragment is BaseSettingsFragment) {
            currentFragment.filterPreferences(query)
        }
    }

    // Update the collapsing toolbar title.
    fun changeTopAppbarTitle(text: String) {
        if (this::binding.isInitialized) {
            binding.collapsingToolbar.title = text
        }
    }

    // Remove focus from the search field and hide the keyboard.
    fun clearSearchFocus() {
        if (this::searchEditText.isInitialized) {
            searchEditText.clearFocus()
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        currentFocus?.let { view ->
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}