package com.deniscerri.ytdl.ui.more

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.PlaybackCoordinator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class PlayerBottomSheetDialog : BottomSheetDialogFragment() {
    private lateinit var playbackCoordinator: PlaybackCoordinator
    private var playerView: PlayerView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_player_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playbackCoordinator = ViewModelProvider(requireActivity())[PlaybackCoordinator::class.java]
        if (!playbackCoordinator.state.value.hasMedia) {
            dismiss()
            return
        }

        (dialog as? BottomSheetDialog)?.apply {
            setCanceledOnTouchOutside(true)
            behavior.isDraggable = true
        }
        view.findViewById<View>(R.id.close_player).setOnClickListener { dismiss() }

        playerView = view.findViewById(R.id.player_view)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playbackCoordinator.controller.collect { controller ->
                    playerView?.player = controller
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playbackCoordinator.state.collect { state ->
                    if (!state.hasMedia && isVisible) dismiss()
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        playbackCoordinator.savePosition()
        playerView?.player = null
    }

    companion object {
        const val TAG = "playerSheet"
    }
}
