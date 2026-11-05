package ru.skachay.app

import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RecentFile(val uri: Uri, val title: String, val source: String, val size: Long, val date: Long)

@Composable
internal fun RecentMedia(refresh: Int) {
    val context = LocalContext.current
    var changed by remember { mutableStateOf(0) }
    var remove by remember { mutableStateOf<RecentFile?>(null) }
    val files by produceState<List<RecentFile>>(initialValue = emptyList(), refresh, changed) {
        value = withContext(Dispatchers.IO) { recentFiles(context) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Недавние", style = MaterialTheme.typography.titleLarge)
        if (files.isEmpty()) Text("Здесь появятся сохранённые файлы.", style = MaterialTheme.typography.bodyMedium)
        files.take(20).forEach { file ->
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
                val thumbnail by produceState<Bitmap?>(initialValue = null, file.uri) {
                    value = withContext(Dispatchers.IO) { loadThumbnail(context, file.uri) }
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(56.dp)) {
                    if (thumbnail != null) Image(thumbnail!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop)
                    else Box(contentAlignment = Alignment.Center) { Text("▶") }
                }
                Column(Modifier.weight(1f).clickable { openFile(context, file.uri) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(file.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    Text("${file.source} · ${Formatter.formatShortFileSize(context, file.size)}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { remove = file }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Удалить") }
            }
        }
    }
    remove?.let { file ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("Удалить файл?") },
            text = { Text("Файл исчезнет из галереи и списка недавних.") },
            confirmButton = { TextButton(onClick = {
                try {
                    context.contentResolver.delete(file.uri, null, null)
                    val filesPrefs = context.getSharedPreferences("download_files", Context.MODE_PRIVATE)
                    filesPrefs.edit().remove(file.uri.toString()).apply()
                    val history = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
                    history.edit().apply { history.all.filterValues { it == file.uri.toString() }.keys.forEach { key -> this.remove(key) } }.apply()
                    changed++
                } catch (_: SecurityException) {
                    Toast.makeText(context, "Не удалось удалить файл. Попробуйте удалить его в галерее", Toast.LENGTH_LONG).show()
                }
                remove = null
            }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Отмена") } },
        )
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
                RecentFile(uri, cursor.getString(0) ?: "Сохранённый файл", Uri.parse(source).host ?: "Скачай", cursor.getLong(1), cursor.getLong(2))
            }
        } catch (_: SecurityException) { null }
    }.sortedByDescending { it.date }
}

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
