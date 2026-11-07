package ru.skachay.app.choice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.skachay.app.settings.Quality

class QualityChoiceModelTest {
    @Test
    fun simpleVideoCardsPreferCompatibleNormalBitrateFormatsAndDeduplicateTiers() {
        val preview = preview(
            video("720-high", 720, 30, 5_000, "avc1.64001f", "mp4", 5_000_000),
            video("720-normal", 720, 30, 2_500, "avc1.4d401f", "mp4", 2_500_000),
            video("360", 360, 30, 700, "avc1.4d401e", "mp4", 700_000),
            video("webm", 1080, 30, 4_000, "vp09.00.40.08", "webm", 4_000_000),
            audio("m4a", 128, 2_000_000),
        )

        val choices = QualityChoiceBuilder.videoCards(preview)

        assertEquals(listOf(QualityVideoTier.RECOMMENDED, QualityVideoTier.ECONOMICAL), choices.map { it.tier })
        assertEquals(listOf("720-normal", "360"), choices.map { it.video.id })
        assertEquals("m4a", choices.first().audio?.id)
        assertEquals(4_500_000L, choices.first().estimatedSizeBytes)
        assertTrue(choices.first().recommended)
    }

    @Test
    fun goodTierPrefersClosestResolutionAndFrameRateTo720pAt30() {
        val preview = preview(
            video("1080", 1080, 30, 4_000),
            video("720-60", 720, 60, 3_000),
            video("720-30", 720, 30, 2_500),
            video("480", 480, 30, 1_500),
            audio("m4a", 128, 1_000_000),
        )

        val good = QualityChoiceBuilder.videoCards(preview).first { it.tier == QualityVideoTier.RECOMMENDED }

        assertEquals("720-30", good.video.id)
        assertEquals(1, QualityChoiceBuilder.videoCards(preview).count { it.video.height == 720 })
    }

    @Test
    fun audioCardsOfferOriginalM4aAndEstimatedCompactMp3() {
        val preview = preview(
            video("720", 720, 30, 2_500),
            audio("m4a-128", 128, null),
            audio("m4a-192", 192, null),
        ).copy(durationSeconds = 100)

        val choices = QualityChoiceBuilder.audioCards(preview)

        assertEquals(listOf(QualityAudioKind.BEST_M4A, QualityAudioKind.COMPACT_MP3), choices.map { it.kind })
        assertEquals("Компактное · mp3 128 кбит/с", choices[1].kind.title)
        assertEquals("m4a-192", choices.first().source.id)
        assertEquals(1_600_000L, choices[1].estimatedSizeBytes)
    }

    @Test
    fun mediaSizeUsesRussianOneDecimalBelowTenMegabytes() {
        assertEquals("≈1,6 МБ", formatMediaSizeRu(1_600_000))
        assertEquals("≈19 МБ", formatMediaSizeRu(19_000_000))
    }

    @Test
    fun defaultsToRecommendedAndRestoresRememberedTierWhenEnabled() {
        val preview = preview(
            video("1080", 1080, 30, 4_000),
            video("720", 720, 30, 2_500),
            video("360", 360, 30, 700),
            audio("m4a", 128, 1_000_000),
        )
        val choices = QualityChoiceBuilder.videoCards(preview)

        assertEquals(QualityVideoTier.RECOMMENDED, QualityChoiceBuilder.initialVideoChoice(choices, Quality.AUTO, remember = false, lastTier = "MAXIMUM")?.tier)
        assertEquals(QualityVideoTier.MAXIMUM, QualityChoiceBuilder.initialVideoChoice(choices, Quality.AUTO, remember = true, lastTier = "MAXIMUM")?.tier)
        assertEquals(QualityVideoTier.ECONOMICAL, QualityChoiceBuilder.initialVideoChoice(choices, Quality.P360, remember = false, lastTier = null)?.tier)
    }

    @Test
    fun configuredHeightPresetDoesNotPreselectAResolutionAboveItsLimit() {
        val choices = QualityChoiceBuilder.videoCards(preview(
            video("2160", 2160, 30, 12_000),
            video("1080", 1080, 30, 5_000),
            video("720", 720, 30, 2_500),
            video("360", 360, 30, 700),
            audio("m4a", 128, 1_000_000),
        ))

        val choice = QualityChoiceBuilder.initialVideoChoice(choices, Quality.P1080, remember = false, lastTier = null)

        assertTrue((choice?.video?.height ?: Int.MAX_VALUE) <= 1080)
    }

    private fun preview(vararg formats: AvailableFormat) = VideoPreview("Видео", null, 60, null, formats.toList())

    private fun video(id: String, height: Int, fps: Int, bitrate: Int, codec: String = "avc1.4d401f", ext: String = "mp4", size: Long? = null) =
        AvailableFormat(id, ext, codec, null, height, fps, null, size, videoBitrate = bitrate)

    private fun audio(id: String, bitrate: Int, size: Long?) =
        AvailableFormat(id, "m4a", null, "mp4a.40.2", null, null, bitrate, size)
}
