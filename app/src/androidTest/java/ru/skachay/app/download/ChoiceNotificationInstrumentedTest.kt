package ru.skachay.app.download

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.skachay.app.ChoiceActivity
import ru.skachay.app.DownloadService
import ru.skachay.app.MainActivity
import ru.skachay.app.choice.SocialPreviewCodec
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ChoiceNotificationInstrumentedTest {
    @Test
    fun youtubeNotificationOpensChoiceWithTheSameUrl() = checkChoice("https://youtu.be/notification-test", "Выбрать качество")

    @Test
    fun instagramNotificationOpensChoiceWithTheSameUrl() = checkChoice("https://www.instagram.com/p/NotARealPost/", "Выбрать файлы")

    @Test
    fun tiktokNotificationOpensChoiceWithTheSameUrl() = checkChoice("https://www.tiktok.com/@test/photo/1234567890", "Выбрать файлы")

    private fun checkChoice(
        url: String,
        actionLabel: String,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue("Notifications must be enabled on the test device", manager.areNotificationsEnabled())
        val main = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val file =
            File(context.cacheDir, "notification-test.jpg").apply {
                writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
            }
        val uri = MediaStoreWriter(context).save(file, "image/jpeg")
        val history = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val historyKey = InstagramLink.canonical(url) ?: TikTokLink.postId(url)?.let { "tiktok:$it" } ?: url
        val isYouTube = SourceType.isYouTube(url)
        val cached =
            File(context.cacheDir, "${if (isYouTube) "previews" else "social-previews"}/$digest.json").apply {
                parentFile?.mkdirs()
                if (isYouTube) {
                    writeText("""{"title":"Тест","formats":[]}""")
                } else {
                    writeText(
                        SocialPreviewCodec.encode(
                            digest,
                            UUID.randomUUID().toString(),
                            System.currentTimeMillis(),
                            listOf(ResolvedMedia("https://example.com/video.mp4", ResolvedMedia.Kind.VIDEO)),
                        ),
                    )
                }
            }
        var notificationId: Int? = null
        try {
            history.edit().putString(historyKey, uri.toString()).commit()
            val started = System.currentTimeMillis()
            context.startForegroundService(Intent(context, DownloadService::class.java).putExtra(DownloadService.EXTRA_URL, url))
            val deadline = SystemClock.elapsedRealtime() + 15_000
            var notification: Notification? = null
            while (SystemClock.elapsedRealtime() < deadline) {
                val entry =
                    manager.activeNotifications.firstOrNull {
                        it.postTime >= started && it.notification.extras
                            .getCharSequence(Notification.EXTRA_TITLE)
                            ?.toString() == "Уже скачано"
                    }
                if (entry != null) {
                    notification = entry.notification
                    notificationId = entry.id
                    break
                }
                Thread.sleep(100)
            }
            val completed = requireNotNull(notification) { "Expected the completed download notification" }
            val actionCandidate = completed.actions.orEmpty().firstOrNull { it.title.toString() == actionLabel }
            val action = requireNotNull(actionCandidate) { "Expected a choice action" }
            val monitor = instrumentation.addMonitor(ChoiceActivity::class.java.name, null, false)
            action.actionIntent.send()
            val choice = monitor.waitForActivityWithTimeout(10_000)
            val choiceActivity = requireNotNull(choice) { "Expected the choice screen" }
            assertEquals(url, choiceActivity.intent.getStringExtra(DownloadService.EXTRA_URL))
            choiceActivity.finish()
            instrumentation.removeMonitor(monitor)
        } finally {
            notificationId?.let(manager::cancel)
            history.edit().remove(historyKey).commit()
            context.contentResolver.delete(uri, null, null)
            cached.delete()
            file.delete()
            main.finish()
        }
    }
}
