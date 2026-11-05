package ru.skachay.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.skachay.app.settings.Quality

class YtDlpOptionsTest {
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
}
