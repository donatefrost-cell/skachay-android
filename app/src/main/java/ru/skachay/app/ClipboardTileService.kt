package ru.skachay.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.skachay.app.settings.SettingsRepository
import ru.skachay.app.settings.ShareMode

class ClipboardTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.label = getString(R.string.shortcut_download_clipboard)
            tile.state = if (clipboardUrl() == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val url = clipboardUrl()
            if (url == null) {
                Toast.makeText(this, "Скопируйте ссылку, затем нажмите ещё раз", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return@unlockAndRun
            }
            scope.launch {
                val settings = SettingsRepository.forContext(this@ClipboardTileService).settings.first()
                if (settings.shareMode == ShareMode.CHOOSE) {
                    startActivity(
                        Intent(this@ClipboardTileService, ChoiceActivity::class.java)
                            .putExtra(DownloadService.EXTRA_URL, url)
                            .putExtra(DownloadService.EXTRA_QUALITY, settings.qualityForUrl(url).name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } else {
                    startDownload(this@ClipboardTileService, url, settings.qualityForUrl(url))
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun clipboardUrl(): String? =
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.let(SharedUrl::firstIn)
        } catch (_: SecurityException) {
            null
        }
}
