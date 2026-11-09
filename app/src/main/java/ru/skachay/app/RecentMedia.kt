package ru.skachay.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.FileNotFoundException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RecentFilter(val label: String) { ALL("Все"), VIDEO("Видео"), PHOTO("Фото"), AUDIO("Аудио") }
private data class RecentFile(val uri: Uri, val title: String, val source: String, val size: Long, val date: Long, val mimeType: String)

@Composable
internal fun RecentMedia(refresh: Int, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var changed by remember { mutableStateOf(0) }
    var remove by remember { mutableStateOf<List<RecentFile>?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf(emptySet<String>()) }
    var pendingDeletion by remember { mutableStateOf(emptySet<String>()) }
    var filter by remember { mutableStateOf(RecentFilter.ALL) }
    var query by remember { mutableStateOf("") }
    val files by produceState<List<RecentFile>>(initialValue = emptyList(), refresh, changed) {
        value = withContext(Dispatchers.IO) { recentFiles(context) }
    }
    val visibleFiles = remember(files, filter, query, pendingDeletion) {
        files.filter { file ->
            val typeMatches = when (filter) {
                RecentFilter.ALL -> true
                RecentFilter.VIDEO -> file.mimeType.startsWith("video/")
                RecentFilter.PHOTO -> file.mimeType.startsWith("image/")
                RecentFilter.AUDIO -> file.mimeType.startsWith("audio/")
            }
            val textMatches = query.isBlank() || file.title.contains(query.trim(), ignoreCase = true) || file.source.contains(query.trim(), ignoreCase = true)
            typeMatches && textMatches && file.uri.toString() !in pendingDeletion
        }
    }
    val selectedFiles = files.filter { it.uri.toString() in selectedUris }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selecting) "Выбрано: ${selectedFiles.size}" else "Недавние", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = {
                selecting = !selecting
                selectedUris = emptySet()
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (selecting) "Готово" else "Выбрать") }
        }
        if (selecting && selectedFiles.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { shareRecentFiles(context, selectedFiles) }) { Text("Поделиться") }
                TextButton(onClick = { remove = selectedFiles }) { Text("Удалить") }
            }
        }
        if (files.isEmpty()) Text("Здесь появятся сохранённые файлы.", style = MaterialTheme.typography.bodyMedium)
        else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(100) },
                label = { Text("Поиск файлов") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecentFilter.entries.forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
                }
            }
            if (visibleFiles.isEmpty()) Text("По этому фильтру файлов нет.", style = MaterialTheme.typography.bodyMedium)
        }
        visibleFiles.take(20).forEach { file ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable {
                    if (selecting) {
                        val key = file.uri.toString()
                        selectedUris = if (key in selectedUris) selectedUris - key else selectedUris + key
                    } else openFile(context, file.uri)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val thumbnail by produceState<Bitmap?>(initialValue = null, file.uri) {
                    value = withContext(Dispatchers.IO) { loadThumbnail(context, file.uri) }
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(56.dp)) {
                    if (thumbnail != null) Image(thumbnail!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop)
                    else Box(contentAlignment = Alignment.Center) { Text("▶") }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(file.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    Text("${file.source} · ${Formatter.formatShortFileSize(context, file.size)} · ${dateLabel(file.date)}", style = MaterialTheme.typography.bodySmall)
                }
                if (selecting) {
                    androidx.compose.material3.Checkbox(
                        checked = file.uri.toString() in selectedUris,
                        onCheckedChange = { checked ->
                            val key = file.uri.toString()
                            selectedUris = if (checked) selectedUris + key else selectedUris - key
                        },
                    )
                } else TextButton(onClick = { remove = listOf(file) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Удалить") }
            }
        }
    }
    remove?.let { filesToRemove ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text(if (filesToRemove.size == 1) "Удалить файл?" else "Удалить ${filesToRemove.size} файлов?") },
            text = { Text("Файлы исчезнут из галереи и списка недавних.") },
            confirmButton = { TextButton(onClick = {
                val uris = filesToRemove.map { it.uri.toString() }.toSet()
                pendingDeletion = pendingDeletion + uris
                selectedUris = selectedUris - uris
                selecting = false
                changed++
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = if (uris.size == 1) "Файл удалён из списка" else "Файлы удалены из списка",
                        actionLabel = "Отменить",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        pendingDeletion = pendingDeletion - uris
                    } else {
                        try {
                            withContext(Dispatchers.IO) { deleteRecentFiles(context, filesToRemove) }
                        } catch (_: SecurityException) {
                            Toast.makeText(context, "Не удалось удалить файл. Попробуйте удалить его в галерее", Toast.LENGTH_LONG).show()
                        }
                        pendingDeletion = pendingDeletion - uris
                    }
                    changed++
                }
                remove = null
            }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Отмена") } },
        )
    }
}

private fun deleteRecentFiles(context: Context, files: List<RecentFile>) {
    val uris = files.map { it.uri.toString() }.toSet()
    uris.forEach { context.contentResolver.delete(Uri.parse(it), null, null) }
    val filesPrefs = context.getSharedPreferences("download_files", Context.MODE_PRIVATE)
    filesPrefs.edit().apply { uris.forEach { remove(it) } }.apply()
    val history = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    history.edit().apply {
        history.all.filterValues { it is String && it in uris }.keys.forEach { remove(it) }
    }.apply()
}

private fun shareRecentFiles(context: Context, files: List<RecentFile>) {
    if (files.isEmpty()) return
    try {
        val intent = Intent(if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE)
            .setType(files.map { it.mimeType }.distinct().singleOrNull() ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (files.size == 1) intent.putExtra(Intent.EXTRA_STREAM, files.single().uri)
        else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { it.uri }))
        intent.clipData = ClipData.newUri(context.contentResolver, "Скачай", files.first().uri).apply {
            files.drop(1).forEach { addItem(ClipData.Item(it.uri)) }
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться файлами"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Не удалось поделиться файлами", Toast.LENGTH_LONG).show()
    }
}

private fun recentFiles(context: Context): List<RecentFile> {
    val files = context.getSharedPreferences("download_files", Context.MODE_PRIVATE).all
        .mapNotNull { (uri, source) -> (source as? String)?.let { uri to it } }.toMap().toMutableMap()
    context.getSharedPreferences("downloads", Context.MODE_PRIVATE).all.forEach { (source, uri) ->
        if (uri is String) files.putIfAbsent(uri, source)
    }
    return files.mapNotNull { (rawUri, source) ->
        val uri = Uri.parse(rawUri)
        try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                RecentFile(
                    uri,
                    cursor.getString(0) ?: "Сохранённый файл",
                    Uri.parse(source).host ?: "Скачай",
                    cursor.getLong(1),
                    cursor.getLong(2),
                    context.contentResolver.getType(uri).orEmpty(),
                )
            }
        } catch (_: SecurityException) { null }
    }.sortedByDescending { it.date }
}

private fun dateLabel(seconds: Long): String = java.text.SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru"))
    .format(java.util.Date(seconds * 1000))

private fun loadThumbnail(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(112, 112), null)
    else context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 }) }
} catch (_: FileNotFoundException) { null } catch (_: SecurityException) { null } catch (_: java.io.IOException) { null }

private fun openFile(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Не удалось открыть файл. Найдите его в галерее", Toast.LENGTH_LONG).show()
    }
}
