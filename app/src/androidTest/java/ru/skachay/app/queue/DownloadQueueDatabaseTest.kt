package ru.skachay.app.queue

import android.content.Intent
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.skachay.app.DownloadService

@RunWith(AndroidJUnit4::class)
class DownloadQueueDatabaseTest {
    private lateinit var database: DownloadQueueDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, DownloadQueueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun interruptedDownloadCanBeRetriedFromSavedRequest() = runBlocking {
        val dao = database.queue()
        dao.upsert(DownloadQueueEntry(
            id = 18,
            url = "https://youtu.be/abc123",
            title = "Видео",
            requestJson = "{\"schema\":1,\"extras\":{}}",
            status = DownloadQueueStatus.RUNNING,
            createdAt = 1,
            updatedAt = 2,
            percent = 42,
            bytesReceived = 4_200,
            totalBytes = 10_000,
        ))

        assertEquals(1, dao.markInterrupted(3))
        val failed = requireNotNull(dao.get(18))
        assertEquals(DownloadQueueStatus.FAILED, failed.status)
        assertEquals(42, failed.percent)
        assertTrue(failed.error?.contains("Повторить") == true)

        dao.restart(18, 4)
        val retried = requireNotNull(dao.get(18))
        assertEquals(DownloadQueueStatus.QUEUED, retried.status)
        assertNull(retried.percent)
        assertEquals(0L, retried.bytesReceived)
        assertEquals("{\"schema\":1,\"extras\":{}}", retried.requestJson)
    }

    @Test
    fun requestCodecRestoresArraysAndDownloadOptions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = Intent(context, DownloadService::class.java)
            .putExtra(DownloadService.EXTRA_URL, "https://youtu.be/abc123")
            .putExtra(DownloadService.EXTRA_QUALITY, "P720")
            .putExtra(DownloadService.EXTRA_SELECTED_ITEMS, intArrayOf(0, 2))
            .putExtra(DownloadService.EXTRA_EMBED_SUBTITLES, true)
            .putStringArrayListExtra(DownloadService.EXTRA_PLAYLIST_QUEUE, arrayListOf("first", "second"))

        val restored = DownloadRequestCodec.restore(context, DownloadRequestCodec.encode(request), 55)

        assertEquals("https://youtu.be/abc123", restored.getStringExtra(DownloadService.EXTRA_URL))
        assertEquals("P720", restored.getStringExtra(DownloadService.EXTRA_QUALITY))
        assertTrue(restored.getIntArrayExtra(DownloadService.EXTRA_SELECTED_ITEMS)!!.contentEquals(intArrayOf(0, 2)))
        assertTrue(restored.getBooleanExtra(DownloadService.EXTRA_EMBED_SUBTITLES, false))
        assertEquals(arrayListOf("first", "second"), restored.getStringArrayListExtra(DownloadService.EXTRA_PLAYLIST_QUEUE))
        assertEquals(55, restored.getIntExtra(DownloadService.EXTRA_NOTIFICATION_ID, 0))
    }
}
