package ru.skachay.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityPresetPresentationTest {
    @Test
    fun qualityPickerShowsAllOptionsInRequiredOrder() {
        assertEquals(
            listOf("Авто", "144p", "240p", "360p", "480p", "720p", "1080p", "1440p", "4K", "Макс"),
            QualityPresetPresentation.options.map { it.label },
        )
    }

    @Test
    fun selectedQualityHasShortReadableSummary() {
        assertEquals("Выбрано: 1440p", QualityPresetPresentation.summary(Quality.P1440))
        assertEquals("Выбрано: 4K", QualityPresetPresentation.summary(Quality.P2160))
        assertEquals("Выбрано: Авто", QualityPresetPresentation.summary(Quality.AUTO))
    }

    @Test
    fun legacyBitrateStopsMapToNearestVisibleStop() {
        assertEquals(VideoBitrateLimit.K2000, VideoBitrateLimit.fromStoredName("K2500"))
        assertEquals(VideoBitrateLimit.K4000, VideoBitrateLimit.fromStoredName("K5000"))
        assertEquals(VideoBitrateLimit.K16000, VideoBitrateLimit.fromStoredName("K12000"))
        assertEquals(VideoBitrateLimit.ANY, VideoBitrateLimit.fromStoredName(null))
    }

    @Test
    fun bitrateStopsShowTheRequiredCaptionsInAscendingOrder() {
        assertEquals(
            listOf("1", "2", "4", "8", "16 Мбит/с", "Макс"),
            VideoBitrateLimit.stops.map { it.stopCaption },
        )
    }
}
