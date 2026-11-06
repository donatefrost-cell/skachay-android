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
import ru.skachay.app.download.SavedFileName
import ru.skachay.app.download.SourceType
import ru.skachay.app.download.TikTokLink
import ru.skachay.app.download.TikTokResolver
import ru.skachay.app.download.YtDlpRuntime
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.skachay.app.settings.AppSettings
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.SettingsRepository
import ru.skachay.app.recipes.RecipeRegistry
import ru.skachay.app.diagnostics.DiagnosticStatus
import ru.skachay.app.diagnostics.DiagnosticsStore
import ru.skachay.app.diagnostics.DownloadStage
import ru.skachay.app.diagnostics.StageTimer
import ru.skachay.app.diagnostics.diagnosticError
import ru.skachay.app.diagnostics.providerFor
import ru.skachay.app.choice.PreviewStore
import ru.skachay.app.choice.SocialPreviewStore
import ru.skachay.app.choice.SocialSelection

private class SocialPreviewUnavailable : IOException()

class DownloadService : Service() {
    private val scheduler = Executors.newSingleThreadExecutor()
    private val executor = ThreadPoolExecutor(2, 4, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
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
        val id = nextId.incrementAndGet()
        val overrideQuality = Quality.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_QUALITY) }
        val fileName = SavedFileName.clean(intent?.getStringExtra(EXTRA_FILE_NAME))
        startForeground(FOREGROUND_ID, progressNotification(null, url, id, overrideQuality, fileName))
        synchronized(lock) {
            running++
            latestStartId = startId
        }
        val formatId = intent?.getStringExtra(EXTRA_FORMAT_ID)?.takeIf { Regex("[A-Za-z0-9._-]{1,80}").matches(it) }
        val videoOnly = intent?.getBooleanExtra(EXTRA_VIDEO_ONLY, false) ?: false
        val usePreview = intent?.getBooleanExtra(EXTRA_USE_PREVIEW, false) ?: false
        val socialPreviewId = intent?.getStringExtra(EXTRA_SOCIAL_PREVIEW_ID)
        val selectedItems = intent?.getIntArrayExtra(EXTRA_SELECTED_ITEMS)
        scheduler.execute {
            try {
                val settings = runBlocking { SettingsRepository.forContext(this@DownloadService).settings.first() }
                executor.corePoolSize = settings.parallelDownloads
                executor.execute {
                    try {
                        download(url, id, settings.copy(quality = overrideQuality ?: settings.quality), usePreview, formatId, videoOnly, socialPreviewId, selectedItems, fileName)
                    } finally {
                        finishJob()
                    }
                }
            } catch (error: Exception) {
                Log.w("Skachay", "Не удалось начать загрузку: ${error.javaClass.simpleName}")
                val provider = providerFor(url)
                val job = DiagnosticsStore.begin(this, provider)
                DiagnosticsStore.finish(this, provider, job, DiagnosticStatus.FAILED, emptyMap(), diagnosticError(provider, error))
                notifySafely(id, errorNotification(url, id, overrideQuality, usePreview, formatId, videoOnly, socialPreviewId, selectedItems, fileName))
                finishJob()
            }
        }
        return START_NOT_STICKY
    }

    private fun finishJob() {
        synchronized(lock) {
            running--
            if (running == 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(latestStartId)
            }
        }
    }

    private fun download(url: String, id: Int, settings: AppSettings, usePreview: Boolean, formatId: String?, videoOnly: Boolean, socialPreviewId: String?, selectedItems: IntArray?, fileName: String?) {
        val baseKey = InstagramLink.canonical(url) ?: TikTokLink.postId(url)?.let { "tiktok:$it" } ?: url
        val socialRequested = socialPreviewId != null || selectedItems != null
        val socialPreview = if (socialPreviewId != null && selectedItems != null) {
            try { SocialPreviewStore.load(this, url, socialPreviewId) } catch (_: IOException) { null }
        } else null
        val chosenItems = if (socialPreview != null && selectedItems != null) SocialSelection.items(socialPreview.items, selectedItems) else null
        val historyKey = when {
            socialPreview != null && chosenItems != null && selectedItems != null -> SocialSelection.historyKey(baseKey, selectedItems, socialPreview.items.size)
            socialRequested -> "$baseKey::expired-selection"
            formatId != null -> "$baseKey::format:$formatId:$videoOnly"
            else -> baseKey
        }
        if (!activeUrls.add(historyKey)) {
            notifySafely(id, resultNotification("Уже скачивается", "Подождите завершения", null, null, url, id, settings.quality, fileName))
            return
        }
        val provider = providerFor(url)
        val job = DiagnosticsStore.begin(this, provider)
        val timer = StageTimer(SystemClock::elapsedRealtime)
        var directory: File? = null
        try {
            if (socialRequested && chosenItems == null) throw SocialPreviewUnavailable()
            val history = getSharedPreferences("downloads", MODE_PRIVATE)
            val previous = history.getString(historyKey, null)?.let(Uri::parse)
            if (previous != null && exists(previous)) {
                val type = contentResolver.getType(previous) ?: "*/*"
                DiagnosticsStore.finish(this, provider, job, DiagnosticStatus.ALREADY, emptyMap())
                notifySafely(id, resultNotification("Уже скачано", "Нажмите, чтобы открыть", previous, type, url, id, settings.quality, fileName))
                return
            }
            directory = File(cacheDir, "job-${UUID.randomUUID()}")
            if (!directory.mkdir()) throw IOException("Could not create temporary folder")
            var lastUpdate = 0L
            val report: (Int?) -> Unit = { percent ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate >= 500 || percent == 100) {
                    lastUpdate = now
                    notifySafely(FOREGROUND_ID, progressNotification(percent, url, id, settings.quality, fileName))
                }
            }
            timer.switchTo(DownloadStage.EXTRACTION)
            val items = chosenItems ?: when {
                InstagramLink.supports(url) -> instagram.resolve(url)
                TikTokLink.supports(url) -> tiktok.resolve(url)
                else -> emptyList()
            }
            val saved = if (items.isNotEmpty()) {
                timer.switchTo(DownloadStage.TRANSFER)
                saveResolvedItems(items, directory, settings.connections, timer, report, fileName)
            } else {
                if (TikTokLink.isPhoto(url)) throw IOException("Photo post unavailable")
                val file = if (SourceType.isYouTube(url) || InstagramLink.supports(url) || TikTokLink.supports(url)) {
                    if (InstagramLink.supports(url) && !RecipeRegistry.current.instagram.ytDlpEnabled) throw IOException("Instagram fallback disabled")
                    if (TikTokLink.supports(url) && !RecipeRegistry.current.tiktok.ytDlpEnabled) throw IOException("TikTok fallback disabled")
                    val cached = if (SourceType.isYouTube(url) && (usePreview || formatId != null)) {
                        PreviewStore.load(this, url, forDownload = true) ?: PreviewStore.getOrFetch(this, url)
                    } else null
                    val selected = formatId?.let { wanted -> cached?.preview?.formats?.firstOrNull { it.id == wanted } ?: throw IOException("Selected format unavailable") }
                    YtDlpRuntime.download(this, url, directory, settings.quality, report, timer::switchTo, cached?.file, selected, videoOnly)
                } else {
                    val direct = File(directory, "direct.tmp")
                    timer.switchTo(DownloadStage.TRANSFER)
                    downloader.download(url, direct, connections = settings.connections) { received, total ->
                        report(total?.takeIf { it > 0 }?.let { (received * 100 / it).coerceIn(0, 100).toInt() })
                    }
                    if (detect(direct, url) == null) {
                        direct.delete()
                        YtDlpRuntime.download(this, url, directory, settings.quality, report, timer::switchTo)
                    } else direct
                }
                val mime = detect(file, if (file.name == "direct.tmp") url else file.name)
                    ?: throw IOException("Not a supported media file")
                if (InstagramLink.isVideoPost(url) && !mime.startsWith("video/")) {
                    throw IOException("Expected video")
                }
                if (TikTokLink.supports(url) && mime.startsWith("audio/")) throw IOException("Photos or video unavailable")
                timer.switchTo(DownloadStage.SAVING)
                listOf(MediaStoreWriter(this).save(file, mime, fileName) to mime)
            }
            val (uri, mime) = saved.first()
            history.edit().putString(historyKey, uri.toString()).apply()
            getSharedPreferences("download_files", MODE_PRIVATE).edit().apply {
                saved.forEach { (itemUri, _) -> putString(itemUri.toString(), url) }
            }.apply()
            DiagnosticsStore.finish(this, provider, job, DiagnosticStatus.FINISHED, timer.finish())
            notifySafely(id, resultNotification("Готово", "Нажмите, чтобы открыть", uri, mime, url, id, settings.quality, fileName))
        } catch (error: Exception) {
            Log.w("Skachay", "Загрузка ${provider.label}: ${error.javaClass.simpleName}")
            DiagnosticsStore.finish(this, provider, job, DiagnosticStatus.FAILED, timer.finish(), diagnosticError(provider, error))
            notifySafely(id, errorNotification(url, id, settings.quality, usePreview, formatId, videoOnly, socialPreviewId, selectedItems, fileName, error is SocialPreviewUnavailable))
        } finally {
            directory?.deleteRecursively()
            activeUrls.remove(historyKey)
        }
    }

    private fun saveResolvedItems(items: List<ResolvedMedia>, directory: File, connections: Int, timer: StageTimer, report: (Int?) -> Unit, fileName: String?): List<Pair<Uri, String>> {
        val files = items.mapIndexed { index, item ->
            val file = File(directory, "item-$index.tmp")
            downloader.download(item.url, file, connections = connections, headers = item.headers) { received, total ->
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
            timer.switchTo(DownloadStage.SAVING)
            files.forEachIndexed { index, (file, mime) ->
                saved += MediaStoreWriter(this).save(file, mime, fileName, if (files.size > 1) index + 1 else null) to mime
            }
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

    private fun progressNotification(percent: Int?, url: String, id: Int, quality: Quality?, fileName: String?): Notification {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Скачиваю…")
            .setContentText(if (percent == null) "Пожалуйста, подождите" else "$percent %")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
        addChoiceAction(builder, url, id, quality, fileName)
        return builder.build()
    }

    private fun resultNotification(title: String, message: String, uri: Uri?, mime: String?, url: String, id: Int, quality: Quality?, fileName: String?): Notification {
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
        addChoiceAction(builder, url, id, quality, fileName)
        return builder.build()
    }

    private fun choicePendingIntent(url: String, id: Int, quality: Quality?, fileName: String?): PendingIntent {
        val choice = Intent(this, ChoiceActivity::class.java).putExtra(EXTRA_URL, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        quality?.let { choice.putExtra(EXTRA_QUALITY, it.name) }
        fileName?.let { choice.putExtra(EXTRA_FILE_NAME, it) }
        return PendingIntent.getActivity(this, id, choice, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun addChoiceAction(builder: Notification.Builder, url: String, id: Int, quality: Quality?, fileName: String?) {
        val label = when {
            SourceType.isYouTube(url) -> "Выбрать качество"
            InstagramLink.supports(url) || TikTokLink.supports(url) -> "Выбрать файлы"
            else -> return
        }
        builder.addAction(Notification.Action.Builder(null, label, choicePendingIntent(url, id, quality, fileName)).build())
    }

    private fun errorNotification(url: String, id: Int, quality: Quality?, usePreview: Boolean, formatId: String?, videoOnly: Boolean, socialPreviewId: String?, selectedItems: IntArray?, fileName: String?, chooseAgain: Boolean = false): Notification {
        val retry = Intent(this, DownloadService::class.java).putExtra(EXTRA_URL, url)
        quality?.let { retry.putExtra(EXTRA_QUALITY, it.name) }
        retry.putExtra(EXTRA_USE_PREVIEW, usePreview).putExtra(EXTRA_VIDEO_ONLY, videoOnly)
        formatId?.let { retry.putExtra(EXTRA_FORMAT_ID, it) }
        socialPreviewId?.let { retry.putExtra(EXTRA_SOCIAL_PREVIEW_ID, it) }
        selectedItems?.let { retry.putExtra(EXTRA_SELECTED_ITEMS, it) }
        fileName?.let { retry.putExtra(EXTRA_FILE_NAME, it) }
        val pending = if (chooseAgain) choicePendingIntent(url, id, quality, fileName)
            else PendingIntent.getForegroundService(this, id, retry, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message = when {
            chooseAgain -> "Сведения о выбранных файлах устарели. Откройте выбор и отметьте нужные файлы заново."
            InstagramLink.supports(url) -> "Публикация может быть закрыта или удалена. Проверьте ссылку; возможно, Instagram изменился."
            TikTokLink.supports(url) -> "Публикация может быть закрыта или недоступна. Проверьте ссылку и попробуйте ещё раз."
            else -> "Не получилось скачать. Проверьте, что файл доступен, и попробуйте ещё раз."
        }
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Не получилось скачать")
            .setContentText("Проверьте ссылку и попробуйте ещё раз")
            .setStyle(Notification.BigTextStyle().bigText(message))
            .addAction(Notification.Action.Builder(null, if (chooseAgain) "Выбрать заново" else "Повторить", pending).build())
            .setAutoCancel(true)
        if (!chooseAgain) addChoiceAction(builder, url, id, quality, fileName)
        return builder.build()
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
        scheduler.shutdownNow()
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        scheduler.shutdownNow()
        executor.shutdownNow()
        stopSelf(startId)
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_USE_PREVIEW = "use_preview"
        const val EXTRA_FORMAT_ID = "format_id"
        const val EXTRA_VIDEO_ONLY = "video_only"
        const val EXTRA_SOCIAL_PREVIEW_ID = "social_preview_id"
        const val EXTRA_SELECTED_ITEMS = "selected_items"
        const val EXTRA_FILE_NAME = "file_name"
        private const val CHANNEL = "downloads"
        private const val FOREGROUND_ID = 1
    }
}
