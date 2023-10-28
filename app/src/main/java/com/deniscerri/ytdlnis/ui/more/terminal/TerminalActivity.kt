package com.deniscerri.ytdlnis.ui.more.terminal

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavArgument
import androidx.navigation.NavGraph
import androidx.navigation.NavType
import androidx.navigation.fragment.NavHostFragment
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdlnis.ui.BaseActivity
import com.deniscerri.ytdlnis.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates


class TerminalActivity : BaseActivity() {
    private lateinit var terminalViewModel: TerminalViewModel
    private lateinit var navHostFragment: NavHostFragment
    private var downloadID by Delegates.notNull<Long>()
    private lateinit var graph: NavGraph

    @SuppressLint("SetTextI18n")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)
        terminalViewModel = ViewModelProvider(this)[TerminalViewModel::class.java]
        downloadID = savedInstanceState?.getLong("downloadID") ?: 0L
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type
        Log.e(TAG, "$action $type")
        var text : String? = null
        if (action == Intent.ACTION_SEND && type != null) {
            Log.e(TAG, action)
            text = if (intent.getStringExtra(Intent.EXTRA_TEXT) == null){
                val uri = if (Build.VERSION.SDK_INT >= 33){
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                }else{
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                "-a \"${FileUtil.formatPath(uri?.path ?: "")}\""
            }else{
                intent.getStringExtra(Intent.EXTRA_TEXT)
            }
        }
        navHostFragment = supportFragmentManager.findFragmentById(R.id.frame_layout) as NavHostFragment
        graph = navHostFragment.navController.navInflater.inflate(R.navigation.terminal_graph)
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO){
                terminalViewModel.getCount()
            }
            val bundle = Bundle()
            if (count == 0){
                bundle.putString("share", text ?: "")
                graph.setStartDestination(R.id.terminalFragment)
                graph.findNode(graph.startDestinationId)?.addArgument("share", NavArgument.Builder()
                    .setType(NavType.StringType)
                    .setDefaultValue(text ?: "")
                    .build()
                )
            }
            navHostFragment.navController.setGraph(graph, bundle)
        }
    }

    companion object {
        private const val TAG = "TerminalActivity"
    }

}