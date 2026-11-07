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
import ru.skachay.app.choice.PreviewStore
import ru.skachay.app.choice.SocialPreviewStore
import ru.skachay.app.download.SourceType
import ru.skachay.app.download.InstagramLink
import ru.skachay.app.download.TikTokLink
import ru.skachay.app.settings.SettingsRepository
import ru.skachay.app.settings.ShareMode

class ShareActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending = 0

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        accept(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
    }

    private fun accept(incoming: Intent?) {
        val text = when (incoming?.action) {
            Intent.ACTION_SEND -> incoming.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> incoming.dataString
            else -> null
        }
        val url = SharedUrl.firstIn(text)
        if (url == null) {
            Toast.makeText(this, "Не удалось найти ссылку", Toast.LENGTH_LONG).show()
            if (pending == 0) finishWithoutAnimation()
        } else {
            if (SourceType.isYouTube(url)) PreviewStore.prefetch(applicationContext, url)
            pending++
            scope.launch {
                try {
                    val settings = SettingsRepository.forContext(this@ShareActivity).settings.first()
                    if (settings.shareMode == ShareMode.CHOOSE) {
                        if (InstagramLink.supports(url) || TikTokLink.supports(url)) {
                            SocialPreviewStore.prefetch(applicationContext, url)
                        }
                        startActivity(Intent(this@ShareActivity, ChoiceActivity::class.java)
                            .putExtra(DownloadService.EXTRA_URL, url)
                            .putExtra(DownloadService.EXTRA_QUALITY, settings.quality.name))
                    } else {
                        startDownload(this@ShareActivity, url)
                    }
                } finally {
                    pending--
                    if (pending == 0) finishWithoutAnimation()
                }
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
