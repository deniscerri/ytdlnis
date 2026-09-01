package com.deniscerri.ytdl.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.io.File

/** Reads low-cost local signals used to avoid download/FFmpeg pile-ups on a hot phone. */
class DeviceResourceMonitor(context: Context) {
    private val applicationContext = context.applicationContext
    private val powerManager =
        applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun snapshot(): DeviceResourcePolicy.Snapshot {
        return DeviceResourcePolicy.Snapshot(
            cpuLoadPerCore = readCpuLoadPerCore(),
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                powerManager.currentThermalStatus
            } else {
                null
            },
            batteryTemperatureCelsius = readBatteryTemperature(),
        )
    }

    private fun readCpuLoadPerCore(): Double? = runCatching {
        val oneMinuteLoad = File("/proc/loadavg")
            .readText()
            .substringBefore(' ')
            .toDouble()
        oneMinuteLoad / Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    }.getOrNull()

    private fun readBatteryTemperature(): Float? {
        val battery = applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return null
        val tenthsCelsius = battery.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE,
            Int.MIN_VALUE,
        )
        return if (tenthsCelsius == Int.MIN_VALUE) null else tenthsCelsius / 10f
    }
}
