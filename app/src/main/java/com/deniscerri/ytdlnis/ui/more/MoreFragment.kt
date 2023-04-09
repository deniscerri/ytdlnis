package com.deniscerri.ytdlnis.ui.more

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.ArrayList
import kotlin.system.exitProcess

class MoreFragment : Fragment() {
    private lateinit var mainSharedPreferences: SharedPreferences
    private lateinit var mainSharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var terminal: TextView
    private lateinit var logs: TextView
    private lateinit var commandTemplates: TextView
    private lateinit var downloadQueue: TextView
    private lateinit var cookies: TextView
    private lateinit var terminateApp: TextView
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
        mainSharedPreferencesEditor = mainSharedPreferences.edit()
        terminal = view.findViewById(R.id.terminal)
        logs = view.findViewById(R.id.logs)
        commandTemplates = view.findViewById(R.id.command_templates)
        downloadQueue = view.findViewById(R.id.download_queue)
        cookies = view.findViewById(R.id.cookies)
        terminateApp = view.findViewById(R.id.terminate)
        settings = view.findViewById(R.id.settings)

        val navHostFragment = parentFragmentManager.findFragmentById(R.id.frame_layout)

        if (mainSharedPreferences.getBoolean("log_downloads", false)) {
            logs.visibility = View.VISIBLE
        }else {
            logs.visibility = View.GONE
        }


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

        cookies.setOnClickListener {
            val intent = Intent(context, CookiesActivity::class.java)
            startActivity(intent)
        }

        terminateApp.setOnClickListener {
            if (mainSharedPreferences.getBoolean("ask_terminate_app", true)){
                var doNotShowAgain = false
                val terminateDialog = MaterialAlertDialogBuilder(requireContext())
                terminateDialog.setTitle(getString(R.string.confirm_delete_history))
                val dialogView = layoutInflater.inflate(R.layout.dialog_terminate_app, null)
                val checkbox = dialogView.findViewById<CheckBox>(R.id.doNotShowAgain)
                terminateDialog.setView(dialogView)



                checkbox.setOnCheckedChangeListener { compoundButton, b ->
                    doNotShowAgain = compoundButton.isChecked
                }

                terminateDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
                terminateDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                    if (doNotShowAgain){
                        mainSharedPreferencesEditor.putBoolean("ask_terminate_app", false)
                        mainSharedPreferencesEditor.commit()
                    }
                    mainActivity.finishAndRemoveTask()
                    exitProcess(0)
                }
                terminateDialog.show()
            }else{
                mainActivity.finishAndRemoveTask()
                exitProcess(0)
            }

        }

        settings.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            startActivity(intent)
        }

    }

    companion object {
        const val TAG = "MoreFragment"
    }

}