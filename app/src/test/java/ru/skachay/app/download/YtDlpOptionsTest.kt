package ru.skachay.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.skachay.app.settings.Quality
import ru.skachay.app.choice.AvailableFormat

class YtDlpOptionsTest {
    @Test
    fun appliesConfiguredNetworkRetriesToVideoAndFragments() {
        val args = YtDlpOptions.request("https://youtu.be/example", "/cache/out.%(ext)s", retries = 3).buildCommand()
        for (option in listOf("--retries", "--fragment-retries", "--extractor-retries")) {
            val index = args.indexOf(option)
            assertTrue(index >= 0 && args[index + 1] == "3")
        }
    }

    @Test
    fun selectsCompatible720pWithoutEmbeddingOrReencoding() {
        val args = YtDlpOptions.request("https://youtu.be/example", "/cache/media.%(ext)s").buildCommand()
        assertTrue(args.contains("--no-playlist"))
        assertTrue(args.any { it.contains("height<=720") && it.contains("avc1") })
        assertTrue(args.contains("--concurrent-fragments") && args.contains("4"))
        assertTrue(args.contains("--merge-output-format") && args.contains("mp4"))
        assertFalse(args.contains("--recode-video"))
        assertFalse(args.contains("--embed-thumbnail"))
    }

    @Test
    fun permitsFormatsWithoutHeightMetadataForInstagram() {
        val args = YtDlpOptions.request("https://www.instagram.com/reel/abc/", "/cache/media.%(ext)s").buildCommand()
        val index = args.indexOf("--format")
        assertTrue(index >= 0)
        assertFalse(args[index + 1].contains("height<=720"))
    }

    @Test
    fun bestPresetAllowsCompatibleVideoUpTo1080p() {
        val args = YtDlpOptions.request("https://youtu.be/example", "/cache/media.%(ext)s", Quality.BEST).buildCommand()
        assertTrue(args.any { it.contains("height<=1080") && it.contains("avc1") })
        assertFalse(args.any { it.contains("height<=720") })
    }

    @Test
    fun usesTheExtractedInfoAndSelectedFormatWithoutExtractingTheUrlAgain() {
        val selected = AvailableFormat("137", "mp4", "avc1", null, 1080, 30, null, null)
        val args = YtDlpOptions.request("https://youtu.be/example", "/cache/video.%(ext)s", Quality.AUTO,
            infoJson = "/cache/info.json", selected = selected).buildCommand()
        assertTrue(args.contains("--load-info-json") && args.contains("/cache/info.json"))
        assertTrue(args.contains("137+bestaudio[ext=m4a]/137+bestaudio"))
        assertFalse(args.contains("https://youtu.be/example"))
    }

    @Test
    fun preservesAudioOnlyAndVideoWithoutAudioSelections() {
        val video = AvailableFormat("137", "mp4", "avc1", null, 1080, 30, null, null)
        val audio = AvailableFormat("140", "m4a", null, "mp4a", null, null, 128, null)
        val withoutAudio = YtDlpOptions.request("https://youtu.be/example", "/cache/out.%(ext)s", selected = video, videoOnly = true).buildCommand()
        val audioOnly = YtDlpOptions.request("https://youtu.be/example", "/cache/out.%(ext)s", selected = audio).buildCommand()
        assertTrue(withoutAudio.contains("137"))
        assertTrue(audioOnly.contains("140"))
        assertFalse(withoutAudio.any { it.contains("bestaudio") })
        assertFalse(audioOnly.any { it.contains("bestaudio") })
    }

    @Test
    fun remuxesSelectedWebmAudioIntoAnAndroidCompatibleAudioContainer() {
        val audio = AvailableFormat("251", "webm", null, "opus", null, null, 128, null)
        val args = YtDlpOptions.request("https://youtu.be/example", "/cache/out.%(ext)s", selected = audio).buildCommand()
        assertTrue(args.contains("--extract-audio"))
        assertTrue(args.indexOf("--audio-format").let { it >= 0 && args[it + 1] == "best" })
    }
}
