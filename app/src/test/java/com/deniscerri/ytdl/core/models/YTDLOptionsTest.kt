package com.deniscerri.ytdl.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YTDLOptionsTest {
    @Test
    fun appendsAriaCertificateWithoutDuplicatingItOnRetry() {
        val options = YTDLOptions()
            .addOption("--downloader-args", "aria2c:-x4 -s4")

        assertTrue(
            options.appendToArgument(
                "--downloader-args",
                "aria2c:",
                "--ca-certificate=/cert.pem",
            ),
        )
        assertTrue(
            options.appendToArgument(
                "--downloader-args",
                "aria2c:",
                "--ca-certificate=/cert.pem",
            ),
        )
        assertFalse(options.appendToArgument("--missing", "aria2c:", "unused"))
        assertEquals(
            listOf("--downloader-args", "aria2c:-x4 -s4 --ca-certificate=/cert.pem"),
            options.buildOptions(),
        )
    }
}
