package com.deniscerri.ytdl.ui.welcome

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.deniscerri.ytdl.R

class WelcomeGuideFragment : Fragment(R.layout.fragment_welcome_guide) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.open_guide).setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GUIDE_URL))) }
        }
    }

    private companion object {
        const val GUIDE_URL = "https://ytdlnis.org"
    }
}
