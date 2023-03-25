package com.deniscerri.ytdlnis.ui.more

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.databinding.FragmentHomeBinding
import com.deniscerri.ytdlnis.ui.downloads.DownloadQueueActivity
import com.deniscerri.ytdlnis.ui.more.downloadLogs.DownloadLogListActivity
import com.deniscerri.ytdlnis.ui.more.settings.SettingsActivity

class MoreFragment : Fragment() {
    private lateinit var mainSharedPreferences: SharedPreferences
    private lateinit var terminal: TextView
    private lateinit var logs: TextView
    private lateinit var commandTemplates: TextView
    private lateinit var downloadQueue: TextView
    private lateinit var settings: TextView
    private lateinit var mainActivity: MainActivity
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        return inflater.inflate(R.layout.fragment_more, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainSharedPreferences = requireContext().getSharedPreferences("root_preferences", Activity.MODE_PRIVATE)
        terminal = view.findViewById(R.id.terminal)
        logs = view.findViewById(R.id.logs)
        commandTemplates = view.findViewById(R.id.command_templates)
        downloadQueue = view.findViewById(R.id.download_queue)
        settings = view.findViewById(R.id.settings)

        val navHostFragment = parentFragmentManager.findFragmentById(R.id.frame_layout)

        terminal.setOnClickListener {
            val intent = Intent(context, TerminalActivity::class.java)
            startActivity(intent)
        }

        logs.setOnClickListener {
            val intent = Intent(context, DownloadLogListActivity::class.java)
            startActivity(intent)
        }

        commandTemplates.setOnClickListener {
            val intent = Intent(context, CommandTemplatesActivity::class.java)
            startActivity(intent)
        }

        downloadQueue.setOnClickListener {
            val intent = Intent(context, DownloadQueueActivity::class.java)
            startActivity(intent)
        }

        settings.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            startActivity(intent)
        }

    }

    companion object {
        const val TAG = "MoreFragment"
    }

    override fun onResume() {
        super.onResume()
        if (mainSharedPreferences.getBoolean("log_downloads", false)) {
            logs.visibility = View.VISIBLE
        }else {
            logs.visibility = View.GONE
        }

    }
}