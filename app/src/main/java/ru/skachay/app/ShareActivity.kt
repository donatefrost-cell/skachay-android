package ru.skachay.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.skachay.app.settings.SettingsRepository
import ru.skachay.app.settings.ShareMode

class ShareActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
            finishWithoutAnimation()
        } else {
            scope.launch {
                val settings = SettingsRepository.forContext(this@ShareActivity).settings.first()
                if (settings.shareMode == ShareMode.CHOOSE) {
                    startActivity(Intent(this@ShareActivity, ChoiceActivity::class.java)
                        .putExtra(DownloadService.EXTRA_URL, url)
                        .putExtra(DownloadService.EXTRA_QUALITY, settings.quality.name))
                } else {
                    startDownload(this@ShareActivity, url)
                }
                finishWithoutAnimation()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
