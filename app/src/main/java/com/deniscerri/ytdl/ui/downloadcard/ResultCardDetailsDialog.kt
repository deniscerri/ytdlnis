package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.findNavController
import androidx.paging.filter
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.ui.adapter.ActiveDownloadMinifiedAdapter
import com.deniscerri.ytdl.ui.adapter.GenericDownloadAdapter
import com.deniscerri.ytdl.util.Extensions.setFullScreen
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.InfoUtil
import com.deniscerri.ytdl.util.NotificationUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.VideoPlayerUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.yausername.youtubedl_android.YoutubeDL
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class ResultCardDetailsDialog : BottomSheetDialogFragment(), GenericDownloadAdapter.OnItemClickListener, ActiveDownloadMinifiedAdapter.OnItemClickListener {
    private lateinit var infoUtil: InfoUtil
    private lateinit var notificationUtil: NotificationUtil
    private lateinit var videoView: PlayerView
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var resultViewModel: ResultViewModel

    private lateinit var activeAdapter: ActiveDownloadMinifiedAdapter
    private lateinit var queuedAdapter: GenericDownloadAdapter
    private var activeCount: Int = 0
    
    private lateinit var downloadManager: DownloadManager

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var dialogView : View
    private lateinit var item: ResultItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        infoUtil = InfoUtil(requireActivity())
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        resultViewModel = ViewModelProvider(this)[ResultViewModel::class.java]
        downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout to use as dialog or embedded fragment
        dialogView =  inflater.inflate(R.layout.result_card_details, container, false)
        return dialogView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }


    @SuppressLint("RestrictedApi", "SetTextI18n", "UseGetLayoutInflater")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.result_card_details, null)
        dialog.setContentView(view)
        dialog.window?.navigationBarColor = SurfaceColors.SURFACE_1.getColor(requireActivity())

        dialog.setOnShowListener {
            val behavior = BottomSheetBehavior.from(dialogView.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            if(resources.getBoolean(R.bool.isTablet) || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
                dialog.setFullScreen()
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val i = if (Build.VERSION.SDK_INT >= 33){
            arguments?.getParcelable("result", ResultItem::class.java)
        }else{
            arguments?.getParcelable<ResultItem>("result")
        }

        if (i == null) {
            dismiss()
            return
        }

        item = i

        //remove outdated player url of 1hr so it can refetch it in the player
        if (item.creationTime > System.currentTimeMillis() - 3600000) item.urls = ""

        activeAdapter = ActiveDownloadMinifiedAdapter(this,requireActivity())
        queuedAdapter = GenericDownloadAdapter(this,requireActivity())

        val bottomSheetLink = view.findViewById<MaterialButton>(R.id.bottom_sheet_link)
        val downloadThumb = view.findViewById<MaterialButton>(R.id.download_thumb)
        val title = view.findViewById<TextView>(R.id.title)
        val bottomInfo = view.findViewById<TextView>(R.id.bottom_info)
        val downloadMusic = view.findViewById<Button>(R.id.download_music)
        val downloadVideo = view.findViewById<Button>(R.id.download_video)

        val runningRecycler = view.findViewById<RecyclerView>(R.id.running_recycler)
        val running = view.findViewById<TextView>(R.id.running)
        val queuedRecycler = view.findViewById<RecyclerView>(R.id.queued_recycler)
        val queued = view.findViewById<TextView>(R.id.queued)

        runningRecycler.adapter = activeAdapter
        runningRecycler.layoutManager = GridLayoutManager(context, 1)

        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (preferences.getBoolean("swipe_gestures", true)){
            val itemTouchHelper = ItemTouchHelper(simpleCallback)
            itemTouchHelper.attachToRecyclerView(queuedRecycler)
        }

        queuedRecycler.adapter = queuedAdapter
        queuedRecycler.layoutManager = GridLayoutManager(context, resources.getInteger(R.integer.grid_size))

        lifecycleScope.launch {
            downloadViewModel.activeDownloads.map { it.filter { d -> d.url == item.url } }.collectLatest {
                delay(500)
                activeAdapter.submitList(it)
                if (it.isEmpty()){
                    running.visibility = View.GONE
                    runningRecycler.visibility = View.GONE
                }else{
                    running.visibility = View.VISIBLE
                    runningRecycler.visibility = View.VISIBLE
                }
            }
        }

        WorkManager.getInstance(requireContext())
            .getWorkInfosByTagLiveData("download")
            .observe(viewLifecycleOwner){ list ->
                list.forEach {work ->
                    if (work == null) return@forEach
                    val id = work.progress.getLong("id", 0L)
                    if(id == 0L) return@forEach

                    val progress = work.progress.getInt("progress", 0)
                    val progressBar = view.findViewWithTag<LinearProgressIndicator>("$id##progress")
                    requireActivity().runOnUiThread {
                        try {
                            progressBar?.setProgressCompat(progress, true)
                        }catch (ignored: Exception) {}
                    }
                }
            }

        lifecycleScope.launch {
            downloadViewModel.queuedDownloads.map { it.filter { d -> d.url == item.url } }.collectLatest {
                queuedAdapter.submitData(it)
            }
        }

        queuedAdapter.addLoadStateListener { loadState ->
            lifecycleScope.launch {
                if (loadState.append.endOfPaginationReached )
                {
                    if (queuedAdapter.itemCount < 1){
                        queued.visibility = View.GONE
                        queuedRecycler.visibility = View.GONE
                    }else{
                        queued.visibility = View.VISIBLE
                        queuedRecycler.visibility = View.VISIBLE
                    }
                }
            }
        }

        lifecycleScope.launch {
            downloadViewModel.activeDownloadsCount.collectLatest {
                activeCount = it
            }
        }


        bottomSheetLink.text = item.url
        bottomSheetLink.setOnClickListener{
            UiUtil.openLinkIntent(requireContext(), item.url)
        }
        bottomSheetLink.setOnLongClickListener{
            UiUtil.copyLinkToClipBoard(requireContext(), item.url)
            true
        }

        downloadThumb.setOnClickListener {
            downloadManager.enqueue(
                DownloadManager.Request(item.thumb.toUri())
                    .setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_WIFI or
                                DownloadManager.Request.NETWORK_MOBILE
                    )
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "YTDLnis/" + item.title + ".jpg"))
        }

        title.text = item.title
        bottomInfo.text = item.author

        downloadMusic.setOnClickListener {
            onButtonClick(DownloadViewModel.Type.audio)
        }

        downloadVideo.setOnClickListener {
            onButtonClick(DownloadViewModel.Type.video)
        }


        videoView = view.findViewById(R.id.video_view)
        val player = VideoPlayerUtil.buildPlayer(requireContext())
        videoView.player = player

        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    if (item.urls.isEmpty()) {
                        infoUtil.getStreamingUrlAndChapters(item.url)
                    }else{
                        Pair(item.urls.split("\n"), null)
                    }
                }

                if (data.first.isEmpty()) throw Exception("No Data found!")

                val urls = data.first
                if (urls.size == 2){
                    val audioSource : MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(MediaItem.fromUri(Uri.parse(urls[0])))
                    val videoSource: MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(MediaItem.fromUri(Uri.parse(urls[1])))
                    player.setMediaSource(MergingMediaSource(videoSource, audioSource))
                }else{
                    player.addMediaItem(MediaItem.fromUri(Uri.parse(urls[0])))
                }

                player.prepare()
                player.play()
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
    }

    private fun onButtonClick(type: DownloadViewModel.Type){
        if (sharedPreferences.getBoolean("download_card", true)) {
            val bundle = Bundle()
            bundle.putParcelable("result", item)
            bundle.putSerializable("type", type)
            findNavController().navigateUp()
            findNavController().navigate(R.id.downloadBottomSheetDialog, bundle)
        } else {
            lifecycleScope.launch{
                val downloadItem = withContext(Dispatchers.IO){
                    downloadViewModel.createDownloadItemFromResult(
                        result = item,
                        givenType = type)
                }
                downloadViewModel.queueDownloads(listOf(downloadItem))
                findNavController().navigateUp()
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        cleanUp()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanUp()
    }


    private fun cleanUp(){
        kotlin.runCatching {
            videoView.player?.stop()
            videoView.player?.release()
        }
    }

    private fun removeQueuedItem(id: Long){
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(id)
            }
            val deleteDialog = MaterialAlertDialogBuilder(requireContext())
            deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + item.title + "\"!")
            deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
            deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                item.status = DownloadRepository.Status.Cancelled.toString()
                lifecycleScope.launch(Dispatchers.IO){
                    downloadViewModel.updateDownload(item)
                }

                Snackbar.make(requireView().rootView, getString(R.string.cancelled) + ": " + item.title, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo)) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            downloadViewModel.deleteDownload(item.id)
                            downloadViewModel.queueDownloads(listOf(item))
                        }
                    }.show()
            }
            deleteDialog.show()
        }
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val itemID = viewHolder.itemView.tag.toString().toLong()
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        lifecycleScope.launch {
                            val deletedItem = withContext(Dispatchers.IO){
                                downloadViewModel.getItemByID(itemID)
                            }
                            queuedAdapter.notifyItemChanged(viewHolder.bindingAdapterPosition)
                            removeQueuedItem(deletedItem.id)
                        }
                    }

                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(
                    requireContext(),
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
                    .addSwipeLeftBackgroundColor(Color.RED)
                    .addSwipeLeftActionIcon(R.drawable.baseline_delete_24)
                    .addSwipeRightBackgroundColor(
                        MaterialColors.getColor(
                            requireContext(),
                            R.attr.colorOnSurfaceInverse, Color.TRANSPARENT
                        )
                    )
                    .create()
                    .decorate()
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }

    override fun onActionButtonClick(itemID: Long) {
        removeQueuedItem(itemID)
    }

    override fun onCardClick(itemID: Long) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }

            UiUtil.showDownloadItemDetailsCard(
                item,
                requireActivity(),
                DownloadRepository.Status.valueOf(item.status),
                removeItem = { it: DownloadItem, sheet: BottomSheetDialog ->
                    sheet.hide()
                    removeQueuedItem(itemID)
                },
                downloadItem = {
                    runBlocking{
                        downloadViewModel.queueDownloads(listOf(it))
                    }
                },
                longClickDownloadButton = {
                    findNavController().navigate(R.id.downloadBottomSheetDialog, bundleOf(
                        Pair("downloadItem", it),
                        Pair("result", downloadViewModel.createResultItemFromDownload(it)),
                        Pair("type", it.type)
                    )
                    )
                },
                scheduleButtonClick = {}
            )
        }
    }

    override fun onCardSelect(isChecked: Boolean, position: Int) {}

    override fun onCancelClick(itemID: Long) {
        lifecycleScope.launch {
            if (activeCount == 1){
                val queue = withContext(Dispatchers.IO){
                    val list = downloadViewModel.getQueued().toMutableList()
                    list.map { it.status = DownloadRepository.Status.Queued.toString() }
                    list
                }

                runBlocking {
                    downloadViewModel.queueDownloads(queue)
                }
            }

        }

        cancelActiveDownload(itemID)
    }
    override fun onCardClick() {
        this.dismiss()
        findNavController().navigate(
            R.id.downloadQueueMainFragment
        )
    }

    private fun cancelActiveDownload(itemID: Long){
        lifecycleScope.launch {
            val id = itemID.toInt()
            YoutubeDL.getInstance().destroyProcessById(id.toString())
            notificationUtil.cancelDownloadNotification(id)

            withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }.let {
                it.status = DownloadRepository.Status.Cancelled.toString()
                lifecycleScope.launch(Dispatchers.IO){
                    downloadViewModel.updateDownload(it)
                }
            }
        }

    }

}
