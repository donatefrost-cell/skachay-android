package ru.skachay.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import ru.skachay.app.download.MediaDownloadClient
import ru.skachay.app.download.AudioBitrate
import ru.skachay.app.download.AudioExport
import ru.skachay.app.download.DownloadFolderGrantRegistry
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
import ru.skachay.app.download.YouTubeDirectTransfer
import ru.skachay.app.download.YouTubeFormatSelector
import ru.skachay.app.download.YouTubePlaylistLink
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
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
import ru.skachay.app.diagnostics.TransferStats
import ru.skachay.app.diagnostics.TransferSnapshot
import ru.skachay.app.diagnostics.YtDlpProgress
import ru.skachay.app.diagnostics.formatBytes
import ru.skachay.app.diagnostics.formatDuration
import ru.skachay.app.diagnostics.formatSpeed
import ru.skachay.app.diagnostics.diagnosticError
import ru.skachay.app.diagnostics.providerFor
import ru.skachay.app.choice.PreviewStore
import ru.skachay.app.choice.SocialPreviewStore
import ru.skachay.app.choice.SocialSelection
import ru.skachay.app.notifications.DownloadNotificationId
import ru.skachay.app.notifications.DownloadNotificationSnapshot
import ru.skachay.app.notifications.DownloadNotificationState
import okhttp3.OkHttpClient
import okhttp3.Request

private class SocialPreviewUnavailable : IOException()
private class WifiOnlyRequiredException : IOException()

