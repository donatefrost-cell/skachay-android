package ru.skachay.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ShareActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        val url = SharedUrl.firstIn(text)
        if (url == null) {
            Toast.makeText(this, "Не удалось найти ссылку", Toast.LENGTH_LONG).show()
        } else {
            try {
                startForegroundService(Intent(this, DownloadService::class.java).putExtra(DownloadService.EXTRA_URL, url))
                Toast.makeText(this, "Скачиваю… результат будет в уведомлении", Toast.LENGTH_LONG).show()
            } catch (_: IllegalStateException) {
                Toast.makeText(this, "Не удалось начать скачивание. Откройте приложение и попробуйте ещё раз", Toast.LENGTH_LONG).show()
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
