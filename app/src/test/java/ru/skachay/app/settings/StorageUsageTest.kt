package ru.skachay.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageUsageTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun countsNestedFilesAndHandlesMissingDirectories() {
        val root = temp.newFolder("storage")
        root.resolve("first").writeBytes(ByteArray(11))
        root.resolve("nested").mkdir()
        root.resolve("nested/second").writeBytes(ByteArray(23))
        assertEquals(34L, StorageUsage.bytes(root))
        assertEquals(0L, StorageUsage.bytes(root.resolve("missing")))
    }

    @Test
    fun clearCacheRemovesDisposableEntriesAndKeepsActiveDownloadDirectories() {
        val cache = temp.newFolder("cache")
        val previews = cache.resolve("previews").apply { mkdir() }
        previews.resolve("metadata.json").writeText("preview")
        val activeDownload = cache.resolve("job-active").apply { mkdir() }
        activeDownload.resolve("partial.tmp").writeText("in progress")

        assertTrue(StorageUsage.clearCache(cache))
        assertFalse(previews.exists())
        assertTrue(activeDownload.resolve("partial.tmp").isFile)
    }
}
