package ru.skachay.app.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
