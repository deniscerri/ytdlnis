package com.deniscerri.ytdl.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {
    private fun redact(text: String) = SecretRedactor.redact(text)
    private val r = SecretRedactor.PLACEHOLDER

    @Test
    fun ytdlpCredentialOptions() {
        assertEquals("yt-dlp -u $r -p $r URL", redact("yt-dlp -u alice -p hunter2 URL"))
        assertEquals("yt-dlp --password=$r URL", redact("yt-dlp --password=hunter2 URL"))
        assertEquals("yt-dlp --video-password $r URL", redact("yt-dlp --video-password \"a b\" URL"))
        assertEquals("yt-dlp --twofactor $r", redact("yt-dlp --twofactor 123456"))
    }

    @Test
    fun pathsAndOrdinaryOptionsAreKept() {
        val command = "yt-dlp -P /sdcard/Download -o \"%(title)s.%(ext)s\" -f \"bv*+ba\" https://youtu.be/abc?si=share"
        assertEquals(command, redact(command))
    }

    @Test
    fun headersTokensAndUrls() {
        assertFalse(redact("--add-headers \"Authorization: Bearer abcdefghijklmnop\"").contains("abcdefghijklmnop"))
        assertFalse(redact("--add-headers \"Cookie: SID=abc; HSID=def\"").contains("SID=abc"))
        assertFalse(redact("https://alice:pa55@example.com/video").contains("pa55"))
        assertFalse(redact("https://cdn.example.com/v.mp4?token=abc123&sig=deadbeef&x=1").contains("abc123"))
        assertTrue(redact("https://cdn.example.com/v.mp4?token=abc123&x=1").contains("x=1"))
        assertFalse(redact("key sk-proj-abcdefghijklmnopqrstuvwxyz").contains("abcdefghijklmnop"))
        assertFalse(redact("AIzaSyA1234567890abcdefghijklmnopqrstuv").contains("1234567890"))
    }

    @Test
    fun extraSecretsAreReplaced() {
        assertEquals("value $r here", SecretRedactor.redact("value my-custom-secret here", listOf("my-custom-secret")))
    }
}
