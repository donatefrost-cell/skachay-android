package ru.skachay.app

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.skachay.app.settings.StorageUsage

@Composable
internal fun StorageControls() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val sizes by produceState<Pair<Long, Long>?>(initialValue = null, refresh) {
        value = withContext(Dispatchers.IO) {
            val all = StorageUsage.bytes(context.dataDir)
            val cache = StorageUsage.bytes(context.cacheDir)
            (all - cache).coerceAtLeast(0) to cache
        }
    }
    Text("Память и история", style = MaterialTheme.typography.titleLarge)
    sizes?.let { (files, cache) ->
        Text("Данные приложения: ${Formatter.formatShortFileSize(context, files)}")
        Text("Временный кэш: ${Formatter.formatShortFileSize(context, cache)}")
        Text("Сохранённые в галерее файлы здесь не учитываются.", style = MaterialTheme.typography.bodyMedium)
    } ?: Text("Подсчитываю занятое место…")
    FilledTonalButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Очистить историю") }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Очистить историю?") },
            text = { Text("Список недавних и сведения о скачанных ссылках исчезнут. Файлы в галерее останутся.") },
            confirmButton = { TextButton(onClick = {
                context.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().clear().apply()
                context.getSharedPreferences("download_files", Context.MODE_PRIVATE).edit().clear().apply()
                confirm = false
                refresh++
            }) { Text("Очистить") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}
