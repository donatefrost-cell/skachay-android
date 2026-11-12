package ru.skachay.app.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Locale
import ru.skachay.app.R
import ru.skachay.app.SkachayPreview
import ru.skachay.app.SharedUrl
import ru.skachay.app.startDownload

@Composable
internal fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(DiagnosticsStore.PREFERENCES, Context.MODE_PRIVATE) }
    var entries by remember { mutableStateOf(DiagnosticsStore.read(context)) }
    var link by remember { mutableStateOf("") }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> entries = DiagnosticsStore.read(context) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Назад", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Диагностика", style = MaterialTheme.typography.headlineMedium)
        }
        Text("Здесь показаны последние попытки скачивания и время каждого этапа.")
        entries.forEach { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.provider.label, style = MaterialTheme.typography.titleLarge)
                    Text(entry.status.label, style = MaterialTheme.typography.titleMedium)
                    entry.lastError?.let { Text("Последняя ошибка: $it") }
                    if (entry.engineWarnings.isNotEmpty()) {
                        Text("Предупреждения yt-dlp", style = MaterialTheme.typography.titleSmall)
                        entry.engineWarnings.forEach { warning ->
                            Text(warning, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    DownloadStage.entries.forEach { stage ->
                        val duration = entry.durations[stage]?.let { String.format(Locale.forLanguageTag("ru"), "%.1f с", it / 1000.0) }
                            ?: "нет данных"
                        Text("${stage.label}: $duration", style = MaterialTheme.typography.bodyMedium)
                    }
                    entry.bytesReceived?.let { received ->
                        val total = entry.bytesTotal?.let { " из ${android.text.format.Formatter.formatShortFileSize(context, it)}" }.orEmpty()
                        Text("Получено: ${android.text.format.Formatter.formatShortFileSize(context, received)}$total")
                    }
                    entry.averageBytesPerSecond?.let { Text("Средняя скорость: ${formatSpeed(it)}") }
                    entry.remainingMillis?.let { Text("Осталось: ${formatDuration(it)}") }
                    entry.vpnActive?.let { Text("VPN: ${if (it) "активен" else "не активен"}") }
                }
            }
        }
        Text("Проверить ссылку", style = MaterialTheme.typography.titleLarge)
        Text("При проверке файл будет сохранён в галерею.")
        OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Ссылка на видео или фото") }, modifier = Modifier.fillMaxWidth())
        Button(enabled = SharedUrl.firstIn(link) != null, onClick = {
            SharedUrl.firstIn(link)?.let { startDownload(context, it) }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Проверить и скачать") }
        FilledTonalButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(
                "Диагностика Скачай",
                diagnosticLog(entries, DiagnosticsStore.recentUnhandledExceptions(context)),
            ))
            Toast.makeText(context, "Журнал скопирован", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Скопировать лог") }
    }
}

@Preview(name = "Диагностика · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Preview(name = "Диагностика · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f, showSystemUi = true)
@Preview(name = "Диагностика · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Preview(name = "Диагностика · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun DiagnosticsScreenPreview() {
    SkachayPreview { DiagnosticsScreen(onBack = {}) }
}
