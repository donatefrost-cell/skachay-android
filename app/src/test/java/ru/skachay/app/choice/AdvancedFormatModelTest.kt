package ru.skachay.app.choice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedFormatModelTest {
    private val formats =
        listOf(
            video("h264", "mp4", "avc1.64001f", 1080, 30, 4_700, 277_000_000),
            video("vp9", "webm", "vp09.00.40.08", 1080, 60, 6_000, 350_000_000),
            video("av1", "webm", "av01.0.08M.08", 2160, 30, 12_000, 900_000_000),
            audio("m4a", "m4a", "mp4a.40.2", 128, 5_000_000),
        )

    @Test
    fun filtersAreSpecificAndCompatibleFilterIncludesOnlyGallerySafeFormats() {
        assertEquals(listOf("h264", "m4a"), filterAdvancedFormats(formats, AdvancedFormatFilter.COMPATIBLE).map { it.id })
        assertEquals(listOf("h264"), filterAdvancedFormats(formats, AdvancedFormatFilter.H264).map { it.id })
        assertEquals(listOf("vp9"), filterAdvancedFormats(formats, AdvancedFormatFilter.VP9).map { it.id })
        assertEquals(listOf("av1"), filterAdvancedFormats(formats, AdvancedFormatFilter.AV1).map { it.id })
        assertEquals(listOf("h264", "vp9", "av1"), filterAdvancedFormats(formats, AdvancedFormatFilter.VIDEO_ONLY).map { it.id })
        assertEquals(formats, filterAdvancedFormats(formats, AdvancedFormatFilter.ALL))
    }

    @Test
    fun rowMetadataUsesNonBreakingUnitsAndMegabitsAboveOneThousand() {
        assertEquals("4,7\u00a0Мбит/с", formatAdvancedBitrateRu(4_700))
        assertEquals("960\u00a0кбит/с", formatAdvancedBitrateRu(960))
        assertEquals("277\u00a0МБ", formatAdvancedSizeRu(277_000_000))
        assertTrue(formatAdvancedSizeRu(null).contains("неизвест"))
    }

    private fun video(
        id: String,
        extension: String,
        codec: String,
        height: Int,
        fps: Int,
        bitrate: Int,
        bytes: Long,
    ) = AvailableFormat(id, extension, codec, null, height, fps, null, bytes, videoBitrate = bitrate)

    private fun audio(
        id: String,
        extension: String,
        codec: String,
        bitrate: Int,
        bytes: Long,
    ) = AvailableFormat(id, extension, null, codec, null, null, bitrate, bytes)
}