class DownloadService : Service() {
    private val scheduler = Executors.newSingleThreadExecutor()
    private val executor = ThreadPoolExecutor(2, 4, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    private val downloader = MediaDownloadClient.downloader
    private val instagram = InstagramResolver()
    private val tiktok = TikTokResolver()
    private val activeUrls = ConcurrentHashMap.newKeySet<String>()
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val notificationCenter by lazy {
        DownloadNotificationCenter(this, notifications, ::groupSummaryNotification, GROUP_SUMMARY_ID)
    }
    private val thumbnailClient by lazy {
        OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    }
    private val notificationLargeIcon by lazy {
        Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFF534AB7.toInt())
            getDrawable(R.drawable.ic_launcher_foreground)?.apply {
                setBounds(0, 0, 108, 108)
                draw(canvas)
            }
        }
    }
    private val lock = Any()
    private var running = 0
    private var latestStartId = 0

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Загрузки", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shared = SharedUrl.firstIn(intent?.getStringExtra(EXTRA_URL))
        if (shared == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val url = shared
        val id = notificationIdFor(intent, url)
        val overrideQuality = Quality.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_QUALITY) }
        val audioExport = AudioExport.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_AUDIO_EXPORT) } ?: AudioExport.SOURCE
        val audioBitrate = AudioBitrate.entries.firstOrNull { it.kilobits == intent?.getIntExtra(EXTRA_AUDIO_BITRATE, 192) } ?: AudioBitrate.K192
        val subtitleLanguage = intent?.getStringExtra(EXTRA_SUBTITLE_LANGUAGE)
            ?.takeIf { Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*").matches(it) }
        val subtitleAutomatic = intent?.getBooleanExtra(EXTRA_SUBTITLE_AUTOMATIC, false) ?: false
        val embedSubtitles = intent?.getBooleanExtra(EXTRA_EMBED_SUBTITLES, false) ?: false
        val embedThumbnail = intent?.getBooleanExtra(EXTRA_EMBED_THUMBNAIL, false) ?: false
        val allowPlaylist = (intent?.getBooleanExtra(EXTRA_ALLOW_PLAYLIST, false) ?: false) && YouTubePlaylistLink.isPurePlaylist(url)
        val playlistAudioOnly = allowPlaylist && (intent?.getBooleanExtra(EXTRA_PLAYLIST_AUDIO_ONLY, false) ?: false)
        val downloadFolderUri = intent?.getStringExtra(EXTRA_DOWNLOAD_FOLDER_URI)
        val overrideDownloadFolder = intent?.getBooleanExtra(EXTRA_OVERRIDE_DOWNLOAD_FOLDER, false) ?: false
        val folderGrantLeased = intent?.getBooleanExtra(EXTRA_FOLDER_GRANT_LEASED, false) ?: false
        val fileName = SavedFileName.clean(intent?.getStringExtra(EXTRA_FILE_NAME))
        if (!notificationCenter.start(id, progressNotification(null, url, id, overrideQuality, fileName))) {
            return START_NOT_STICKY
        }
        synchronized(lock) {
            running++
            latestStartId = startId
        }
        val formatId = intent?.getStringExtra(EXTRA_FORMAT_ID)?.takeIf { Regex("[A-Za-z0-9._-]{1,80}").matches(it) }
        val videoOnly = intent?.getBooleanExtra(EXTRA_VIDEO_ONLY, false) ?: false
        val usePreview = intent?.getBooleanExtra(EXTRA_USE_PREVIEW, false) ?: false
        val socialPreviewId = intent?.getStringExtra(EXTRA_SOCIAL_PREVIEW_ID)
        val selectedItems = intent?.getIntArrayExtra(EXTRA_SELECTED_ITEMS)
        val folderLeaseAtStart = if (!folderGrantLeased && downloadFolderUri == null && !overrideDownloadFolder) {
            DownloadFolderGrantRegistry.acquireSelected()
        } else null
        scheduler.execute {
            var leasedFolderUri = if (folderGrantLeased) downloadFolderUri else folderLeaseAtStart
            try {
                val settings = runBlocking { SettingsRepository.forContext(this@DownloadService).settings.first() }
                executor.corePoolSize = settings.parallelDownloads
                val existingFolderLease = leasedFolderUri
                val requestedFolderUri = when {
                    existingFolderLease != null -> {
                        DownloadFolderGrantRegistry.initialize(this@DownloadService, settings.downloadFolderUri)
                        if (overrideDownloadFolder) downloadFolderUri else downloadFolderUri ?: existingFolderLease
                    }
                    overrideDownloadFolder && downloadFolderUri == null -> {
                        DownloadFolderGrantRegistry.initialize(this@DownloadService, settings.downloadFolderUri)
                        null
                    }
                    else -> DownloadFolderGrantRegistry.initializeAndAcquire(
                        this@DownloadService,
                        settings.downloadFolderUri,
                        preferredUri = downloadFolderUri,
                    ).also { leasedFolderUri = it }
                }
                val folderUriToRelease = leasedFolderUri
                executor.execute {
                    try {
                        download(url, id, settings.copy(quality = overrideQuality ?: settings.qualityForUrl(url)), usePreview, formatId, videoOnly,
                            socialPreviewId, selectedItems, fileName, audioExport, audioBitrate, subtitleLanguage, subtitleAutomatic, embedSubtitles,
                            embedThumbnail, allowPlaylist, playlistAudioOnly, requestedFolderUri, true)
                    } finally {
                        DownloadFolderGrantRegistry.release(this@DownloadService, folderUriToRelease)
                        finishJob()
                    }
                }
                leasedFolderUri = null
            } catch (error: Exception) {
                DownloadFolderGrantRegistry.release(this@DownloadService, leasedFolderUri)
                Log.w("Skachay", "Не удалось начать загрузку: ${error.javaClass.simpleName}")
                val provider = providerFor(url)
                val job = DiagnosticsStore.begin(this, provider)
                DiagnosticsStore.finish(this, provider, job, DiagnosticStatus.FAILED, emptyMap(), diagnosticError(provider, error))
                notifySafely(id, errorNotification(url, id, overrideQuality, usePreview, formatId, videoOnly, socialPreviewId, selectedItems, fileName,
                    audioExport = audioExport, audioBitrate = audioBitrate, subtitleLanguage = subtitleLanguage,
                    subtitleAutomatic = subtitleAutomatic, embedSubtitles = embedSubtitles, embedThumbnail = embedThumbnail,
                    allowPlaylist = allowPlaylist, playlistAudioOnly = playlistAudioOnly,
                    reason = diagnosticError(provider, error)))
                finishJob()
            }
        }
        return START_NOT_STICKY
    }

    private fun finishJob() {
        synchronized(lock) {
            running--
            if (running == 0) {
                stopSelfResult(latestStartId)
            }
        }
    }

    private fun download(
        url: String, id: Int, settings: AppSettings, usePreview: Boolean, formatId: String?, videoOnly: Boolean,
        socialPreviewId: String?, selectedItems: IntArray?, fileName: String?, audioExport: AudioExport,
        audioBitrate: AudioBitrate, subtitleLanguage: String?, subtitleAutomatic: Boolean, embedSubtitles: Boolean, embedThumbnail: Boolean,
        allowPlaylist: Boolean, playlistAudioOnly: Boolean, downloadFolderUri: String?, overrideDownloadFolder: Boolean,
    ) {
        val requestedFolderUri = if (overrideDownloadFolder) downloadFolderUri else downloadFolderUri ?: settings.downloadFolderUri
        val baseKey = InstagramLink.canonical(url) ?: TikTokLink.postId(url)?.let { "tiktok:$it" } ?: url
        val socialRequested = socialPreviewId != null || selectedItems != null
        val socialPreview = if (socialPreviewId != null && selectedItems != null) {
            try { SocialPreviewStore.load(this, url, socialPreviewId) } catch (_: IOException) { null }
        } else null
        val chosenItems = if (socialPreview != null && selectedItems != null) SocialSelection.items(socialPreview.items, selectedItems) else null
        val baseHistoryKey = when {
            allowPlaylist -> "$baseKey::playlist:${if (playlistAudioOnly) "audio" else "video"}"
            socialPreview != null && chosenItems != null && selectedItems != null -> SocialSelection.historyKey(baseKey, selectedItems, socialPreview.items.size)
            socialRequested -> "$baseKey::expired-selection"
            formatId != null -> "$baseKey::format:$formatId:$videoOnly"
            else -> baseKey
        }
        val optionSuffix = buildString {
            if (audioExport != AudioExport.SOURCE) append("::audio:${audioExport.name}:${audioBitrate.kilobits}")
            if (subtitleLanguage != null) append("::subs:$subtitleLanguage:${if (subtitleAutomatic) "auto" else "manual"}:${if (embedSubtitles) "embedded" else "file"}")
            if (settings.quality != Quality.AUTO && formatId == null && !socialRequested) append("::quality:${settings.quality.name}")
            requestedFolderUri?.let { append("::folder:${it.hashCode().toUInt()}") }
        }
        val historyKey = baseHistoryKey + optionSuffix
        if (!activeUrls.add(historyKey)) {
            notificationCenter.discard(id)
            return
        }
        val provider = providerFor(url)
        val job = DiagnosticsStore.begin(this, provider)
        val timer = StageTimer(SystemClock::elapsedRealtime)
        val transferStats = TransferStats(SystemClock::elapsedRealtime)
        var directory: File? = null
        var mediaTitle: String? = null
        var thumbnailUrl: String? = null
        try {
            val outputFolderUri = resolveDownloadFolder(requestedFolderUri, null)
            if (settings.wifiOnly && !wifiAvailable()) throw WifiOnlyRequiredException()
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
                    val transfer = transferStats.snapshot()
                    val notification = progressNotification(percent, url, id, settings.quality, fileName, transfer, mediaTitle)
                    notifySafely(id, notification, transfer.averageBytesPerSecond)
                }
            }
            val detailedReport: (YtDlpProgress) -> Unit = { progress ->
                val sample = transferStats.update(progress.receivedBytes, progress.totalBytes)
                val percent = sample.totalBytes?.takeIf { it > 0 }
                    ?.let { (sample.bytesReceived * 100 / it).coerceIn(0, 100).toInt() } ?: progress.percent
                report(percent)
            }
            timer.switchTo(DownloadStage.EXTRACTION)
            val items = chosenItems ?: when {
                InstagramLink.supports(url) -> instagram.resolve(url)
                TikTokLink.supports(url) -> tiktok.resolve(url)
                else -> emptyList()
            }
            val saved = if (items.isNotEmpty()) {
                timer.switchTo(DownloadStage.TRANSFER)
                transferStats.start()
                saveResolvedItems(items, directory, settings.connections, settings.retries, timer, report, fileName, transferStats, outputFolderUri)
            } else {
                if (TikTokLink.isPhoto(url)) throw IOException("Photo post unavailable")
                var audioOnly = false
                if (InstagramLink.supports(url) || TikTokLink.supports(url)) {
                    throw IOException("Не удалось получить прямую ссылку на файл. Проверьте публикацию и повторите попытку.")
                }
                var playlistFiles: List<File>? = null
                val file = if (SourceType.isYouTube(url)) {
                    if (allowPlaylist) {
                        timer.record(DownloadStage.RUNTIME_START, YtDlpRuntime.startupDurationMs() ?: 0L)
                        transferStats.start()
                        playlistFiles = YtDlpRuntime.downloadPlaylist(
                            context = this,
                            url = url,
                            directory = directory,
                            quality = settings.quality,
                            onProgress = report,
                            onStage = timer::switchTo,
                            retries = settings.retries,
                            audioExport = audioExport,
                            audioBitrate = audioBitrate,
                            audioOnly = playlistAudioOnly,
                            subtitleLanguage = subtitleLanguage,
                            subtitleAutomatic = subtitleAutomatic,
                            embedSubtitles = embedSubtitles,
                            onDetailedProgress = detailedReport,
                            videoBitrateLimit = settings.videoBitrateLimit,
                            embedThumbnail = embedThumbnail,
                        )
                        mediaTitle = "Плейлист · ${playlistFiles!!.size} файлов"
                        playlistFiles!!.first()
                    } else {
                    val extractionStarted = SystemClock.elapsedRealtime()
                    val cached = if (usePreview || formatId != null) {
                        PreviewStore.load(this, url, forDownload = true) ?: PreviewStore.getOrFetch(this, url)
                    } else PreviewStore.getOrFetch(this, url)
                    mediaTitle = cached.preview.title.take(100)
                    thumbnailUrl = cached.preview.thumbnailUrl
                    report(null)
                    val extractionElapsed = (SystemClock.elapsedRealtime() - extractionStarted).coerceAtLeast(0)
                    timer.record(DownloadStage.EXTRACTION, PreviewStore.extractionDuration(url) ?: extractionElapsed)
                    timer.record(DownloadStage.RUNTIME_START, YtDlpRuntime.startupDurationMs() ?: 0L)
                    val selected = formatId?.let { wanted -> cached.preview.formats.firstOrNull { it.id == wanted } ?: throw IOException("Selected format unavailable") }
                    audioOnly = selected != null && !selected.hasVideo && selected.hasAudio
                    val streams = if (audioExport == AudioExport.SOURCE && subtitleLanguage == null && !embedThumbnail) {
                        if (selected != null) YouTubeFormatSelector.selected(cached.preview, selected, videoOnly)
                        else YouTubeFormatSelector.automatic(cached.preview, settings.quality, settings.videoBitrateLimit)
                    } else null
                    if (streams != null) {
                        transferStats.start()
                        try {
                            YouTubeDirectTransfer.download(
                                context = this,
                                streams = streams,
                                directory = directory,
                                connections = settings.connections,
                                retries = settings.retries,
                                onStage = timer::switchTo,
                                onProgress = { received, total ->
                                    val sample = transferStats.update(received, total)
                                    val percent = sample.totalBytes?.takeIf { it > 0 }
                                        ?.let { (sample.bytesReceived * 100 / it).coerceIn(0, 100).toInt() }
                                    report(percent)
                                },
                                onFirstByte = { transferStats.markFirstByte() },
                            )
                        } catch (error: IOException) {
                            if (Thread.currentThread().isInterrupted) throw error
                            directory.listFiles()?.filter { it.name.startsWith("youtube-") || it.name == "video.mp4" }?.forEach(File::delete)
                            transferStats.start()
                            YtDlpRuntime.download(this, url, directory, settings.quality, report, timer::switchTo, cached.file, selected,
                                videoOnly, settings.retries, audioExport, audioBitrate, subtitleLanguage, subtitleAutomatic, embedSubtitles,
                                onDetailedProgress = detailedReport, videoBitrateLimit = settings.videoBitrateLimit, embedThumbnail = embedThumbnail)
                        }
                    } else {
                        transferStats.start()
                        YtDlpRuntime.download(this, url, directory, settings.quality, report, timer::switchTo, cached.file, selected,
                            videoOnly, settings.retries, audioExport, audioBitrate, subtitleLanguage, subtitleAutomatic, embedSubtitles,
                            onDetailedProgress = detailedReport, videoBitrateLimit = settings.videoBitrateLimit, embedThumbnail = embedThumbnail)
                    }
                    }
                } else {
                    val direct = File(directory, "direct.tmp")
                    timer.switchTo(DownloadStage.TRANSFER)
                    transferStats.start()
                    downloader.download(url, direct, connections = settings.connections, retries = settings.retries) { received, total ->
                        transferStats.update(received, total)
                        report(total?.takeIf { it > 0 }?.let { (received * 100 / it).coerceIn(0, 100).toInt() })
                    }
                    if (detect(direct, url) == null) {
                        direct.delete()
                        transferStats.start()
                        YtDlpRuntime.download(this, url, directory, settings.quality, report, timer::switchTo,
                            retries = settings.retries, onDetailedProgress = detailedReport, embedThumbnail = embedThumbnail)
                    } else direct
                }
                timer.switchTo(DownloadStage.SAVING)
                val output = mutableListOf<Pair<Uri, String>>()
                try {
                    if (playlistFiles != null) {
                        val writer = MediaStoreWriter(this)
                        playlistFiles!!.forEachIndexed { index, playlistFile ->
                            val playlistMime = detect(playlistFile, playlistFile.name)
                                ?: throw IOException("Not a supported playlist media file")
                            val itemName = fileName ?: playlistFile.nameWithoutExtension
                            output += writer.save(playlistFile, playlistMime, itemName, if (fileName != null) index + 1 else null, outputFolderUri) to playlistMime
                            val subtitles = subtitleLanguage?.let { language ->
                                directory.listFiles()?.filter { sidecar ->
                                    sidecar.isFile && sidecar.name.startsWith("${playlistFile.nameWithoutExtension}.$language.") &&
                                        sidecar.extension.equals("srt", true)
                                }.orEmpty()
                            }.orEmpty()
                            subtitles.forEach { subtitle ->
                                val subtitleName = fileName?.let { "$it.$subtitleLanguage" } ?: "${playlistFile.nameWithoutExtension}.$subtitleLanguage"
                                output += writer.save(subtitle, "application/x-subrip", subtitleName, if (fileName != null) index + 1 else null, outputFolderUri) to "application/x-subrip"
                            }
                        }
                    } else {
                        val mime = detect(file, if (file.name == "direct.tmp") url else file.name, audioOnly)
                            ?: throw IOException("Not a supported media file")
                        if (InstagramLink.isVideoPost(url) && !mime.startsWith("video/")) throw IOException("Expected video")
                        if (TikTokLink.supports(url) && mime.startsWith("audio/")) throw IOException("Photos or video unavailable")
                        output += MediaStoreWriter(this).save(file, mime, fileName, folderUri = outputFolderUri) to mime
                        val subtitleFiles = subtitleLanguage?.let { language ->
                            directory.listFiles()?.filter { it.isFile && it.name.startsWith("video.$language.") && it.extension.equals("srt", true) }.orEmpty()
                        }.orEmpty()
                        subtitleFiles.forEach { subtitle ->
                            val subtitleName = "${fileName ?: file.nameWithoutExtension}.$subtitleLanguage"
                            output += MediaStoreWriter(this).save(subtitle, "application/x-subrip", subtitleName, folderUri = outputFolderUri) to "application/x-subrip"
                        }
                    }
                } catch (error: Exception) {
                    output.forEach { (uri, _) -> deleteSavedUri(uri) }
                    throw error
                }
                output
            }
            val (uri, mime) = saved.first()
            history.edit().putString(historyKey, uri.toString()).apply()
            getSharedPreferences("download_files", MODE_PRIVATE).edit().apply {
                saved.forEach { (itemUri, _) -> putString(itemUri.toString(), url) }
            }.apply()
            transferStats.snapshot().firstByteMillis?.let { timer.record(DownloadStage.FIRST_BYTE, it) }
            val durations = timer.finish()
            DiagnosticsStore.finish(this, provider, job, DiagnosticStatus.FINISHED, durations,
                transfer = transferStats.snapshot(), vpnActive = vpnActive())
            val thumbnail = loadNotificationThumbnail(thumbnailUrl)
            val resultMessage = if (mediaTitle?.startsWith("Плейлист") == true) "Все файлы добавлены в галерею" else "Готово · нажмите, чтобы открыть"
            notifySafely(id, resultNotification(mediaTitle ?: "Готово", resultMessage, uri, mime, url, id, settings.quality, fileName, thumbnail))
        } catch (error: Exception) {
            Log.w("Skachay", "Загрузка ${provider.label}: ${error.javaClass.simpleName}")
            transferStats.snapshot().firstByteMillis?.let { timer.record(DownloadStage.FIRST_BYTE, it) }
            val friendlyError = if (error is WifiOnlyRequiredException) "Подключитесь к Wi-Fi и повторите загрузку." else diagnosticError(provider, error)
            DiagnosticsStore.finish(this, provider, job, DiagnosticStatus.FAILED, timer.finish(), friendlyError,
                transfer = transferStats.snapshot(), vpnActive = vpnActive())
            notifySafely(id, errorNotification(url, id, settings.quality, usePreview, formatId, videoOnly, socialPreviewId, selectedItems, fileName,
                error is SocialPreviewUnavailable, audioExport, audioBitrate, subtitleLanguage, subtitleAutomatic, embedSubtitles,
                embedThumbnail, allowPlaylist, playlistAudioOnly,
                reason = if (error is SocialPreviewUnavailable) null else friendlyError))
        } finally {
            directory?.deleteRecursively()
            activeUrls.remove(historyKey)
        }
    }

    private fun saveResolvedItems(items: List<ResolvedMedia>, directory: File, connections: Int, retries: Int, timer: StageTimer, report: (Int?) -> Unit, fileName: String?, transferStats: TransferStats, folderUri: Uri?): List<Pair<Uri, String>> {
        val files = items.mapIndexed { index, item ->
            val file = File(directory, "item-$index.tmp")
            downloader.download(item.url, file, connections = connections, headers = item.headers, retries = retries) { received, total ->
                val sample = transferStats.update(received, total)
                val itemPercent = total?.takeIf { it > 0 }?.let { (received * 100 / it).coerceIn(0, 100).toInt() }
                report(itemPercent?.let { (index * 100 + it) / items.size })
            }
            val mime = detect(file, item.url, audioOnly = item.type == ResolvedMedia.Kind.AUDIO) ?: throw IOException("Not a supported media file")
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
                saved += MediaStoreWriter(this).save(file, mime, fileName, if (files.size > 1) index + 1 else null, folderUri) to mime
            }
        } catch (error: Exception) {
            saved.forEach { (uri, _) -> deleteSavedUri(uri) }
            throw error
        }
        return saved
    }

    private fun detect(file: File, url: String, audioOnly: Boolean = false): String? {
        val header = ByteArray(32)
        val length = file.inputStream().use { it.read(header).coerceAtLeast(0) }
        return MediaType.detect(header.copyOf(length), url, audioOnly)
    }

    private fun resolveDownloadFolder(intentUri: String?, settingsUri: String?): Uri? {
        val value = intentUri ?: settingsUri ?: return null
        val uri = try { Uri.parse(value) } catch (_: Exception) { throw IOException("Invalid save folder") }
        if (uri.scheme != "content") throw IOException("Invalid save folder")
        val hasWritePermission = contentResolver.persistedUriPermissions.any { grant -> grant.uri == uri && grant.isWritePermission }
        if (!hasWritePermission) throw IOException("Selected save folder permission expired")
        return uri
    }

    private fun deleteSavedUri(uri: Uri) {
        try {
            if (android.provider.DocumentsContract.isDocumentUri(this, uri)) {
                android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
            } else {
                contentResolver.delete(uri, null, null)
            }
        } catch (_: Exception) { }
    }

    private fun exists(uri: Uri): Boolean = try {
        contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: java.io.FileNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun progressNotification(
        percent: Int?, url: String, id: Int, quality: Quality?, fileName: String?,
        transfer: TransferSnapshot? = null, title: String? = null,
    ): Notification {
        val text = buildList {
            percent?.let { add("$it%") }
            transfer?.averageBytesPerSecond?.takeIf { it > 0 }?.let { add(formatSpeed(it)) }
            if (transfer?.bytesReceived != null && transfer.totalBytes != null) {
                add("${formatBytes(transfer.bytesReceived)} из ${formatBytes(transfer.totalBytes)}")
            }
            transfer?.remainingMillis?.let { add("ещё ${formatDuration(it)}") }
        }.joinToString(" · ")
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setLargeIcon(notificationLargeIcon)
            .setContentTitle(title ?: Uri.parse(url).host?.removePrefix("www.") ?: "Скачай")
            .setContentText(text.ifBlank { "Подключаюсь к источнику…" })
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .setColor(0xFF534AB7.toInt())
        addChoiceAction(builder, url, id, quality, fileName)
        return builder.build()
    }

    private fun resultNotification(
        title: String, message: String, uri: Uri?, mime: String?, url: String, id: Int,
        quality: Quality?, fileName: String?, thumbnail: Bitmap? = null,
    ): Notification {
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setLargeIcon(thumbnail ?: notificationLargeIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            .setColor(0xFF534AB7.toInt())
            .setAutoCancel(true)
        thumbnail?.let { builder.setStyle(Notification.BigPictureStyle().bigPicture(it).bigLargeIcon(notificationLargeIcon)) }
        if (uri != null && mime != null) {
            val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(this, uri.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(pending)
            builder.addAction(Notification.Action.Builder(null, "Открыть", pending).build())
            val share = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            share.clipData = android.content.ClipData.newUri(contentResolver, "Скачай", uri)
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val chooser = Intent.createChooser(share, "Поделиться файлом").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val sharePending = PendingIntent.getActivity(this, id + SHARE_REQUEST_OFFSET, chooser,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, "Поделиться", sharePending).build())
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

    private fun errorNotification(
        url: String, id: Int, quality: Quality?, usePreview: Boolean, formatId: String?, videoOnly: Boolean,
        socialPreviewId: String?, selectedItems: IntArray?, fileName: String?, chooseAgain: Boolean = false,
        audioExport: AudioExport = AudioExport.SOURCE, audioBitrate: AudioBitrate = AudioBitrate.K192,
        subtitleLanguage: String? = null, subtitleAutomatic: Boolean = false, embedSubtitles: Boolean = false,
        embedThumbnail: Boolean = false,
        allowPlaylist: Boolean = false, playlistAudioOnly: Boolean = false,
        reason: String? = null,
    ): Notification {
        val retry = Intent(this, DownloadService::class.java).putExtra(EXTRA_URL, url)
        quality?.let { retry.putExtra(EXTRA_QUALITY, it.name) }
        retry.putExtra(EXTRA_USE_PREVIEW, usePreview).putExtra(EXTRA_VIDEO_ONLY, videoOnly)
        formatId?.let { retry.putExtra(EXTRA_FORMAT_ID, it) }
        socialPreviewId?.let { retry.putExtra(EXTRA_SOCIAL_PREVIEW_ID, it) }
        selectedItems?.let { retry.putExtra(EXTRA_SELECTED_ITEMS, it) }
        fileName?.let { retry.putExtra(EXTRA_FILE_NAME, it) }
        retry.putExtra(EXTRA_AUDIO_EXPORT, audioExport.name)
        retry.putExtra(EXTRA_AUDIO_BITRATE, audioBitrate.kilobits)
        subtitleLanguage?.let { retry.putExtra(EXTRA_SUBTITLE_LANGUAGE, it) }
        retry.putExtra(EXTRA_SUBTITLE_AUTOMATIC, subtitleAutomatic)
        retry.putExtra(EXTRA_EMBED_SUBTITLES, embedSubtitles)
        retry.putExtra(EXTRA_EMBED_THUMBNAIL, embedThumbnail)
        retry.putExtra(EXTRA_ALLOW_PLAYLIST, allowPlaylist)
        retry.putExtra(EXTRA_PLAYLIST_AUDIO_ONLY, playlistAudioOnly)
        retry.putExtra(EXTRA_OVERRIDE_DOWNLOAD_FOLDER, false)
        val pending = if (chooseAgain) choicePendingIntent(url, id, quality, fileName)
            else PendingIntent.getForegroundService(this, id, retry, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message = reason ?: when {
            chooseAgain -> "Сведения о выбранных файлах устарели. Откройте выбор и отметьте нужные файлы заново."
            InstagramLink.supports(url) -> "Публикация может быть закрыта или удалена. Проверьте ссылку; возможно, Instagram изменился."
            TikTokLink.supports(url) -> "Публикация может быть закрыта или недоступна. Проверьте ссылку и попробуйте ещё раз."
            else -> "Не получилось скачать. Проверьте, что файл доступен, и попробуйте ещё раз."
        }
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setLargeIcon(notificationLargeIcon)
            .setContentTitle("Не получилось скачать")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            .setColor(0xFF534AB7.toInt())
            .addAction(Notification.Action.Builder(null, if (chooseAgain) "Выбрать заново" else "Повторить", pending).build())
            .setAutoCancel(true)
        if (!chooseAgain) addChoiceAction(builder, url, id, quality, fileName)
        return builder.build()
    }

    private fun notifySafely(id: Int, notification: Notification, speedBytesPerSecond: Long? = null) {
        notificationCenter.publish(id, notification, speedBytesPerSecond)
    }

    private fun notificationIdFor(intent: Intent?, url: String): Int {
        val identity = buildString {
            append(url.length).append(':').append(url)
            intent?.extras?.let { extras ->
                extras.keySet().sorted().forEach { key ->
                    val value = when (val extra = extras.get(key)) {
                        is IntArray -> extra.joinToString(",")
                        is LongArray -> extra.joinToString(",")
                        is BooleanArray -> extra.joinToString(",")
                        else -> extra.toString()
                    }
                    append('|').append(key.length).append(':').append(key)
                        .append(value.length).append(':').append(value)
                }
            }
        }
        return DownloadNotificationId.from(identity)
    }

    private fun groupSummaryNotification(activeCount: Int, totalCount: Int, speedBytesPerSecond: Long): Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setLargeIcon(notificationLargeIcon)
            .setContentTitle("Скачай")
            .setContentText(buildString {
                append("Скачивание: $activeCount из $totalCount")
                if (speedBytesPerSecond > 0) append(" · ").append(formatSpeed(speedBytesPerSecond))
            })
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            .setColor(0xFF534AB7.toInt())
            .setOnlyAlertOnce(true)
            .build()

    private fun loadNotificationThumbnail(url: String?): Bitmap? {
        if (url == null) return null
        return try {
            val request = Request.Builder().url(url).header("User-Agent", "Skachay/Android").build()
            thumbnailClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body.contentLength() !in 0..2_000_000) return null
                val bytes = response.body.bytes()
                if (bytes.size > 2_000_000) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096) return null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 384) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun vpnActive(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    private fun wifiAvailable(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.any { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
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
        const val EXTRA_AUDIO_EXPORT = "audio_export"
        const val EXTRA_AUDIO_BITRATE = "audio_bitrate"
        const val EXTRA_SUBTITLE_LANGUAGE = "subtitle_language"
        const val EXTRA_SUBTITLE_AUTOMATIC = "subtitle_automatic"
        const val EXTRA_EMBED_SUBTITLES = "embed_subtitles"
        const val EXTRA_EMBED_THUMBNAIL = "embed_thumbnail"
        const val EXTRA_ALLOW_PLAYLIST = "allow_playlist"
        const val EXTRA_PLAYLIST_AUDIO_ONLY = "playlist_audio_only"
        const val EXTRA_DOWNLOAD_FOLDER_URI = "download_folder_uri"
        const val EXTRA_PLAYLIST_QUEUE = "playlist_queue"
        const val EXTRA_OVERRIDE_DOWNLOAD_FOLDER = "override_download_folder"
        const val EXTRA_FOLDER_GRANT_LEASED = "folder_grant_leased"
        private const val CHANNEL = "downloads"
        private const val GROUP_SUMMARY_ID = Int.MAX_VALUE
        private const val GROUP_KEY = "ru.skachay.app.DOWNLOADS"
        private const val SHARE_REQUEST_OFFSET = 10_000
    }
}

private class DownloadNotificationCenter(
    private val service: Service,
    private val manager: NotificationManager,
    private val summary: (Int, Int, Long) -> Notification,
    private val summaryId: Int,
) {
    private val state = DownloadNotificationState<Notification>()
    private val speeds = mutableMapOf<Int, Long>()

    @Synchronized
    fun start(id: Int, notification: Notification): Boolean {
        val snapshot = state.startIfAbsent(id, notification) ?: return false
        speeds[id] = 0
        render(snapshot)
        return true
    }

    @Synchronized
    fun publish(id: Int, notification: Notification, speedBytesPerSecond: Long? = null) {
        if (!state.contains(id)) {
            notifySafely(id, notification)
        } else if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            if (speedBytesPerSecond != null) speeds[id] = speedBytesPerSecond
            render(state.update(id, notification))
        } else {
            speeds.remove(id)
            render(state.complete(id, notification))
        }
    }

    @Synchronized
    fun discard(id: Int) {
        speeds.remove(id)
        render(state.discard(id))
        try {
            manager.cancel(id)
        } catch (_: SecurityException) {
            // Android can hide notifications if the user declined permission.
        }
    }

    private fun render(snapshot: DownloadNotificationSnapshot<Notification>) {
        if (snapshot.detachForeground) service.stopForeground(Service.STOP_FOREGROUND_DETACH)
        snapshot.completed?.let { (id, notification) -> notifySafely(id, notification) }
        snapshot.foregroundId?.let { id ->
            snapshot.active[id]?.let { notification ->
                try {
                    service.startForeground(id, notification)
                } catch (_: SecurityException) {
                    notifySafely(id, notification)
                }
            }
        }
        snapshot.active.forEach { (id, notification) ->
            if (id != snapshot.foregroundId) notifySafely(id, notification)
        }
        try {
            if (snapshot.showSummary) {
                val totalSpeed = snapshot.active.keys.sumOf { speeds[it] ?: 0L }
                manager.notify(summaryId, summary(snapshot.active.size, snapshot.totalCount, totalSpeed))
            } else manager.cancel(summaryId)
        } catch (_: SecurityException) {
            // Android can hide notifications if the user declined permission.
        }
    }

    private fun notifySafely(id: Int, notification: Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Android can hide notifications if the user declined permission.
        }
    }
}
