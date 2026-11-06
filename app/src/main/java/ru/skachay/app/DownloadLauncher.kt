package ru.skachay.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import ru.skachay.app.download.AudioBitrate
import ru.skachay.app.download.AudioExport
import ru.skachay.app.settings.Quality

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
) {
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
    try {
        context.startForegroundService(service)
        Toast.makeText(context, "Скачиваю… результат будет в уведомлении", Toast.LENGTH_LONG).show()
    } catch (_: IllegalStateException) {
        Toast.makeText(context, "Не удалось начать скачивание. Откройте приложение и попробуйте ещё раз", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Не удалось начать скачивание. Откройте приложение и попробуйте ещё раз", Toast.LENGTH_LONG).show()
    }
}
