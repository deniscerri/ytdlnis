package com.deniscerri.ytdl.ui.more.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.os.bundleOf
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavArgument
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.viewmodel.TerminalViewModel
import com.deniscerri.ytdl.ui.adapter.TerminalSessionsAdapter
import com.deniscerri.ytdl.util.Extensions.enableFastScroll
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class TerminalSessionListFragment : Fragment(), TerminalSessionsAdapter.OnItemClickListener {
    private var topAppBar: MaterialToolbar? = null
    private lateinit var noResults: RelativeLayout
    private lateinit var adapter: TerminalSessionsAdapter

    private lateinit var terminalViewModel: TerminalViewModel


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        terminalViewModel = ViewModelProvider(requireActivity())[TerminalViewModel::class.java]
        return inflater.inflate(R.layout.fragment_terminal_session_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            noResults = view.findViewById(R.id.no_results)
            val recycler = view.findViewById<RecyclerView>(R.id.terminal_recycler)
            adapter = TerminalSessionsAdapter(this@TerminalSessionListFragment, requireActivity())
            recycler.adapter = adapter
            recycler.enableFastScroll()
            recycler.layoutManager = GridLayoutManager(requireContext(), resources.getInteger(R.integer.grid_size))
            topAppBar = requireActivity().findViewById(R.id.custom_command_toolbar)
            topAppBar!!.setNavigationOnClickListener { requireActivity().finish() }
            topAppBar?.menu?.forEach { it.isVisible = false }
            topAppBar?.menu?.get(0)?.isVisible = true

            topAppBar?.setOnMenuItemClickListener { m: MenuItem ->
                when(m.itemId){
                    R.id.add -> {
                        findNavController().navigate(R.id.terminalFragment, bundleOf(Pair("new", true)),
                            NavOptions.Builder().setPopUpTo(R.id.terminalFragment, true).build())
                    }
                }
                true
            }

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    terminalViewModel.isBoundState.collect { isBound ->
                        if (isBound) {
                            val sessions = terminalViewModel.sessionBinder?.getService()?.sessionList?.keys?.toList()
                            adapter.submitList(sessions)
                            noResults.visibility = if (sessions.isNullOrEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }
    }


    override fun onDeleteClick(sessionId: String) {
        terminalViewModel.sessionBinder?.terminateSession(sessionId)
        val sessions = terminalViewModel.sessionBinder?.getService()?.sessionList?.keys?.toList()
        if (sessions.isNullOrEmpty()) {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        } else {
            adapter.submitList(sessions)
            noResults.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onCardClick(sessionId: String) {
        val bundle = Bundle()
        bundle.putString("id", sessionId)
        findNavController().navigate(R.id.terminalFragment, bundle)
    }
}