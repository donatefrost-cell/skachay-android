package ru.skachay.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import ru.skachay.app.download.AudioBitrate
import ru.skachay.app.download.AudioExport
import ru.skachay.app.download.SourceType
import ru.skachay.app.download.YouTubePlaylistLink
import ru.skachay.app.settings.AppSettings
import ru.skachay.app.settings.DownloadPlatform
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.qualityFor

internal fun AppSettings.qualityForUrl(url: String): Quality = qualityFor(when {
    SourceType.isYouTube(url) -> DownloadPlatform.YOUTUBE
    else -> DownloadPlatform.OTHER
})

internal fun startBatchDownloads(context: Context, urls: List<String>, settings: AppSettings) {
    val batch = urls.distinct().take(20)
    if (batch.isEmpty()) return
    try {
        batch.forEach { url ->
            val service = Intent(context, DownloadService::class.java).putExtra(DownloadService.EXTRA_URL, url)
            service.putExtra(DownloadService.EXTRA_QUALITY, settings.qualityForUrl(url).name)
            context.startForegroundService(service)
        }
        Toast.makeText(context, "Добавлено загрузок: ${batch.size}", Toast.LENGTH_LONG).show()
    } catch (_: IllegalStateException) {
        Toast.makeText(context, "Не удалось добавить список. Откройте приложение и попробуйте ещё раз", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Не удалось добавить список. Откройте приложение и попробуйте ещё раз", Toast.LENGTH_LONG).show()
    }
}

internal fun startDownload(
    context: Context,
    url: String,
    quality: Quality? = null,
    usePreview: Boolean = false,
    formatId: String? = null,
    videoOnly: Boolean = false,
    socialPreviewId: String? = null,
    selectedItems: IntArray? = null,
    fileName: String? = null,
    audioExport: AudioExport = AudioExport.SOURCE,
    audioBitrate: Int = AudioBitrate.K192.kilobits,
    subtitleLanguage: String? = null,
    subtitleAutomatic: Boolean = false,
    embedSubtitles: Boolean = false,
    embedThumbnail: Boolean = false,
    allowPlaylist: Boolean = false,
    playlistAudioOnly: Boolean = false,
) {
    if (YouTubePlaylistLink.isPurePlaylist(url) && !allowPlaylist) {
        val choice = Intent(context, ChoiceActivity::class.java).putExtra(DownloadService.EXTRA_URL, url)
        quality?.let { choice.putExtra(DownloadService.EXTRA_QUALITY, it.name) }
        fileName?.let { choice.putExtra(DownloadService.EXTRA_FILE_NAME, it) }
        if (context !is Activity) choice.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(choice)
        return
    }
    val service = Intent(context, DownloadService::class.java).putExtra(DownloadService.EXTRA_URL, url)
    quality?.let { service.putExtra(DownloadService.EXTRA_QUALITY, it.name) }
    service.putExtra(DownloadService.EXTRA_USE_PREVIEW, usePreview)
    formatId?.let { service.putExtra(DownloadService.EXTRA_FORMAT_ID, it) }
    service.putExtra(DownloadService.EXTRA_VIDEO_ONLY, videoOnly)
    socialPreviewId?.let { service.putExtra(DownloadService.EXTRA_SOCIAL_PREVIEW_ID, it) }
    selectedItems?.let { service.putExtra(DownloadService.EXTRA_SELECTED_ITEMS, it) }
    fileName?.let { service.putExtra(DownloadService.EXTRA_FILE_NAME, it) }
    service.putExtra(DownloadService.EXTRA_AUDIO_EXPORT, audioExport.name)
    service.putExtra(DownloadService.EXTRA_AUDIO_BITRATE, audioBitrate)
    subtitleLanguage?.let { service.putExtra(DownloadService.EXTRA_SUBTITLE_LANGUAGE, it) }
    service.putExtra(DownloadService.EXTRA_SUBTITLE_AUTOMATIC, subtitleAutomatic)
    service.putExtra(DownloadService.EXTRA_EMBED_SUBTITLES, embedSubtitles)
    service.putExtra(DownloadService.EXTRA_EMBED_THUMBNAIL, embedThumbnail)
    service.putExtra(DownloadService.EXTRA_ALLOW_PLAYLIST, allowPlaylist)
    service.putExtra(DownloadService.EXTRA_PLAYLIST_AUDIO_ONLY, playlistAudioOnly)
    try {
        context.startForegroundService(service)
        Toast.makeText(context, "Скачиваю… результат будет в уведомлении", Toast.LENGTH_LONG).show()
    } catch (_: IllegalStateException) {
        Toast.makeText(context, "Не удалось начать скачивание. Откройте приложение и попробуйте ещё раз", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Не удалось начать скачивание. Откройте приложение и попробуйте ещё раз", Toast.LENGTH_LONG).show()
    }
}
