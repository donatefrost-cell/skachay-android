package ru.skachay.app.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun UpdateBanner() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("updates", Context.MODE_PRIVATE) }
    var release by remember { mutableStateOf(ReleaseStore.available(context)) }
    var downloading by remember { mutableStateOf(false) }
    var installerUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> release = ReleaseStore.available(context) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val allowInstall = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = installerUri
        if (uri != null && context.packageManager.canRequestPackageInstalls()) openInstaller(context, uri)
        else message(context, "Чтобы установить обновление, разрешите установку для «Скачай»")
    }
    release?.let { update ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Доступно обновление", style = MaterialTheme.typography.titleLarge)
                Text("Версия ${update.tag.removePrefix("v")}")
                Button(enabled = !downloading, onClick = {
                    scope.launch {
                        downloading = true
                        try {
                            val uri = withContext(Dispatchers.IO) { UpdateDownloader(context.applicationContext).download(update) }
                            installerUri = uri
                            if (context.packageManager.canRequestPackageInstalls()) {
                                openInstaller(context, uri)
                            } else {
                                try {
                                    allowInstall.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                                } catch (_: ActivityNotFoundException) {
                                    message(context, "Откройте настройки телефона и разрешите установку для «Скачай»")
                                }
                            }
                        } catch (_: InvalidUpdateException) {
                            message(context, "Обновление не подошло к установленной версии. Проверьте подпись приложения")
                        } catch (_: IOException) {
                            message(context, "Не удалось скачать обновление. Проверьте интернет и попробуйте снова")
                        } finally {
                            downloading = false
                        }
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(if (downloading) "Скачиваю обновление…" else "Обновить")
                }
            }
        }
    }
}

@Composable
internal fun UpdateCheckButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    FilledTonalButton(enabled = !checking, onClick = {
        scope.launch {
            checking = true
            try {
                val result = withContext(Dispatchers.IO) { ReleaseStore.check(context.applicationContext) }
                message(context, when (result) {
                    is ReleaseCheck.Found -> "Доступно обновление. Откройте главный экран"
                    ReleaseCheck.UpToDate -> "У вас последняя версия"
                    ReleaseCheck.Unavailable -> "Не удалось проверить обновления. Попробуйте позже"
                })
            } finally {
                checking = false
            }
        }
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(if (checking) "Проверяю…" else "Проверить обновления")
    }
}

private fun openInstaller(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (_: ActivityNotFoundException) {
        message(context, "Не удалось открыть установщик. Попробуйте ещё раз")
    } catch (_: SecurityException) {
        message(context, "Не удалось открыть установщик. Проверьте разрешения телефона")
    }
}

private fun message(context: Context, value: String) = Toast.makeText(context, value, Toast.LENGTH_LONG).show()
