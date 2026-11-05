package ru.skachay.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import ru.skachay.app.download.DirectDownloader
import ru.skachay.app.download.MediaStoreWriter
import ru.skachay.app.download.MediaType
import ru.skachay.app.download.InstagramLink
import ru.skachay.app.download.InstagramResolver
import ru.skachay.app.download.ResolvedMedia
import ru.skachay.app.download.SourceType
import ru.skachay.app.download.TikTokLink
import ru.skachay.app.download.TikTokResolver
import ru.skachay.app.download.YtDlpRuntime
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {
    private val executor = Executors.newFixedThreadPool(2)
    private val downloader = DirectDownloader()
    private val instagram = InstagramResolver()
    private val tiktok = TikTokResolver()
    private val activeUrls = ConcurrentHashMap.newKeySet<String>()
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val nextId = AtomicInteger(100)
    private val lock = Any()
    private var running = 0
    private var latestStartId = 0

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Скачивание", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shared = SharedUrl.firstIn(intent?.getStringExtra(EXTRA_URL))
        if (shared == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val url = shared
        startForeground(FOREGROUND_ID, progressNotification(null))
        synchronized(lock) {
            running++
            latestStartId = startId
        }
        val id = nextId.incrementAndGet()
        executor.execute {
            try {
                download(url, id)
            } finally {
                synchronized(lock) {
                    running--
                    if (running == 0) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelfResult(latestStartId)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun download(url: String, id: Int) {
        val historyKey = InstagramLink.canonical(url) ?: TikTokLink.postId(url)?.let { "tiktok:$it" } ?: url
        if (!activeUrls.add(historyKey)) {
            notifySafely(id, resultNotification("Уже скачивается", "Подождите завершения", null, null))
            return
        }
        var directory: File? = null
        try {
            val history = getSharedPreferences("downloads", MODE_PRIVATE)
            val previous = history.getString(historyKey, null)?.let(Uri::parse)
            if (previous != null && exists(previous)) {
                val type = contentResolver.getType(previous) ?: "*/*"
                notifySafely(id, resultNotification("Уже скачано", "Нажмите, чтобы открыть", previous, type))
                return
            }
            directory = File(cacheDir, "job-${UUID.randomUUID()}")
            if (!directory.mkdir()) throw IOException("Could not create temporary folder")
            var lastUpdate = 0L
            val report: (Int?) -> Unit = { percent ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate >= 500 || percent == 100) {
                    lastUpdate = now
                    notifySafely(FOREGROUND_ID, progressNotification(percent))
                }
            }
            val items = when {
                InstagramLink.supports(url) -> instagram.resolve(url)
                TikTokLink.supports(url) -> tiktok.resolve(url)
                else -> emptyList()
            }
            val saved = if (items.isNotEmpty()) {
                saveResolvedItems(items, directory, report)
            } else {
                if (TikTokLink.isPhoto(url)) throw IOException("Photo post unavailable")
                val file = if (SourceType.isYouTube(url) || InstagramLink.supports(url) || TikTokLink.supports(url)) {
                    YtDlpRuntime.download(this, url, directory, report)
                } else {
                    val direct = File(directory, "direct.tmp")
                    try {
                        downloader.download(url, direct) { received, total ->
                            report(total?.takeIf { it > 0 }?.let { (received * 100 / it).coerceIn(0, 100).toInt() })
                        }
                        if (detect(direct, url) == null) throw IOException("Not a direct media link")
                        direct
                    } catch (_: IOException) {
                        direct.delete()
                        YtDlpRuntime.download(this, url, directory, report)
                    }
                }
                val mime = detect(file, if (file.name == "direct.tmp") url else file.name)
                    ?: throw IOException("Not a supported media file")
                if (InstagramLink.isVideoPost(url) && !mime.startsWith("video/")) {
                    throw IOException("Expected video")
                }
                if (TikTokLink.supports(url) && mime.startsWith("audio/")) throw IOException("Photos or video unavailable")
                listOf(MediaStoreWriter(this).save(file, mime) to mime)
            }
            val (uri, mime) = saved.first()
            history.edit().putString(historyKey, uri.toString()).apply()
            notifySafely(id, resultNotification("Готово", "Нажмите, чтобы открыть", uri, mime))
        } catch (error: Exception) {
            val message = error.message.orEmpty().replace(Regex("https?://\\S+"), "[ссылка]").take(300)
            Log.w("Skachay", "Загрузка: ${error.javaClass.simpleName}: $message")
            notifySafely(id, errorNotification(url, id))
        } finally {
            directory?.deleteRecursively()
            activeUrls.remove(historyKey)
        }
    }

    private fun saveResolvedItems(items: List<ResolvedMedia>, directory: File, report: (Int?) -> Unit): List<Pair<Uri, String>> {
        val files = items.mapIndexed { index, item ->
            val file = File(directory, "item-$index.tmp")
            downloader.download(item.url, file, headers = item.headers) { received, total ->
                val itemPercent = total?.takeIf { it > 0 }?.let { (received * 100 / it).coerceIn(0, 100).toInt() }
                report(itemPercent?.let { (index * 100 + it) / items.size })
            }
            val mime = detect(file, item.url) ?: throw IOException("Not a supported media file")
            val expected = when (item.type) {
                ResolvedMedia.Kind.IMAGE -> "image/"
                ResolvedMedia.Kind.VIDEO -> "video/"
                ResolvedMedia.Kind.AUDIO -> "audio/"
            }
            if (!mime.startsWith(expected)) throw IOException("Unexpected media type")
            file to mime
        }
        val saved = mutableListOf<Pair<Uri, String>>()
        try {
            files.forEach { (file, mime) -> saved += MediaStoreWriter(this).save(file, mime) to mime }
        } catch (error: Exception) {
            saved.forEach { (uri, _) -> contentResolver.delete(uri, null, null) }
            throw error
        }
        return saved
    }

    private fun detect(file: File, url: String): String? {
        val header = ByteArray(32)
        val length = file.inputStream().use { it.read(header).coerceAtLeast(0) }
        return MediaType.detect(header.copyOf(length), url)
    }

    private fun exists(uri: Uri): Boolean = try {
        contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: java.io.FileNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun progressNotification(percent: Int?): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Скачиваю…")
        .setContentText(if (percent == null) "Пожалуйста, подождите" else "$percent %")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, percent ?: 0, percent == null)
        .build()

    private fun resultNotification(title: String, message: String, uri: Uri?, mime: String?): Notification {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
        if (uri != null && mime != null) {
            val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(this, uri.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(pending)
        }
        return builder.build()
    }

    private fun errorNotification(url: String, id: Int): Notification {
        val retry = Intent(this, DownloadService::class.java).putExtra(EXTRA_URL, url)
        val pending = PendingIntent.getForegroundService(this, id, retry, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message = when {
            InstagramLink.supports(url) -> "Публикация может быть закрыта или удалена. Проверьте ссылку; возможно, Instagram изменился."
            TikTokLink.supports(url) -> "Публикация может быть закрыта или недоступна. Проверьте ссылку и попробуйте ещё раз."
            else -> "Не получилось скачать. Проверьте, что файл доступен, и попробуйте ещё раз."
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Не получилось скачать")
            .setContentText("Проверьте ссылку и попробуйте ещё раз")
            .setStyle(Notification.BigTextStyle().bigText(message))
            .addAction(Notification.Action.Builder(null, "Повторить", pending).build())
            .setAutoCancel(true)
            .build()
    }

    private fun notifySafely(id: Int, notification: Notification) {
        try {
            notifications.notify(id, notification)
        } catch (_: SecurityException) {
            // Android can hide notifications if the user declined permission.
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        executor.shutdownNow()
        stopSelf(startId)
    }

    companion object {
        const val EXTRA_URL = "url"
        private const val CHANNEL = "downloads"
        private const val FOREGROUND_ID = 1
    }
}
