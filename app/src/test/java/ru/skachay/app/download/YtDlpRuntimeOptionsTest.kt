package ru.skachay.app.download

import com.yausername.youtubedl_android.YoutubeDLRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpRuntimeOptionsTest {
    @Test
    fun youtubeRequestsEnableOfficialEjsAndKeepItsCache() {
        val request = YoutubeDLRequest("https://youtu.be/example")
        YtDlpOptions.addRuntimeSupport(request, "https://youtu.be/example", "/private/cache/yt-dlp")
        val args = request.buildCommand()

        assertTrue(args.contains("--cache-dir"))
        assertEquals("/private/cache/yt-dlp", args[args.indexOf("--cache-dir") + 1])
        assertTrue(args.contains("--remote-components"))
        assertEquals("ejs:github", args[args.indexOf("--remote-components") + 1])
        assertFalse(args.contains("--no-cache-dir"))
    }

    @Test
    fun nonYoutubeRequestsDoNotFetchYoutubeChallengeScripts() {
        val request = YoutubeDLRequest("https://www.instagram.com/reel/example/")
        YtDlpOptions.addRuntimeSupport(request, "https://www.instagram.com/reel/example/", "/private/cache/yt-dlp")
        val args = request.buildCommand()

        assertTrue(args.contains("--cache-dir"))
        assertFalse(args.contains("--remote-components"))
    }
}
