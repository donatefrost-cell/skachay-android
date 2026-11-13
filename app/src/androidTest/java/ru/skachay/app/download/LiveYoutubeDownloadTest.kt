package ru.skachay.app.download

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.skachay.app.DownloadService
import ru.skachay.app.MainActivity
import ru.skachay.app.choice.PreviewStore
import ru.skachay.app.diagnostics.DiagnosticsStore
import ru.skachay.app.diagnostics.DownloadProvider
import ru.skachay.app.queue.DownloadQueueDatabase
import ru.skachay.app.queue.DownloadQueueStatus
import ru.skachay.app.settings.Quality

@RunWith(AndroidJUnit4::class)
class LiveYoutubeDownloadTest {
    @Test
    fun downloadsAndSavesUserVideoAt720p() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            instrumentation.uiAutomation
                .executeShellCommand(
                    "pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}",
                ).close()
        }
        val main = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val url = "https://youtu.be/jMVf1Jsp3oo"
        val id = 19720613
        var result: Uri? = null
        try {
            val preview = PreviewStore.getOrFetch(context, url).preview
            val selected = YouTubeFormatSelector.preferredVideo(preview, Quality.P720)
            assertNotNull("No supported 720p format was returned", selected)
            assertEquals(720, requireNotNull(selected).height)
            context.startForegroundService(
                Intent(context, DownloadService::class.java)
                    .putExtra(DownloadService.EXTRA_URL, url)
                    .putExtra(DownloadService.EXTRA_NOTIFICATION_ID, id)
                    .putExtra(DownloadService.EXTRA_FORMAT_ID, selected.id)
                    .putExtra(DownloadService.EXTRA_USE_PREVIEW, true)
                    .putExtra(DownloadService.EXTRA_QUALITY, Quality.P720.name),
            )
            val dao = DownloadQueueDatabase.get(context).queue()
            val deadline = SystemClock.elapsedRealtime() + 8 * 60_000L
            while (SystemClock.elapsedRealtime() < deadline) {
                val entry = runBlocking { dao.get(id) }
                if (entry?.status == DownloadQueueStatus.COMPLETE) {
                    result = Uri.parse(requireNotNull(entry.resultUri))
                    break
                }
                if (entry?.status == DownloadQueueStatus.FAILED) {
                    val diagnostic = DiagnosticsStore.read(context).first { it.provider == DownloadProvider.YOUTUBE }
                    throw AssertionError("Download failed: ${entry.error}; ${diagnostic.technicalDetails}")
                }
                SystemClock.sleep(1000)
            }
            assertNotNull("Download did not complete before the deadline", result)
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, requireNotNull(result), null)
                val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString("mime").orEmpty() }
                assertTrue("Saved file lacks video: $tracks", tracks.any { it.startsWith("video/") })
                assertTrue("Saved file lacks audio: $tracks", tracks.any { it.startsWith("audio/") })
            } finally {
                extractor.release()
            }
        } finally {
            result?.let { context.contentResolver.delete(it, null, null) }
            main.finish()
        }
    }
}
