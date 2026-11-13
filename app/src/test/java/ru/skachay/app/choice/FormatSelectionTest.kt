package ru.skachay.app.choice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.skachay.app.audioSourceSubtitle
import ru.skachay.app.download.AudioBitrate
import ru.skachay.app.download.AudioExport
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.VideoBitrateLimit
import ru.skachay.app.videoBitrateOptions

class FormatSelectionTest {
    private val formats =
        listOf(
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
        val selection =
            FormatSelection(
                resolution = 720,
                bitrateStop = VideoBitrateLimit.K4000,
                codec = "H.264",
                fps = 30,
                container = "mp4",
            )

        assertEquals(
            listOf("h264-360", "h264-720-muxed", "h264-720-30"),
            FormatSelectionResolver.candidates(formats, selection).map { it.id },
        )
    }

    @Test
    fun pinnedFormatWinsUntilAFilterChangesThenPinIsCleared() {
        val selection =
            FormatSelection(
                resolution = 720,
                codec = null,
                pinnedFormatId = "vp9-720",
            )

        assertEquals("vp9-720", FormatSelectionResolver.resolve(formats, selection)?.id)

        val changed =
            selection.withFilters(
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
    fun exactFormatSelectionUpdatesEveryVisibleVideoFilter() {
        val pinned = FormatSelection().pin(formats.first())

        assertEquals(720, pinned.resolution)
        assertEquals(Quality.P720, pinned.qualityPreset)
        assertEquals("H.264", pinned.codec)
        assertEquals(30, pinned.fps)
        assertEquals("mp4", pinned.container)
        assertEquals(2_500, pinned.customBitrateKbps)
        assertEquals("h264-720-30", FormatSelectionResolver.resolve(formats, pinned)?.id)
        assertNull(pinned.withFilters(720, VideoBitrateLimit.ANY, null, null, null, false).customBitrateKbps)
    }

    @Test
    fun customBitrateStopFiltersOutHigherBitrateVariants() {
        val selection = FormatSelection(resolution = 720, customBitrateKbps = 2_500)

        assertEquals(
            listOf("h264-360", "h264-720-muxed", "h264-720-30", "h264-720-webm"),
            FormatSelectionResolver.candidates(formats, selection).map { it.id },
        )
    }

    @Test
    fun audioModeOnlyReturnsAudioFormatsAndPrefersM4a() {
        val selection = FormatSelection(audio = true)

        assertEquals(listOf("m4a", "opus"), FormatSelectionResolver.candidates(formats, selection).map { it.id })
        assertEquals("m4a", FormatSelectionResolver.resolve(formats, selection)?.id)
    }

    @Test
    fun resolutionCapDoesNotFilterAudioFormats() {
        val selection = FormatSelection(resolution = 720, qualityPreset = Quality.P720, audio = true)

        assertEquals(listOf("m4a", "opus"), FormatSelectionResolver.candidates(formats, selection).map { it.id })
    }

    @Test
    fun audioExportSizesAreEstimatedForTheSelectedFormat() {
        val preview =
            VideoPreview(
                title = "Audio",
                author = null,
                durationSeconds = 60,
                thumbnailUrl = null,
                formats = formats,
            )

        assertEquals(1_440_000L, FormatSelectionResolver.estimateBytes(preview, FormatSelection(audio = true)))
        assertEquals(
            960_000L,
            FormatSelectionResolver.estimateBytes(
                preview,
                FormatSelection(audio = true, audioExport = AudioExport.MP3, audioBitrate = AudioBitrate.K128),
            ),
        )
    }

    @Test
    fun audioSourceSubtitleNamesTheActualContainerAndCodec() {
        assertEquals(
            "m4a · исходное качество",
            audioSourceSubtitle(audio("m4a", "m4a", "mp4a.40.2", 192)),
        )
        assertEquals(
            "OPUS · WEBM · исходный формат",
            audioSourceSubtitle(audio("opus", "webm", "opus", 160)),
        )
    }

    @Test
    fun qualityPresetDoesNotResolveAboveItsHeightLimit() {
        val selected =
            FormatSelectionResolver.resolve(
                formats,
                FormatSelection(qualityPreset = Quality.P1080),
            )

        assertTrue((selected?.height ?: Int.MAX_VALUE) <= 1080)
        assertEquals("h264-1080", selected?.id)
    }

    @Test
    fun qualityResolutionChoicesOnlyShowUniqueAvailableVideoHeights() {
        val withUnsupportedOnlyResolution =
            formats + video("vp9-1440-only", "webm", "vp09.00.40.08", 1440, 30, 5_000)

        assertEquals(listOf(360, 720, 1080), availableVideoResolutions(withUnsupportedOnlyResolution))
    }

    @Test
    fun resolutionChoicesFallBackToAvailableVideoWhenNoCompatibleFormatExists() {
        val formatsWithoutH264Mp4 = listOf(video("vp9-720", "webm", "vp09.00.40.08", 720, 30, 3_000))

        assertEquals(listOf(720), availableVideoResolutions(formatsWithoutH264Mp4))
    }

    @Test
    fun videoOnlyResolutionChoicesUseVideoOnlyFormats() {
        val formats =
            listOf(
                video("h264-720-muxed", "mp4", "avc1.4d401f", 720, 30, 2_500, audioCodec = "mp4a.40.2"),
                video("vp9-480-silent", "webm", "vp09.00.40.08", 480, 30, 1_200),
            )

        assertEquals(listOf(720), availableVideoResolutions(formats))
        assertEquals(listOf(480), availableVideoResolutions(formats, videoOnly = true))
    }

    @Test
    fun resolutionMapsToTheClosestQualityPresetWithoutRaisingTheCap() {
        assertEquals(Quality.P360, qualityPresetForResolution(360))
        assertEquals(Quality.P2160, qualityPresetForResolution(2160))
        assertEquals(Quality.MAX, qualityPresetForResolution(4320))
    }

    @Test
    fun maximumResolutionKeepsLowerFormatsWhenExactHeightIsUnavailable() {
        val selection = FormatSelection(resolution = 480, qualityPreset = Quality.P480)

        assertEquals(listOf("h264-360"), FormatSelectionResolver.candidates(formats, selection).map { it.id })
        assertEquals("h264-360", FormatSelectionResolver.resolve(formats, selection)?.id)
    }

    @Test
    fun qualityPresetDoesNotFallBackAboveItsMaximumWhenNoLowerFormatExists() {
        val onlyHighResolution = listOf(video("h264-1080-only", "mp4", "avc1.640028", 1080, 30, 4_000))

        assertNull(FormatSelectionResolver.resolve(onlyHighResolution, FormatSelection(qualityPreset = Quality.P480)))
    }

    @Test
    fun sliderAlwaysOffersThePinnedBitrateEvenWhenItIsNotAQuartile() {
        val preview =
            VideoPreview(
                title = "Video",
                author = null,
                durationSeconds = 60,
                thumbnailUrl = null,
                formats =
                    (500..5_000 step 500).map { bitrate ->
                        video("h264-$bitrate", "mp4", "avc1.4d401f", 720, 30, bitrate)
                    },
            )

        val stops = videoBitrateOptions(preview, FormatSelection(resolution = 720, customBitrateKbps = 4_500))

        assertTrue(stops.any { it.maxKbps == 4_500 && it.preset == null })
    }

    @Test
    fun videoOnlyRequiresSeparateVideoStream() {
        val selected =
            FormatSelectionResolver.candidates(
                formats,
                FormatSelection(resolution = 720, videoOnly = true),
            )

        assertTrue(selected.isNotEmpty())
        assertTrue(selected.all { it.hasVideo && !it.hasAudio })
    }

    @Test
    fun mediaSizeUsesOneRussianDecimalUnderTenMegabytes() {
        assertEquals("≈1,6\u00a0МБ", formatMediaSizeRu(1_600_000L))
        assertEquals("≈19\u00a0МБ", formatMediaSizeRu(19_000_000L))
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

    private fun audio(
        id: String,
        extension: String,
        codec: String,
        bitrate: Int,
    ) = AvailableFormat(
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
