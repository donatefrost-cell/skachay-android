package ru.skachay.app.choice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewLifetimeTest {
    @Test
    fun keepsThePreviewLongEnoughForAnAlreadyOpenedChoice() {
        val age = 20 * 60 * 1000L
        assertFalse(PreviewLifetime.isFresh(age))
        assertTrue(PreviewLifetime.isUsableForDownload(age))
        assertFalse(PreviewLifetime.isUsableForDownload(61 * 60 * 1000L))
        assertFalse(PreviewLifetime.isFresh(-1))
    }
}
