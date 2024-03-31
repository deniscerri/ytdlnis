package com.deniscerri.ytdl.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.edit
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesMonthlyConfig
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesWeeklyConfig
import com.deniscerri.ytdl.database.repository.ObserveSourcesRepository
import com.deniscerri.ytdl.database.viewmodel.CommandTemplateViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdl.database.viewmodel.HistoryViewModel
import com.deniscerri.ytdl.database.viewmodel.ObserveSourcesViewModel
import com.deniscerri.ytdl.database.viewmodel.ResultViewModel
import com.deniscerri.ytdl.util.InfoUtil
import com.deniscerri.ytdl.util.UiUtil
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Month
import java.util.Calendar
import java.util.Locale


class ObserveSourcesBottomSheetDialog : BottomSheetDialogFragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadFragmentAdapter
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var observeSourcesViewModel: ObserveSourcesViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var resultViewModel: ResultViewModel
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var commandTemplateViewModel : CommandTemplateViewModel
    private lateinit var infoUtil: InfoUtil
    private lateinit var sharedPreferences : SharedPreferences
    private lateinit var view: View

    private lateinit var type: Type
    private var currentItem: ObserveSourcesItem? = null


    private lateinit var okButton: MaterialButton
    private lateinit var everyNr: TextInputLayout
    private lateinit var title: TextInputLayout
    private lateinit var url: TextInputLayout
    private lateinit var everyCat: AutoCompleteTextView
    private lateinit var everyTime: TextInputLayout
    private lateinit var weekDays: ChipGroup
    private lateinit var everyMonthDay: TextInputLayout
    private lateinit var startTime: TextInputLayout
    private lateinit var startMonth: TextInputLayout
    private lateinit var endsNever: RadioButton
    private lateinit var endsOn: RadioButton
    private lateinit var endsAfter: RadioButton
    private lateinit var endsOnTime: TextInputLayout
    private lateinit var endsAfterNr: TextInputLayout
    private lateinit var retryMissingDownloads: MaterialSwitch
    private lateinit var getOnlyNewUploads: MaterialSwitch
    private lateinit var resetProcessedLinks: MaterialSwitch


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        observeSourcesViewModel = ViewModelProvider(requireActivity())[ObserveSourcesViewModel::class.java]
        historyViewModel = ViewModelProvider(requireActivity())[HistoryViewModel::class.java]
        resultViewModel = ViewModelProvider(requireActivity())[ResultViewModel::class.java]
        commandTemplateViewModel = ViewModelProvider(requireActivity())[CommandTemplateViewModel::class.java]
        infoUtil = InfoUtil(requireContext())
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        currentItem = if (Build.VERSION.SDK_INT >= 33){
            arguments?.getParcelable("item", ObserveSourcesItem::class.java)
        }else{
            arguments?.getParcelable<ObserveSourcesItem>("item")
        }

        type = currentItem?.downloadItemTemplate?.type ?: arguments?.getSerializable("type") as Type
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val downloadItem = getDownloadItem()
        arguments?.putSerializable("type", downloadItem.type)
    }

    @SuppressLint("RestrictedApi", "InflateParams", "SetTextI18n")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        view = LayoutInflater.from(context).inflate(R.layout.observe_sources_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            if(resources.getBoolean(R.bool.isTablet) || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE){
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.peekHeight = displayMetrics.heightPixels
            }
        }

        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)

        (viewPager2.getChildAt(0) as? RecyclerView)?.apply {
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        var commandTemplateNr = 0
        lifecycleScope.launch{
            withContext(Dispatchers.IO){
                commandTemplateNr = commandTemplateViewModel.getTotalNumber()
                if(commandTemplateNr <= 0){
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.isClickable = true
                    (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.alpha = 0.3f
                }
            }
        }

        val fragmentManager = parentFragmentManager
        fragmentAdapter = DownloadFragmentAdapter(
            fragmentManager,
            lifecycle,
            ResultItem(0, "", "", "", "", "", "", "", mutableListOf(), "", null, null, null, 0),
            currentItem?.downloadItemTemplate,
            true
        )

        viewPager2.adapter = fragmentAdapter
        viewPager2.isSaveFromParentEnabled = false

        when(type) {
            Type.audio -> {
                tabLayout.getTabAt(0)!!.select()
                viewPager2.setCurrentItem(0, false)
            }
            Type.video -> {
                tabLayout.getTabAt(1)!!.select()
                viewPager2.setCurrentItem(1, false)
            }
            else -> {
                tabLayout.getTabAt(2)!!.select()
                viewPager2.postDelayed( {
                    viewPager2.setCurrentItem(2, false)
                }, 200)
            }
        }

        sharedPreferences.edit(commit = true) {
            putString("last_used_download_type",
                type.toString())
        }


        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab!!.position == 2 && commandTemplateNr == 0){
                    tabLayout.selectTab(tabLayout.getTabAt(1))
                    val s = Snackbar.make(view, getString(R.string.add_template_first), Snackbar.LENGTH_LONG)
                    val snackbarView: View = s.view
                    val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                    snackTextView.maxLines = 9999999
                    s.setAction(R.string.new_template){
                        UiUtil.showCommandTemplateCreationOrUpdatingSheet(
                            item = null, context = requireActivity(), lifeCycle = this@ObserveSourcesBottomSheetDialog, commandTemplateViewModel = commandTemplateViewModel,
                            newTemplate = {
                                commandTemplateNr = 1
                                (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.isClickable = true
                                (tabLayout.getChildAt(0) as? ViewGroup)?.getChildAt(2)?.alpha = 1f
                                tabLayout.selectTab(tabLayout.getTabAt(2))
                            },
                            dismissed = {}
                        )
                    }
                    s.show()
                }
                else{
                    viewPager2.setCurrentItem(tab.position, false)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        viewPager2.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.selectTab(tabLayout.getTabAt(position))
                runCatching {
                    sharedPreferences.edit(commit = true) {
                        putString("last_used_download_type",
                            listOf(Type.audio, Type.video, Type.command)[position].toString())
                    }
                }
            }
        })

        viewPager2.setPageTransformer(BackgroundToForegroundPageTransformer())


        title = view.findViewById(R.id.title_textinput)
        url = view.findViewById(R.id.url_textinput)
        everyNr = view.findViewById(R.id.every_textinput)
        everyCat = view.findViewById(R.id.everyCat)
        everyTime = view.findViewById(R.id.every_time)
        weekDays = view.findViewById(R.id.weekdays)
        everyMonthDay = view.findViewById(R.id.everyMonthDay_textInput)
        startTime = view.findViewById(R.id.start_time)
        startMonth = view.findViewById(R.id.startsMonth_textInput)
        endsNever = view.findViewById(R.id.never)
        endsOn = view.findViewById(R.id.on)
        endsOnTime = view.findViewById(R.id.on_date)
        endsAfter = view.findViewById(R.id.after)
        endsAfterNr = view.findViewById(R.id.after_nr)
        retryMissingDownloads = view.findViewById(R.id.retry_missing_downloads)
        getOnlyNewUploads = view.findViewById(R.id.get_new_uploads)
        resetProcessedLinks = view.findViewById(R.id.reset_processed_links)
        okButton = view.findViewById(R.id.okButton)

        title.editText!!.setText(currentItem?.name ?: "")
        title.editText?.doAfterTextChanged { checkIfValid() }

        url.apply {
            editText!!.setText(currentItem?.url ?: "")
            editText?.doAfterTextChanged {
                checkIfValid()
                if (url.editText!!.text.isNotEmpty()){
                    url.endIconDrawable = AppCompatResources.getDrawable(requireActivity(), R.drawable.ic_delete_all)
                }else{
                    url.endIconDrawable = AppCompatResources.getDrawable(requireActivity(), R.drawable.ic_clipboard)
                }
            }
            setEndIconOnClickListener {
                if(url.editText!!.text.isEmpty()){
                    val clipboard: ClipboardManager =
                        requireActivity().getSystemService(AppCompatActivity.CLIPBOARD_SERVICE) as ClipboardManager
                    url.editText!!.setText(clipboard.primaryClip?.getItemAt(0)?.text)
                }else{
                    url.editText!!.setText("")
                }
            }
        }

        everyNr.editText?.setText(currentItem?.everyNr?.toString() ?: "1")
        everyNr.editText?.doAfterTextChanged { checkIfValid() }

        val cats = ObserveSourcesRepository.everyCategoryName.map { getString(it.value) }
        val adapter = ArrayAdapter(requireActivity(),android.R.layout.simple_dropdown_item_1line, cats)
        everyCat.doAfterTextChanged {
            everyTime.isVisible = it.toString() != cats[0]
            weekDays.isVisible = it.toString() == cats[2]
            startMonth.isVisible = it.toString() == cats[3]
            startTime.isVisible = !startMonth.isVisible
            everyMonthDay.isVisible = startMonth.isVisible
        }
        everyCat.setAdapter(adapter)
        if (currentItem != null){
            val idx = ObserveSourcesRepository.EveryCategory.values().indexOf(currentItem!!.everyCategory)
            everyCat.setText(cats[idx], false)
        }else{
            everyCat.setText(cats.first(), false)
        }

        everyTime.apply {
            editText?.apply {
                isFocusable = false
                isClickable = false
                setOnClickListener{
                    UiUtil.showTimePicker(fragmentManager){
                        everyTime.editText?.setText(
                            SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmm"), Locale.getDefault()).format(it.timeInMillis)
                        )
                        everyTime.tag = it.timeInMillis
                        checkIfValid()
                    }
                }
                if (currentItem != null){
                    setText(SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmm"), Locale.getDefault()).format(currentItem?.everyTime))
                    everyTime.tag = currentItem?.everyTime
                    checkIfValid()
                }
            }
        }

        val calendar = Calendar.getInstance()
        //using 2024 because it starts with a monday
        calendar.set(Calendar.YEAR, 2024) // Set the year
        calendar.set(Calendar.MONTH, Calendar.JANUARY) // Set the month (Note: Months are zero-based)
        calendar.set(Calendar.DAY_OF_MONTH, 1) // Set the day of the month
        calendar.firstDayOfWeek = Calendar.MONDAY

        //weekly config

        val daysOfWeek = mutableListOf<String>()
        for (i in 0..6) {
            val dayName = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())!!
            daysOfWeek.add(dayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
            calendar.add(Calendar.DAY_OF_WEEK, 1)
        }
        weekDays.children.forEachIndexed { index, view ->
            (view as Chip).apply {
                text = daysOfWeek[index]
                tag = index + 1
            }
        }

        if (currentItem != null){
            weekDays.children.forEach {  view ->
                (view as Chip).isChecked =
                    currentItem!!.weeklyConfig?.weekDays?.contains(view.tag.toString().toInt()) == true
            }
        }


        //monthly config

        val everyMonthDayAutoCompleteTextView = view.findViewById<AutoCompleteTextView>(R.id.everyMonthDay)
        everyMonthDayAutoCompleteTextView.setText(
            currentItem?.monthlyConfig?.everyMonthDay?.toString() ?: Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
            , false)

        val months = mutableListOf<String>()
        for (i in 0..11){
            months.add(calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())!!.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
            calendar.add(Calendar.MONTH, 1)
        }

        val startMonthAutoCompleteTextView = startMonth.findViewById<AutoCompleteTextView>(R.id.startsMonth)
        startMonthAutoCompleteTextView.setAdapter(ArrayAdapter(requireActivity(),android.R.layout.simple_dropdown_item_1line, months))
        if (currentItem == null){
            startMonthAutoCompleteTextView.setText(months.first(), false)
        }else{
            currentItem?.monthlyConfig?.apply {
                calendar.set(Calendar.MONTH, this.startsMonth)
            }
            startMonthAutoCompleteTextView.setText(
                calendar.getDisplayName(Calendar.MONTH,
                    Calendar.LONG,
                    Locale.getDefault())!!.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, false
            )
        }

        startTime.apply {
            editText?.apply {
                isFocusable = false
                isClickable = true
                setOnClickListener {
                    UiUtil.showDatePickerOnly(fragmentManager){
                        startTime.editText?.setText(
                            SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy"), Locale.getDefault()).format(it.timeInMillis)
                        )
                        startTime.tag = it.timeInMillis
                        checkIfValid()
                    }
                }
                val now = Calendar.getInstance().timeInMillis
                startTime.editText?.setText(
                    SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy"), Locale.getDefault()).format(now)
                )
                startTime.tag = now
            }
        }


        endsOnTime.apply {
            editText?.apply {
                isFocusable = false
                isClickable = true
                setText(
                        SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy"), Locale.getDefault()).format(
                            Calendar.getInstance().apply { add(Calendar.MONTH, 5) }.timeInMillis)
                    )
                tag = Calendar.getInstance().apply { add(Calendar.MONTH, 5) }.timeInMillis
                setOnClickListener {
                    UiUtil.showDatePickerOnly(fragmentManager){
                        endsOnTime.editText?.setText(
                            SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMMyyyy"), Locale.getDefault()).format(it.timeInMillis)
                        )
                        endsOnTime.tag = it.timeInMillis
                        checkIfValid()
                    }
                }
                if (currentItem != null && currentItem!!.endsDate > 0){
                    setText(SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "HHmm"), Locale.getDefault()).format(currentItem?.endsDate))
                    endsOnTime.tag = currentItem?.endsDate
                    checkIfValid()
                }
            }
        }


        endsAfterNr.editText?.setText("10")
        endsAfterNr.editText?.doAfterTextChanged { checkIfValid() }
        if (currentItem != null && currentItem!!.endsAfterCount > 0){
            endsAfterNr.editText?.setText(currentItem!!.endsAfterCount.toString())
        }

        endsNever.setOnClickListener {
            endsOn.isChecked = false
            endsAfter.isChecked = false
            checkIfValid()
        }

        endsOn.setOnClickListener {
            endsNever.isChecked = false
            endsAfter.isChecked = false
            checkIfValid()
        }
        if ((currentItem?.endsDate ?: 0) > 0){
            endsOn.performClick()
        }

        endsAfter.setOnClickListener {
            endsOn.isChecked = false
            endsNever.isChecked = false
            checkIfValid()
        }
        if ((currentItem?.endsAfterCount ?: 0) > 0){
            endsAfter.performClick()
        }

        retryMissingDownloads.isChecked = currentItem?.retryMissingDownloads ?: false
        getOnlyNewUploads.isChecked = currentItem?.getOnlyNewUploads ?: false
        resetProcessedLinks.isVisible = currentItem != null

        if (currentItem != null) okButton.text = getString(R.string.update)
        okButton.setOnClickListener {
            lifecycleScope.launch {
                val item: DownloadItem = getDownloadItem()

                var ends = 0L
                if(endsOn.isChecked){
                    ends = endsOnTime.tag as Long
                }

                var endsAfterCount = 0
                if(endsAfter.isChecked){
                    endsAfterCount = endsAfterNr.editText!!.text.toString().toInt()
                }

                val category = ObserveSourcesRepository.EveryCategory.values()[adapter.getPosition(everyCat.text.toString())]

                val observeItem = ObserveSourcesItem(
                    id = currentItem?.id ?: 0,
                    name = title.editText!!.text.toString(),
                    url = url.editText!!.text.toString(),
                    downloadItemTemplate = item,
                    everyNr = everyNr.editText!!.text.toString().toInt(),
                    everyCategory = category,
                    everyTime = everyTime.tag as Long,
                    weeklyConfig = if (category == ObserveSourcesRepository.EveryCategory.WEEK) {
                        ObserveSourcesWeeklyConfig(
                            weekDays = weekDays.children.filter { (it as Chip).isChecked }.map { it.tag.toString().toInt() }.toList()
                        )
                    }else {
                        null
                    },
                    monthlyConfig = if (category == ObserveSourcesRepository.EveryCategory.MONTH){
                        ObserveSourcesMonthlyConfig(
                            everyMonthDay = everyMonthDay.editText!!.text.toString().toInt(),
                            startsMonth = startMonthAutoCompleteTextView.selectionStart
                        )
                    }else{
                        null
                    },
                    status = ObserveSourcesRepository.SourceStatus.ACTIVE,
                    startsTime = startTime.tag as Long,
                    endsDate = ends,
                    endsAfterCount = endsAfterCount,
                    runCount = 0,
                    getOnlyNewUploads = getOnlyNewUploads.isChecked,
                    retryMissingDownloads = retryMissingDownloads.isChecked,
                    alreadyProcessedLinks = if (resetProcessedLinks.isChecked){
                        mutableListOf()
                    }else{
                        currentItem?.alreadyProcessedLinks ?: mutableListOf()
                    }
                )

                withContext(Dispatchers.IO){
                    observeSourcesViewModel.insert(observeItem)
                }
                dismiss()
            }
        }

        checkIfValid()
    }

    private fun checkIfValid(){
        var valid = title.editText?.text?.isNotBlank() == true
                    && url.editText?.text?.isNotBlank() == true
                    && everyNr.editText?.text?.isNotBlank() == true
                    && everyTime.editText?.text?.isNotBlank() == true
                    && startTime.editText?.text?.isNotBlank() == true

        if (endsOn.isChecked && endsOnTime.editText?.text?.isBlank() == true) valid = false
        else if (endsAfter.isChecked && endsAfterNr.editText?.text?.isBlank() == true) valid = false

        okButton.isEnabled = valid
    }

    private fun getDownloadItem(selectedTabPosition: Int = tabLayout.selectedTabPosition) : DownloadItem{
        return when(selectedTabPosition){
            0 -> {
                val f = fragmentManager?.findFragmentByTag("f0") as DownloadAudioFragment
                f.downloadItem
            }
            1 -> {
                val f = fragmentManager?.findFragmentByTag("f1") as DownloadVideoFragment
                f.downloadItem
            }
            else -> {
                val f = fragmentManager?.findFragmentByTag("f2") as DownloadCommandFragment
                f.downloadItem
            }
        }
    }

}

