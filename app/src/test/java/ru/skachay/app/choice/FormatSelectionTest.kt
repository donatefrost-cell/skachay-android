package ru.skachay.app.choice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.VideoBitrateLimit

class FormatSelectionTest {
    private val formats = listOf(
        video("h264-720-30", "mp4", "avc1.4d401f", 720, 30, 2_500),
        video("h264-720-60", "mp4", "avc1.64001f", 720, 60, 4_500),
        video("h264-720-webm", "webm", "avc1.4d401f", 720, 30, 2_000),
        video("vp9-720", "webm", "vp09.00.40.08", 720, 30, 3_000),
        video("h264-1080", "mp4", "avc1.640028", 1080, 30, 5_000),
        video("h264-360", "mp4", "avc1.4d401e", 360, 30, 700),
        video("h264-720-muxed", "mp4", "avc1.4d401f", 720, 30, 2_200, audioCodec = "mp4a.40.2"),
        audio("m4a", "m4a", "mp4a.40.2", 192),
        audio("opus", "webm", "opus", 160),
    )

    @Test
    fun filtersFormatsByResolutionBitrateCodecFpsAndContainer() {
        val selection = FormatSelection(
            resolution = 720,
            bitrateStop = VideoBitrateLimit.K4000,
            codec = "H.264",
            fps = 30,
            container = "mp4",
        )

        assertEquals(
            listOf("h264-720-muxed", "h264-720-30"),
            FormatSelectionResolver.candidates(formats, selection).map { it.id },
        )
    }

    @Test
    fun pinnedFormatWinsUntilAFilterChangesThenPinIsCleared() {
        val selection = FormatSelection(
            resolution = 720,
            codec = null,
            pinnedFormatId = "vp9-720",
        )

        assertEquals("vp9-720", FormatSelectionResolver.resolve(formats, selection)?.id)

        val changed = selection.withFilters(
            resolution = 720,
            bitrateStop = VideoBitrateLimit.ANY,
            codec = "H.264",
            fps = null,
            container = null,
            audio = false,
        )

        assertNull(changed.pinnedFormatId)
        assertEquals("h264-720-30", FormatSelectionResolver.resolve(formats, changed)?.id)
    }

    @Test
    fun audioModeOnlyReturnsAudioFormatsAndPrefersM4a() {
        val selection = FormatSelection(audio = true)

        assertEquals(listOf("m4a", "opus"), FormatSelectionResolver.candidates(formats, selection).map { it.id })
        assertEquals("m4a", FormatSelectionResolver.resolve(formats, selection)?.id)
    }

    @Test
    fun qualityPresetDoesNotResolveAboveItsHeightLimit() {
        val selected = FormatSelectionResolver.resolve(
            formats,
            FormatSelection(qualityPreset = Quality.P1080),
        )

        assertTrue((selected?.height ?: Int.MAX_VALUE) <= 1080)
        assertEquals("h264-1080", selected?.id)
    }

    @Test
    fun videoOnlyRequiresSeparateVideoStream() {
        val selected = FormatSelectionResolver.candidates(
            formats,
            FormatSelection(resolution = 720, videoOnly = true),
        )

        assertTrue(selected.isNotEmpty())
        assertTrue(selected.all { it.hasVideo && !it.hasAudio })
    }

    @Test
    fun mediaSizeUsesOneRussianDecimalUnderTenMegabytes() {
        assertEquals("≈1,6 МБ", formatMediaSizeRu(1_600_000L))
        assertEquals("≈19 МБ", formatMediaSizeRu(19_000_000L))
    }

    private fun video(
        id: String,
        extension: String,
        codec: String,
        height: Int,
        fps: Int,
        bitrate: Int,
        audioCodec: String? = null,
    ) = AvailableFormat(
        id = id,
        extension = extension,
        videoCodec = codec,
        audioCodec = audioCodec,
        height = height,
        fps = fps,
        audioBitrate = null,
        sizeBytes = null,
        videoBitrate = bitrate,
    )

    private fun audio(id: String, extension: String, codec: String, bitrate: Int) = AvailableFormat(
        id = id,
        extension = extension,
        videoCodec = null,
        audioCodec = codec,
        height = null,
        fps = null,
        audioBitrate = bitrate,
        sizeBytes = null,
    )
}
