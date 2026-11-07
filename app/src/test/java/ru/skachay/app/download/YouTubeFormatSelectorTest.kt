package ru.skachay.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.choice.VideoPreview
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.VideoBitrateLimit

class YouTubeFormatSelectorTest {
    @Test
    fun autoSelectsH264UpTo720pAndM4aAudio() {
        val preview = VideoPreview("Видео", null, 60, null, listOf(
            format("1080", "mp4", "avc1.640028", null, 1080, null, null),
            format("720-vp9", "mp4", "vp09.00.31.08", null, 720, null, null),
            format("720-avc1", "mp4", "avc1.4d401f", null, 720, null, "https://cdn.example/video"),
            format("audio-webm", "webm", null, "opus", null, 256, "https://cdn.example/opus"),
            format("audio-m4a", "m4a", null, "mp4a.40.2", null, 128, "https://cdn.example/m4a"),
        ))
        val selected = YouTubeFormatSelector.automatic(preview, Quality.AUTO)!!
        assertEquals("720-avc1", selected.video?.id)
        assertEquals("audio-m4a", selected.audio?.id)
    }

    @Test
    fun automaticSelectionRequiresDirectUrls() {
        val preview = VideoPreview("Видео", null, 60, null, listOf(
            format("720", "mp4", "avc1", null, 720, null, null),
            format("audio", "m4a", null, "mp4a", null, 128, null),
        ))
        assertNull(YouTubeFormatSelector.automatic(preview, Quality.AUTO))
    }

    @Test
    fun automaticSelectionHonorsVideoBitrateLimitWithoutReencoding() {
        val preview = VideoPreview("Видео", null, 60, null, listOf(
            format("720-8000", "mp4", "avc1", null, 720, null, "https://cdn.example/large")
                .copy(videoBitrate = 8_000),
            format("720-3500", "mp4", "avc1", null, 720, null, "https://cdn.example/medium")
                .copy(videoBitrate = 3_500),
            format("audio", "m4a", null, "mp4a", null, 128, "https://cdn.example/audio"),
        ))
        val selected = YouTubeFormatSelector.automatic(preview, Quality.AUTO, VideoBitrateLimit.K5000)
        assertEquals("720-3500", selected?.video?.id)
        assertEquals("audio", selected?.audio?.id)
    }

    @Test
    fun estimatesLargeVideoFromTheSelectedBitrates() {
        val streams = YouTubeStreams(
            video = AvailableFormat("v", "mp4", "avc1", null, 1080, null, null, null, videoBitrate = 10_000),
            audio = AvailableFormat("a", "m4a", null, "mp4a", null, null, 128, null),
        )
        val bytes = YouTubeFormatSelector.estimatedBytes(streams, 600)!!
        org.junit.Assert.assertTrue(bytes > 500L * 1024 * 1024)
    }

    private fun format(id: String, ext: String, video: String?, audio: String?, height: Int?, audioBitrate: Int?, directUrl: String?) =
        AvailableFormat(id, ext, video, audio, height, null, audioBitrate, null, directUrl = directUrl)
}
