package ru.skachay.app.download

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.skachay.app.DownloadService
import ru.skachay.app.MainActivity
import ru.skachay.app.notifications.DownloadNotificationId
import ru.skachay.app.settings.DownloadPlatform
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.SettingsRepository
import ru.skachay.app.settings.qualityFor
import java.io.File

@RunWith(AndroidJUnit4::class)
class DownloadNotificationCountInstrumentedTest {
    @Test
    fun twoSequentialDownloadsLeaveOneFinalNotificationPerRequest() {
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
        val manager = context.getSystemService(NotificationManager::class.java)
        val urls = listOf("https://youtu.be/notification-count-one", "https://youtu.be/notification-count-two")
        val ids = urls.map(::notificationId)
        val settings = runBlocking { SettingsRepository.forContext(context).settings.first() }
        val quality = settings.qualityFor(DownloadPlatform.YOUTUBE)
        val historyKeys =
            urls.map { url ->
                buildString {
                    append(url)
                    if (quality != Quality.AUTO) append("::quality:").append(quality.name)
                    settings.downloadFolderUri?.let { append("::folder:").append(it.hashCode().toUInt()) }
                }
            }
        val file =
            File(context.cacheDir, "notification-count.jpg").apply {
                writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
            }
        val uri = MediaStoreWriter(context).save(file, "image/jpeg")
        val history = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

        try {
            historyKeys.forEach { history.edit().putString(it, uri.toString()).commit() }
            urls.forEachIndexed { index, url ->
                context.startForegroundService(Intent(context, DownloadService::class.java).putExtra(DownloadService.EXTRA_URL, url))
                assertNotNull("Expected one final notification for request $index", waitForNotification(manager, ids[index]))
            }

            val matching = manager.activeNotifications.filter { it.id in ids }
            assertEquals("Each request must keep one notification under its stable ID", ids.toSet(), matching.map { it.id }.toSet())
            assertEquals("A request must not create multiple notification rows", ids.size, matching.size)
            val summaries =
                manager.activeNotifications.count {
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                }
            assertTrue("The app may show at most one group summary", summaries <= 1)
        } finally {
            ids.forEach(manager::cancel)
            manager.cancel(Int.MAX_VALUE)
            historyKeys.forEach { history.edit().remove(it).commit() }
            context.contentResolver.delete(uri, null, null)
            file.delete()
            main.finish()
        }
    }

    private fun waitForNotification(
        manager: NotificationManager,
        id: Int,
    ): Notification? {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            manager.activeNotifications
                .firstOrNull { entry ->
                    entry.id == id && entry.notification.extras
                        .getCharSequence(Notification.EXTRA_TITLE)
                        ?.toString() == "Уже скачано"
                }?.let { return it.notification }
            SystemClock.sleep(100)
        }
        return null
    }

    private fun notificationId(url: String): Int {
        val identity =
            buildString {
                append(url.length).append(':').append(url)
                append('|')
                    .append("url".length)
                    .append(':')
                    .append("url")
                    .append(url.length)
                    .append(':')
                    .append(url)
            }
        return DownloadNotificationId.from(identity)
    }
}
