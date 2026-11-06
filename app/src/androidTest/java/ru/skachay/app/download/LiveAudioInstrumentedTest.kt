package ru.skachay.app.download

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.skachay.app.DownloadService
import ru.skachay.app.MainActivity
import ru.skachay.app.choice.PreviewStore

@RunWith(AndroidJUnit4::class)
class LiveAudioInstrumentedTest {
    @Test
    fun selectedWebmAudioIsSavedInMusic() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveAudio") == "1")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val url = "https://youtu.be/O8G5Mkzhe4s?audio_test=${UUID.randomUUID()}"
        val preview = PreviewStore.getOrFetch(context, url)
        val format = preview.preview.formats.firstOrNull { it.extension == "webm" && it.hasAudio && !it.hasVideo }
        assertNotNull("The sample should offer a WebM audio format", format)
        val name = "SkachayAudioTest_${UUID.randomUUID()}"
        val main = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var saved: Uri? = null
        try {
            context.startForegroundService(Intent(context, DownloadService::class.java)
                .putExtra(DownloadService.EXTRA_URL, url)
                .putExtra(DownloadService.EXTRA_FORMAT_ID, format!!.id)
                .putExtra(DownloadService.EXTRA_USE_PREVIEW, true)
                .putExtra(DownloadService.EXTRA_FILE_NAME, name))
            val deadline = SystemClock.elapsedRealtime() + 180_000
            while (SystemClock.elapsedRealtime() < deadline && saved == null) {
                context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.IS_PENDING),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf("$name.ogg"), null)?.use { cursor ->
                    if (cursor.moveToFirst() && cursor.getLong(1) > 0 && cursor.getInt(2) == 0) {
                        saved = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
                    }
                }
                if (saved == null) SystemClock.sleep(500)
            }
            assertNotNull("The selected audio was not saved to MediaStore", saved)
            val uri = saved!!
            assertEquals("audio/ogg", context.contentResolver.getType(uri))
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0).startsWith("Music/Скачай/"))
            }
            val header = ByteArray(4)
            context.contentResolver.openInputStream(uri)!!.use { assertEquals(4, it.read(header)) }
            assertEquals("audio/ogg", MediaType.detect(header, "saved.ogg", audioOnly = true))
        } finally {
            saved?.let { context.contentResolver.delete(it, null, null) }
            main.finish()
        }
    }
}
