package ru.skachay.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import ru.skachay.app.settings.Quality

internal fun startDownload(context: Context, url: String, quality: Quality? = null) {
    val service = Intent(context, DownloadService::class.java).putExtra(DownloadService.EXTRA_URL, url)
    quality?.let { service.putExtra(DownloadService.EXTRA_QUALITY, it.name) }
    try {
        context.startForegroundService(service)
        Toast.makeText(context, "Скачиваю… результат будет в уведомлении", Toast.LENGTH_LONG).show()
    } catch (_: IllegalStateException) {
        Toast.makeText(context, "Не удалось начать скачивание. Откройте приложение и попробуйте ещё раз", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Не удалось начать скачивание. Откройте приложение и попробуйте ещё раз", Toast.LENGTH_LONG).show()
    }
}
