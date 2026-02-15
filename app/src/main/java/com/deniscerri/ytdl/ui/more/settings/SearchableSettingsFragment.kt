package com.deniscerri.ytdl.ui.more.settings

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R

/**
 * Extension of BaseSettingsFragment that adds support for:
 * 1. Highlighting a specific preference when navigating from search
 * 2. Returning to search results when pressing back
 */
abstract class SearchableSettingsFragment : BaseSettingsFragment() {
    
    private var highlightKey: String? = null
    private var shouldReturnToSearch: Boolean = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get arguments passed from search navigation
        arguments?.let {
            highlightKey = it.getString(MainSettingsFragment.ARG_HIGHLIGHT_KEY)
            shouldReturnToSearch = it.getBoolean(MainSettingsFragment.ARG_RETURN_TO_SEARCH, false)
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Highlight the preference if we navigated from search
        highlightKey?.let { key ->
            highlightPreference(key)
        }
    }
    
    private fun highlightPreference(key: String) {
        // Delay to ensure preferences are fully loaded
        Handler(Looper.getMainLooper()).postDelayed({
            val preference = findPreference<Preference>(key)
            if (preference != null) {
                // Scroll to the preference
                scrollToPreference(preference)
                
                // Highlight it
                highlightPreferenceView(preference)
            }
        }, 300)
    }
    
    private fun highlightPreferenceView(preference: Preference) {
        // Find the RecyclerView containing preferences
        val recyclerView = listView as? RecyclerView ?: return
        
        // Find the view holder for this preference
        Handler(Looper.getMainLooper()).postDelayed({
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                val holder = recyclerView.getChildViewHolder(child)
                
                // Check if this is the preference we're looking for
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val item = preferenceScreen.getPreference(adapterPosition)
                    if (item?.key == preference.key) {
                        // Animate the background color
                        animateHighlight(child)
                        break
                    }
                }
            }
        }, 100)
    }
    
    private fun animateHighlight(view: View) {
        val highlightColor = ContextCompat.getColor(requireContext(), R.color.colorAccent)
        val transparentColor = Color.TRANSPARENT
        
        // Pulse animation: transparent -> highlight -> transparent
        val colorAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            transparentColor,
            highlightColor,
            transparentColor
        ).apply {
            duration = 1500
            repeatCount = 2
            
            addUpdateListener { animator ->
                view.setBackgroundColor(animator.animatedValue as Int)
            }
        }
        
        colorAnimator.start()
        
        // Clear background after animation
        Handler(Looper.getMainLooper()).postDelayed({
            view.setBackgroundColor(Color.TRANSPARENT)
        }, 5000)
    }
    
    /**
     * Override to handle back button behavior
     * Returns true if the back press was handled, false otherwise
     */
    open fun onBackPressed(): Boolean {
        if (shouldReturnToSearch) {
            // Navigate back to main settings with search still active
            findNavController().popBackStack()
            return true
        }
        return false
    }
}
