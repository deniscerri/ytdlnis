package com.deniscerri.ytdl.core.llm

import com.deniscerri.ytdl.core.llm.CommandSafety.BlockedReason
import com.deniscerri.ytdl.core.llm.CommandSafety.Risk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandSafetyTest {

    private fun risks(command: String) = CommandSafety.analyze(command).risks

    @Test
    fun plainYtdlpCommandHasNoRisks() {
        val analysis = CommandSafety.analyze("""yt-dlp -x --audio-format mp3 -o "%(title)s.%(ext)s" "https://youtu.be/abc?si=x&t=1"""")
        assertTrue(analysis.canWrite)
        assertTrue(analysis.risks.isEmpty())
    }

    @Test
    fun operatorsInsideQuotesAreFine() {
        assertTrue(risks("""yt-dlp -f "bv*[height<=720]+ba/b" "https://a.b/?x=1&y=2"""").isEmpty())
        assertTrue(risks("yt-dlp -o '%(title)s;\$(id)s|x' URL").isEmpty())
    }

    @Test
    fun unquotedShellOperatorsAreFlagged() {
        listOf(
            "yt-dlp URL; rm -rf /sdcard",
            "yt-dlp URL && echo done",
            "yt-dlp URL | tee log",
            "yt-dlp URL > out.txt",
            "yt-dlp URL &",
            "yt-dlp `whoami`",
            "yt-dlp \$(cat urls)",
            "yt-dlp \"\$(cat urls)\"",
        ).forEach { assertTrue(it, Risk.SHELL_OPERATORS in risks(it)) }
    }

    @Test
    fun otherProgramsAreFlagged() {
        assertTrue(Risk.NOT_YTDLP in risks("rm -rf /sdcard/Download"))
        assertTrue(Risk.NOT_YTDLP in risks("python -m yt_dlp URL"))
        assertTrue(Risk.RUNS_PROGRAMS in risks("yt-dlp --exec 'rm {}' URL"))
        assertTrue(Risk.RUNS_PROGRAMS in risks("yt-dlp --exec-before-download=x URL"))
        assertTrue(Risk.RUNS_PROGRAMS in risks("yt-dlp --netrc-cmd x URL"))
        assertTrue(Risk.RUNS_PROGRAMS in risks("yt-dlp --config-location /sdcard/x.conf URL"))
        assertTrue(Risk.RUNS_PROGRAMS in risks("yt-dlp --ppa \"ffmpeg:-y -i x\" URL"))
        assertTrue(Risk.RUNS_PROGRAMS in risks("yt-dlp --downloader-args aria2c:--x URL"))
        assertTrue(Risk.UPDATES_YTDLP in risks("yt-dlp -U"))
        assertTrue(Risk.UPDATES_YTDLP in risks("yt-dlp --update-to nightly"))
        assertTrue(Risk.CONTAINS_CREDENTIALS in risks("yt-dlp -u me -p secret URL"))
        assertTrue(Risk.CONTAINS_CREDENTIALS in risks("yt-dlp --password=secret URL"))
        assertTrue(Risk.UNTERMINATED_QUOTE in risks("yt-dlp \"https://a.b"))
    }

    @Test
    fun capitalPIsPathsNotPassword() {
        assertFalse(Risk.CONTAINS_CREDENTIALS in risks("yt-dlp -P /sdcard/Download URL"))
    }

    @Test
    fun controlCharactersAndNewlinesBlockWriting() {
        assertEquals(BlockedReason.MULTIPLE_LINES, CommandSafety.analyze("yt-dlp a\nrm -rf x").blockedReason)
        assertEquals(BlockedReason.MULTIPLE_LINES, CommandSafety.analyze("yt-dlp a\r").blockedReason)
        assertEquals(BlockedReason.CONTROL_CHARACTERS, CommandSafety.analyze("yt-dlp \u001b[2J").blockedReason)
        assertEquals(BlockedReason.CONTROL_CHARACTERS, CommandSafety.analyze("yt-dlp \u0003").blockedReason)
        assertEquals(BlockedReason.CONTROL_CHARACTERS, CommandSafety.analyze("yt-dlp ‮abc").blockedReason)
        assertEquals(BlockedReason.EMPTY, CommandSafety.analyze("   ").blockedReason)
    }

    @Test
    fun cleanJoinsContinuationsAndStripsInvisibleCharacters() {
        val (joined, removedNothing) = CommandSafety.clean("yt-dlp \\\n  -x \\\n  URL\n")
        assertEquals("yt-dlp -x URL", joined)
        assertFalse(removedNothing)

        val (cleaned, removed) = CommandSafety.clean("$ yt-dlp‮ -x\u0007 URL")
        assertEquals("yt-dlp -x URL", cleaned)
        assertTrue(removed)
        assertTrue(CommandSafety.analyze(cleaned).canWrite)
    }
}
