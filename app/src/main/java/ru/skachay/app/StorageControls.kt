package ru.skachay.app

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.skachay.app.settings.StorageUsage
import ru.skachay.app.queue.DownloadQueueDatabase

@Composable
internal fun StorageControls(sizeOverride: Pair<Long, Long>? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    val sizes by produceState<Pair<Long, Long>?>(initialValue = sizeOverride, sizeOverride, refresh) {
        value = sizeOverride ?: withContext(Dispatchers.IO) {
            val all = StorageUsage.bytes(context.dataDir)
            val cache = StorageUsage.bytes(context.cacheDir)
            (all - cache).coerceAtLeast(0) to cache
        }
    }
    sizes?.let { (files, cache) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Данные приложения", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(Formatter.formatShortFileSize(context, files), style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Временный кэш", style = MaterialTheme.typography.titleSmall)
                Text(Formatter.formatShortFileSize(context, cache), style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(
                onClick = {
                    if (clearingCache) return@IconButton
                    clearingCache = true
                    cacheMessage = null
                    scope.launch {
                        val success = withContext(Dispatchers.IO) { StorageUsage.clearCache(context.cacheDir) }
                        cacheMessage = if (success) "Кэш очищен." else "Не удалось очистить часть кэша. Повторите попытку."
                        clearingCache = false
                        refresh++
                    }
                },
                enabled = !clearingCache,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = if (clearingCache) "Очистка кэша" else "Очистить кэш",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            "Сохранённые в галерее файлы здесь не учитываются. Временные файлы текущих загрузок не удаляются.",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        cacheMessage?.let { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    } ?: Text("Подсчитываю занятое место…", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    FilledTonalButton(
        onClick = { confirm = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) { Text("Очистить историю") }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Очистить историю?") },
            text = { Text("Список недавних и сведения о скачанных ссылках исчезнут. Файлы в галерее останутся.") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        context.getSharedPreferences("downloads", Context.MODE_PRIVATE).edit().clear().apply()
                        context.getSharedPreferences("download_files", Context.MODE_PRIVATE).edit().clear().apply()
                        DownloadQueueDatabase.get(context).queue().clearFinished()
                    }
                }
                confirm = false
                refresh++
            }) { Text("Очистить") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}
