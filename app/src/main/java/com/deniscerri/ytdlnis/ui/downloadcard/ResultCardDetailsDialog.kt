package com.deniscerri.ytdlnis.ui.downloadcard

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
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
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
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.ui.adapter.ActiveDownloadAdapter
import com.deniscerri.ytdlnis.ui.adapter.ActiveDownloadMinifiedAdapter
import com.deniscerri.ytdlnis.ui.adapter.GenericDownloadAdapter
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.repository.DownloadRepository
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.ResultViewModel
import com.deniscerri.ytdlnis.util.Extensions.setFullScreen
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.NotificationUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.deniscerri.ytdlnis.util.VideoPlayerUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import java.util.*


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
                val data : MutableList<String?>  = withContext(Dispatchers.IO){
                    if (item.urls.isEmpty()) {
                        infoUtil.getStreamingUrlAndChapters(item.url)
                    }else {
                        item.urls.split("\n").toMutableList()
                    }
                }

                if (data.size > 1) data.removeFirst()

                if (data.isEmpty()) throw Exception("No Streaming URL found!")
                if (data.size == 2){
                    val audioSource : MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(MediaItem.fromUri(Uri.parse(data[0])))
                    val videoSource: MediaSource =
                        DefaultMediaSourceFactory(requireContext())
                            .createMediaSource(MediaItem.fromUri(Uri.parse(data[1])))
                    player.setMediaSource(MergingMediaSource(videoSource, audioSource))
                }else{
                    player.addMediaItem(MediaItem.fromUri(Uri.parse(data[0])))
                }
                player.addMediaItem(MediaItem.fromUri(Uri.parse(data[0])))

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

            val bottomSheet = BottomSheetDialog(requireContext())
            bottomSheet.requestWindowFeature(Window.FEATURE_NO_TITLE)
            bottomSheet.setContentView(R.layout.history_item_details_bottom_sheet)
            val title = bottomSheet.findViewById<TextView>(R.id.bottom_sheet_title)
            title!!.text = item.title.ifEmpty { "`${requireContext().getString(R.string.defaultValue)}`" }
            val author = bottomSheet.findViewById<TextView>(R.id.bottom_sheet_author)
            author!!.text = item.author.ifEmpty { "`${requireContext().getString(R.string.defaultValue)}`" }

            // BUTTON ----------------------------------
            val btn = bottomSheet.findViewById<MaterialButton>(R.id.downloads_download_button_type)

            when (item.type) {
                DownloadViewModel.Type.audio -> {
                    btn!!.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_music)
                }
                DownloadViewModel.Type.video -> {
                    btn!!.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_video)
                }
                else -> {
                    btn!!.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_terminal)
                }
            }

            val time = bottomSheet.findViewById<Chip>(R.id.time)
            val formatNote = bottomSheet.findViewById<Chip>(R.id.format_note)
            val container = bottomSheet.findViewById<Chip>(R.id.container_chip)
            val codec = bottomSheet.findViewById<Chip>(R.id.codec)
            val fileSize = bottomSheet.findViewById<Chip>(R.id.file_size)

            if (item.downloadStartTime <= System.currentTimeMillis() / 1000) time!!.visibility = View.GONE
            else {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = item.downloadStartTime
                time!!.text = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy - HHmm"), Locale.getDefault()).format(calendar.time)

                time.setOnClickListener {
                    UiUtil.showDatePicker(parentFragmentManager) {
                        bottomSheet.dismiss()
                        Toast.makeText(context, getString(R.string.download_rescheduled_to) + " " + it.time, Toast.LENGTH_LONG).show()
                        downloadViewModel.deleteDownload(item.id)
                        item.downloadStartTime = it.timeInMillis
                        WorkManager.getInstance(requireContext()).cancelAllWorkByTag(item.id.toString())
                        runBlocking {
                            downloadViewModel.queueDownloads(listOf(item))
                        }
                    }
                }
            }

            if (item.format.format_note == "?" || item.format.format_note == "") formatNote!!.visibility =
                View.GONE
            else formatNote!!.text = item.format.format_note

            if (item.format.container != "") container!!.text = item.format.container.uppercase()
            else container!!.visibility = View.GONE

            val codecText =
                if (item.format.encoding != "") {
                    item.format.encoding.uppercase()
                }else if (item.format.vcodec != "none" && item.format.vcodec != ""){
                    item.format.vcodec.uppercase()
                } else {
                    item.format.acodec.uppercase()
                }
            if (codecText == "" || codecText == "none"){
                codec!!.visibility = View.GONE
            }else{
                codec!!.visibility = View.VISIBLE
                codec.text = codecText
            }

            val fileSizeReadable = FileUtil.convertFileSize(item.format.filesize)
            if (fileSizeReadable == "?") fileSize!!.visibility = View.GONE
            else fileSize!!.text = fileSizeReadable

            val link = bottomSheet.findViewById<Button>(R.id.bottom_sheet_link)
            val url = item.url
            link!!.text = url
            link.tag = itemID
            link.setOnClickListener{
                bottomSheet.dismiss()
                UiUtil.openLinkIntent(requireContext(), item.url)
            }
            link.setOnLongClickListener{
                bottomSheet.dismiss()
                UiUtil.copyLinkToClipBoard(requireContext(), item.url)
                true
            }
            val remove = bottomSheet.findViewById<Button>(R.id.bottomsheet_remove_button)
            remove!!.tag = itemID
            remove.setOnClickListener{
                bottomSheet.hide()
                removeQueuedItem(itemID)
            }
            val openFile = bottomSheet.findViewById<Button>(R.id.bottomsheet_open_file_button)
            openFile!!.visibility = View.GONE



            val downloadNow = bottomSheet.findViewById<Button>(R.id.bottomsheet_redownload_button)
            if (item.downloadStartTime <= System.currentTimeMillis() / 1000) downloadNow!!.visibility = View.GONE
            else{
                downloadNow!!.text = getString(R.string.download_now)
                downloadNow.setOnClickListener {
                    bottomSheet.dismiss()
                    downloadViewModel.deleteDownload(item.id)
                    item.downloadStartTime = 0
                    WorkManager.getInstance(requireContext()).cancelAllWorkByTag(item.id.toString())
                    runBlocking {
                        downloadViewModel.queueDownloads(listOf(item))
                    }
                }
            }

            bottomSheet.show()
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            bottomSheet.behavior.peekHeight = displayMetrics.heightPixels
            bottomSheet.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
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
    override fun onPauseClick(itemID: Long, action: ActiveDownloadAdapter.ActiveDownloadAction, position: Int) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO){
                downloadViewModel.getItemByID(itemID)
            }
            when(action){
                ActiveDownloadAdapter.ActiveDownloadAction.Pause -> {
                    lifecycleScope.launch {
                        cancelItem(itemID.toInt())
                        item.status = DownloadRepository.Status.ActivePaused.toString()
                        withContext(Dispatchers.IO){
                            downloadViewModel.updateDownload(item)
                        }
                        activeAdapter.notifyItemChanged(position)
                    }
                }
                ActiveDownloadAdapter.ActiveDownloadAction.Resume -> {
                    lifecycleScope.launch {
                        item.status = DownloadRepository.Status.PausedReQueued.toString()
                        withContext(Dispatchers.IO){
                            downloadViewModel.updateDownload(item)
                        }
                        activeAdapter.notifyItemChanged(position)

                        val queue = if (activeCount > 1) listOf(item)
                        else withContext(Dispatchers.IO){
                            val list = downloadViewModel.getQueued().toMutableList()
                            list.map { it.status = DownloadRepository.Status.Queued.toString() }
                            list.add(0, item)
                            list
                        }

                        runBlocking {
                            downloadViewModel.queueDownloads(queue)
                        }
                    }
                }
            }

        }
    }
    override fun onCardClick() {
        this.dismiss()
        findNavController().navigate(
            R.id.downloadQueueMainFragment
        )
    }

    private fun cancelItem(id: Int){
        YoutubeDL.getInstance().destroyProcessById(id.toString())
        WorkManager.getInstance(requireContext()).cancelAllWorkByTag(id.toString())
        notificationUtil.cancelDownloadNotification(id)
    }

    private fun cancelActiveDownload(itemID: Long){
        lifecycleScope.launch {
            val id = itemID.toInt()
            YoutubeDL.getInstance().destroyProcessById(id.toString())
            WorkManager.getInstance(requireContext()).cancelAllWorkByTag(id.toString())
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
