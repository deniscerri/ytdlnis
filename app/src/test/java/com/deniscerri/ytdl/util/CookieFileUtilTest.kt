package com.deniscerri.ytdl.util

import com.deniscerri.ytdl.database.models.CookieItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CookieFileUtilTest {
    @Test
    fun mergeKeepsNewestCookieAndDropsExpiredCookies() {
        val current = ".example.com\tTRUE\t/\tTRUE\t2000\tsession\tnew"
        val duplicate = ".example.com\tTRUE\t/\tTRUE\t3000\tsession\told"
        val expired = ".example.com\tTRUE\t/\tFALSE\t999\texpired\tgone"
        val httpOnly = "#HttpOnly_.example.com\tTRUE\t/\tTRUE\t0\thttp\tonly"

        val result = CookieFileUtil.merge(
            listOf(
                CookieItem(2, "https://example.com", "$current\n$expired\n$httpOnly"),
                CookieItem(1, "https://example.com", duplicate),
            ),
            nowEpochSeconds = 1000,
        )

        assertTrue(result.endsWith("\n"))
        assertTrue(result.contains(current))
        assertTrue(result.contains(httpOnly))
        assertFalse(result.contains(duplicate))
        assertFalse(result.contains(expired))
        assertEquals(5, result.lineSequence().filter { it.isNotEmpty() }.count())
    }

    @Test
    fun writeSkipsUnchangedCookieJar() {
        val directory = Files.createTempDirectory("ytdlnis-cookie-test").toFile()
        val target = directory.resolve("cookies.txt")

        assertTrue(CookieFileUtil.writeAtomicallyIfChanged(target, "first"))
        assertFalse(CookieFileUtil.writeAtomicallyIfChanged(target, "first"))
        assertTrue(CookieFileUtil.writeAtomicallyIfChanged(target, "second"))
        assertEquals("second", target.readText())
        directory.deleteRecursively()
    }

    @Test
    fun sessionCopiesAreIsolatedAndCleanedTogether() {
        val directory = Files.createTempDirectory("ytdlnis-cookie-session-test").toFile()
        val source = directory.resolve("cookies.txt").apply { writeText("first") }

        val firstSession = CookieFileUtil.createSessionCopy(directory, source, 101)
        source.writeText("second")
        val secondSession = CookieFileUtil.createSessionCopy(directory, source, 202)

        assertEquals("first", firstSession.readText())
        assertEquals("second", secondSession.readText())
        CookieFileUtil.deleteAllSessionCopies(directory)
        assertFalse(firstSession.exists())
        assertFalse(secondSession.exists())
        directory.deleteRecursively()
    }
}
