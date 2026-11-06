package ru.skachay.app.download

import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreNameInstrumentedTest {
    @Test
    fun savingTwoFilesWithSameNameKeepsBothContents() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstBytes = byteArrayOf(1, 2, 3)
        val secondBytes = byteArrayOf(4, 5, 6)
        val first = File.createTempFile("name-test-", ".jpg", context.cacheDir).apply { writeBytes(firstBytes) }
        val second = File.createTempFile("name-test-", ".jpg", context.cacheDir).apply { writeBytes(secondBytes) }
        val suggestedName = "SkachayTest_${UUID.randomUUID()}"
        val writer = MediaStoreWriter(context)
        val firstUri = writer.save(first, "image/jpeg", suggestedName)
        var secondUri: android.net.Uri? = null
        try {
            val savedSecond = writer.save(second, "image/jpeg", suggestedName)
            secondUri = savedSecond
            fun displayName(uri: android.net.Uri): String = context.contentResolver.query(uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)!!.use { cursor ->
                assertTrue("Expected a MediaStore row", cursor.moveToFirst())
                cursor.getString(0)
            }
            assertEquals("$suggestedName.jpg", displayName(firstUri))
            assertNotEquals(displayName(firstUri), displayName(savedSecond))
            assertArrayEquals(firstBytes, context.contentResolver.openInputStream(firstUri)!!.use { it.readBytes() })
            assertArrayEquals(secondBytes, context.contentResolver.openInputStream(savedSecond)!!.use { it.readBytes() })
        } finally {
            context.contentResolver.delete(firstUri, null, null)
            secondUri?.let { context.contentResolver.delete(it, null, null) }
            first.delete()
            second.delete()
        }
    }
}
