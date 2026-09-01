package com.deniscerri.ytdl.util

import kotlin.math.min

/** Converts CPU and temperature samples into bounded scheduling limits. */
object DeviceResourcePolicy {
    enum class Pressure {
        NORMAL,
        ELEVATED,
        HIGH,
        CRITICAL,
    }

    data class Snapshot(
        val cpuLoadPerCore: Double?,
        val thermalStatus: Int?,
        val batteryTemperatureCelsius: Float?,
    )

    fun pressure(snapshot: Snapshot): Pressure {
        val thermal = snapshot.thermalStatus
        val battery = snapshot.batteryTemperatureCelsius
        val cpu = snapshot.cpuLoadPerCore

        if ((thermal != null && thermal >= 3) || (battery != null && battery >= 48f)) {
            return Pressure.CRITICAL
        }
        if ((thermal != null && thermal >= 2) ||
            (battery != null && battery >= 44f) ||
            (cpu != null && cpu >= 1.15)
        ) {
            return Pressure.HIGH
        }
        if ((thermal != null && thermal >= 1) ||
            (battery != null && battery >= 40f) ||
            (cpu != null && cpu >= 0.85)
        ) {
            return Pressure.ELEVATED
        }
        return Pressure.NORMAL
    }

    fun limitDownloads(requested: Int, pressure: Pressure): Int = when (pressure) {
        Pressure.NORMAL -> requested.coerceAtLeast(1)
        Pressure.ELEVATED -> min(requested.coerceAtLeast(1), 2)
        Pressure.HIGH -> 1
        Pressure.CRITICAL -> 0
    }

    fun limitFragments(requested: Int, pressure: Pressure): Int = when (pressure) {
        Pressure.NORMAL -> requested.coerceAtLeast(1)
        Pressure.ELEVATED -> min(requested.coerceAtLeast(1), 4)
        Pressure.HIGH -> min(requested.coerceAtLeast(1), 2)
        Pressure.CRITICAL -> 1
    }

    fun allowPostProcessingOverlap(pressure: Pressure): Boolean {
        return pressure == Pressure.NORMAL
    }
}
