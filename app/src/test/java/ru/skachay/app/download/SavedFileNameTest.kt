package ru.skachay.app.download

import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedFileNameTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun removesUnsafeCharactersAndUsesTheActualExtension() {
        assertEquals("_Семья_фото_ лето.jpg", SavedFileName.displayName("../Семья\\фото: лето.JPG", "jpg"))
        assertEquals("Видео.mp4.m4a", SavedFileName.displayName("Видео.mp4", "m4a"))
        assertEquals("Концерт.mp4", SavedFileName.displayName("Концерт.MP4", "mp4"))
        assertEquals("ab.jpg", SavedFileName.displayName("a\u202Eb", "jpg"))
        assertNull(SavedFileName.displayName(" ... ", "jpg"))
    }

    @Test
    fun numbersSeveralItemsAndLimitsUtf8LengthWithoutBreakingEmoji() {
        assertEquals("Семья (1).jpg", SavedFileName.displayName("Семья.jpg", "jpg", 1))
        assertEquals("Семья (2).mp4", SavedFileName.displayName("Семья", "mp4", 2))
        val name = SavedFileName.displayName("😀".repeat(100), "jpg")!!
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 164)
        assertEquals("😀".repeat(40) + ".jpg", name)
    }

    @Test
    fun reservesDistinctFilesWithoutOverwritingExistingContent() {
        val folder = temp.newFolder("gallery")
        val first = SavedFileName.reserve(folder, "Семья.jpg")
        first.writeText("important")
        val executor = Executors.newFixedThreadPool(6)
        try {
            val files = (1..6).map { executor.submit<java.io.File> { SavedFileName.reserve(folder, "Семья.jpg") } }.map { it.get() }
            assertEquals(6, files.map { it.name }.toSet().size)
            assertTrue(files.none { it == first })
            assertEquals("important", first.readText())
        } finally {
            executor.shutdownNow()
        }
    }
}
