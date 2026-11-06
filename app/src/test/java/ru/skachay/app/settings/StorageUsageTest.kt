package ru.skachay.app.settings

import org.junit.Assert.assertEquals
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
}
