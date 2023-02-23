package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.widget.Adapter
import android.widget.Button
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.get
import androidx.core.view.size
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.ResultItem
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.*


class DownloadBottomSheetDialog(private val resultItem: ResultItem, private val type: Type) : BottomSheetDialogFragment() {
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private lateinit var fragmentAdapter : DownloadFragmentAdapter
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var behavior: BottomSheetBehavior<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.download_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels - 400
        }

        tabLayout = view.findViewById(R.id.download_tablayout)
        viewPager2 = view.findViewById(R.id.download_viewpager)

        val fragmentManager = parentFragmentManager
        fragmentAdapter = DownloadFragmentAdapter(
            resultItem,
            fragmentManager,
            lifecycle
        )
        viewPager2.adapter = fragmentAdapter
        viewPager2.isSaveFromParentEnabled = false

        when(type) {
            Type.audio -> {
                tabLayout.selectTab(tabLayout.getTabAt(0))
                viewPager2.setCurrentItem(0, false)
            }
            Type.video -> {
                tabLayout.selectTab(tabLayout.getTabAt(1))
                viewPager2.setCurrentItem(1, false)
            }
            else -> {
                tabLayout.selectTab(tabLayout.getTabAt(2))
                viewPager2.setCurrentItem(2, false)
            }
        }


        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewPager2.setCurrentItem(tab!!.position, false)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
            }
        })

        viewPager2.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.selectTab(tabLayout.getTabAt(position))
            }
        })

        viewPager2.setPageTransformer(BackgroundToForegroundPageTransformer())

        val scheduleBtn = view.findViewById<MaterialButton>(R.id.bottomsheet_schedule_button)
        scheduleBtn.setOnClickListener{
            val currentDate = Calendar.getInstance()
            val date = Calendar.getInstance()

            val datepicker = MaterialDatePicker.Builder.datePicker()
                .setCalendarConstraints(
                    CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointForward.now())
                        .build()
                )
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datepicker.addOnPositiveButtonClickListener{
                date.timeInMillis = it


                val timepicker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(currentDate.get(Calendar.HOUR_OF_DAY))
                    .setMinute(currentDate.get(Calendar.MINUTE))
                    .build()

                timepicker.addOnPositiveButtonClickListener{
                    date[Calendar.HOUR_OF_DAY] = timepicker.hour
                    date[Calendar.MINUTE] = timepicker.minute


                    val item: DownloadItem = getDownloadItem();
                    item.downloadStartTime = date.timeInMillis
                    downloadViewModel.queueDownloads(listOf(item))
                    dismiss()
                }
                timepicker.show(fragmentManager, "timepicker")

            }
            datepicker.show(fragmentManager, "datepicker")

        }

        val download = view.findViewById<Button>(R.id.bottomsheet_download_button)
        download!!.setOnClickListener {
            val item: DownloadItem = getDownloadItem();
            downloadViewModel.queueDownloads(listOf(item))
            dismiss()
        }
    }

    private fun getDownloadItem() : DownloadItem{
        when(tabLayout.selectedTabPosition){
            0 -> {
                val f = fragmentManager?.findFragmentByTag("f0") as DownloadAudioFragment
                return f.downloadItem
            }
            1 -> {
                val f = fragmentManager?.findFragmentByTag("f1") as DownloadVideoFragment
                return f.downloadItem
            }
            else -> {
                val f = fragmentManager?.findFragmentByTag("f2") as DownloadCommandFragment
                return f.downloadItem
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
        parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("downloadSingleSheet")!!).commit()
        for (i in 0 until viewPager2.adapter?.itemCount!!){
            if (parentFragmentManager.findFragmentByTag("f${i}") != null){
                parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("f$i")!!).commit()
            }
        }
    }
}

