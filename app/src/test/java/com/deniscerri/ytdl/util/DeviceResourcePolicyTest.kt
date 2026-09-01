package com.deniscerri.ytdl.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceResourcePolicyTest {
    @Test
    fun criticalHeatHoldsNewDownloads() {
        val pressure = DeviceResourcePolicy.pressure(
            DeviceResourcePolicy.Snapshot(
                cpuLoadPerCore = 0.1,
                thermalStatus = 3,
                batteryTemperatureCelsius = 38f,
            ),
        )
        assertEquals(DeviceResourcePolicy.Pressure.CRITICAL, pressure)
        assertEquals(0, DeviceResourcePolicy.limitDownloads(4, pressure))
        assertFalse(DeviceResourcePolicy.allowPostProcessingOverlap(pressure))
    }

    @Test
    fun highCpuReducesFragmentsAndSerializesDownloads() {
        val pressure = DeviceResourcePolicy.pressure(
            DeviceResourcePolicy.Snapshot(1.2, null, null),
        )
        assertEquals(DeviceResourcePolicy.Pressure.HIGH, pressure)
        assertEquals(1, DeviceResourcePolicy.limitDownloads(4, pressure))
        assertEquals(2, DeviceResourcePolicy.limitFragments(8, pressure))
    }
}
