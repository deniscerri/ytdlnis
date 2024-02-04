package com.deniscerri.ytdlnis.ui.more

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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.MainActivity
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.ui.more.settings.SettingsActivity
import com.deniscerri.ytdlnis.ui.more.terminal.TerminalActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class MoreFragment : Fragment() {
    private lateinit var mainSharedPreferences: SharedPreferences
    private lateinit var mainSharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var terminal: TextView
    private lateinit var logs: TextView
    private lateinit var commandTemplates: TextView
    private lateinit var downloadQueue: TextView
    private lateinit var cookies: TextView
    private lateinit var observeSources: TextView
    private lateinit var terminateApp: TextView
    private lateinit var settings: TextView
    private lateinit var mainActivity: MainActivity
    private lateinit var downloadViewModel: DownloadViewModel
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainActivity = activity as MainActivity
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        return inflater.inflate(R.layout.fragment_more, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainSharedPreferences =  PreferenceManager.getDefaultSharedPreferences(requireContext())
        mainSharedPreferencesEditor = mainSharedPreferences.edit()
        terminal = view.findViewById(R.id.terminal)
        logs = view.findViewById(R.id.logs)
        commandTemplates = view.findViewById(R.id.command_templates)
        downloadQueue = view.findViewById(R.id.download_queue)
        cookies = view.findViewById(R.id.cookies)
        observeSources = view.findViewById(R.id.observe_sources)
        terminateApp = view.findViewById(R.id.terminate)
        settings = view.findViewById(R.id.settings)

        terminal.setOnClickListener {
            val intent = Intent(context, TerminalActivity::class.java)
            startActivity(intent)
        }

        logs.setOnClickListener {
            findNavController().navigate(R.id.downloadLogListFragment)
        }

        commandTemplates.setOnClickListener {
            findNavController().navigate(R.id.commandTemplatesFragment)
        }

        downloadQueue.setOnClickListener {
            findNavController().navigate(R.id.downloadQueueMainFragment)
        }

        cookies.setOnClickListener {
            findNavController().navigate(R.id.cookiesFragment)
        }

        observeSources.setOnClickListener {
            findNavController().navigate(R.id.observeSourcesFragment)
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
                terminateDialog.setPositiveButton(getString(R.string.ok)) { diag: DialogInterface?, _: Int ->
                    runBlocking {
                        val job : Job = lifecycleScope.launch(Dispatchers.IO) {
                            val activeDownloads = downloadViewModel.getActiveDownloads().toMutableList()
                            activeDownloads.map { it.status = DownloadRepository.Status.Queued.toString() }
                            activeDownloads.forEach {
                                downloadViewModel.updateDownload(it)
                            }
                        }
                        runBlocking {
                            job.join()
                            if (doNotShowAgain){
                                mainSharedPreferencesEditor.putBoolean("ask_terminate_app", false)
                                mainSharedPreferencesEditor.commit()
                            }
                            mainActivity.finishAndRemoveTask()
                            mainActivity.finishAffinity()
                            exitProcess(0)
                        }
                    }

                }
                terminateDialog.show()
            }else{
                mainActivity.finishAndRemoveTask()
                mainActivity.finishAffinity()
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