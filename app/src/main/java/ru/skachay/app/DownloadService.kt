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
import ru.skachay.app.download.DirectDownloader
import ru.skachay.app.download.MediaStoreWriter
import ru.skachay.app.download.MediaType
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {
    private val executor = Executors.newFixedThreadPool(2)
    private val downloader = DirectDownloader()
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
        val url = SharedUrl.firstIn(intent?.getStringExtra(EXTRA_URL))
        if (url == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
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
        if (!activeUrls.add(url)) {
            notifySafely(id, resultNotification("Уже скачивается", "Подождите завершения", null, null))
            return
        }
        var file: File? = null
        try {
            val history = getSharedPreferences("downloads", MODE_PRIVATE)
            val previous = history.getString(url, null)?.let(Uri::parse)
            if (previous != null && exists(previous)) {
                val type = contentResolver.getType(previous) ?: "*/*"
                notifySafely(id, resultNotification("Уже скачано", "Нажмите, чтобы открыть", previous, type))
                return
            }
            file = File.createTempFile("media-", ".tmp", cacheDir)
            var lastUpdate = 0L
            downloader.download(url, file) { received, total ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate >= 500 || received == total) {
                    lastUpdate = now
                    val percent = total?.takeIf { it > 0 }?.let { (received * 100 / it).coerceIn(0, 100).toInt() }
                    notifySafely(FOREGROUND_ID, progressNotification(percent))
                }
            }
            val header = ByteArray(32)
            val length = file.inputStream().use { it.read(header).coerceAtLeast(0) }
            val mime = MediaType.detect(header.copyOf(length), url)
                ?: throw IllegalArgumentException("Not a supported media file")
            val uri = MediaStoreWriter(this).save(file, mime)
            history.edit().putString(url, uri.toString()).apply()
            notifySafely(id, resultNotification("Готово", "Нажмите, чтобы открыть", uri, mime))
        } catch (_: Exception) {
            notifySafely(id, errorNotification(url, id))
        } finally {
            file?.delete()
            activeUrls.remove(url)
        }
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
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Не получилось скачать")
            .setContentText("Проверьте ссылку и попробуйте ещё раз")
            .setStyle(Notification.BigTextStyle().bigText("Не получилось скачать. Проверьте, что файл доступен, и попробуйте ещё раз."))
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
