package ru.skachay.app.download

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameTemplateTest {
    @Test
    fun expandsTitleSourceDateAndPlaylistIndex() {
        assertEquals(
            "поездка_YouTube_2026-11-09_2",
            FileNameTemplate.resolve(
                "{title}_{source}_{date}_{index}",
                "поездка",
                "https://youtu.be/abc123",
                "YouTube",
                index = 2,
                date = "2026-11-09",
            ),
        )
    }

    @Test
    fun unsafeCharactersAreCleanedAndBlankTemplateFallsBackToDefaultName() {
        assertEquals("video_name", FileNameTemplate.resolve("{title}", "video/name", "https://youtu.be/id", "YouTube", date = "2026-11-09"))
        assertEquals(null, FileNameTemplate.resolve("  ", "video", "https://youtu.be/id", "YouTube", date = "2026-11-09"))
    }
}
