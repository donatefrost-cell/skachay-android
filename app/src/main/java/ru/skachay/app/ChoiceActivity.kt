package ru.skachay.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.choice.CachedPreview
import ru.skachay.app.choice.PreviewStore
import ru.skachay.app.download.SourceType
import ru.skachay.app.settings.Quality

private sealed interface PreviewState {
    data object NotNeeded : PreviewState
    data object Loading : PreviewState
    data object Failed : PreviewState
    data class Ready(val cached: CachedPreview) : PreviewState
}

class ChoiceActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(newBase.inRussian())

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = SharedUrl.firstIn(intent.getStringExtra(DownloadService.EXTRA_URL))
        if (url == null) { finish(); return }
        val initial = Quality.entries.firstOrNull { it.name == intent.getStringExtra(DownloadService.EXTRA_QUALITY) } ?: Quality.AUTO
        setContent {
            SkachayTheme {
                var quality by remember { mutableStateOf(initial) }
                var selected by remember { mutableStateOf<AvailableFormat?>(null) }
                var videoOnly by remember { mutableStateOf(false) }
                var showFormats by remember { mutableStateOf(false) }
                var showAudio by remember { mutableStateOf(false) }
                var state by remember(url) { mutableStateOf<PreviewState>(if (SourceType.isYouTube(url)) PreviewState.Loading else PreviewState.NotNeeded) }
                LaunchedEffect(url) {
                    if (state is PreviewState.Loading) {
                        state = try {
                            PreviewState.Ready(withContext(Dispatchers.IO) { PreviewStore.getOrFetch(applicationContext, url) })
                        } catch (error: CancellationException) { throw error }
                        catch (error: Exception) {
                            android.util.Log.w("SkachayPreview", "Не удалось получить сведения: ${error.javaClass.simpleName}")
                            PreviewState.Failed
                        }
                    }
                }
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(onDismissRequest = { finish() }, sheetState = sheetState) {
                    Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.82f).dp).padding(horizontal = 24.dp)) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Скачать", style = MaterialTheme.typography.headlineSmall)
                            when (val previewState = state) {
                                PreviewState.Loading -> Text("Получаю сведения о видео…")
                                PreviewState.Failed -> Text("Не удалось получить список форматов. Можно попробовать скачать автоматически.")
                                PreviewState.NotNeeded -> Text("Для этой ссылки приложение выберет доступный файл автоматически.")
                                is PreviewState.Ready -> {
                                    PreviewCard(previewState.cached)
                                    if (previewState.cached.preview.formats.isNotEmpty()) {
                                        FilledTonalButton(onClick = { showFormats = !showFormats }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                            Text(if (showFormats) "Скрыть форматы" else "Выбрать конкретный формат")
                                        }
                                    }
                                }
                            }
                            selected?.let { Text("Выбрано: ${formatLabel(this@ChoiceActivity, it)}", style = MaterialTheme.typography.bodyMedium) }
                            Text("Качество видео YouTube", style = MaterialTheme.typography.titleMedium)
                            Quality.entries.forEach { option ->
                                ChoiceRow(if (option == Quality.AUTO) "Авто — до 720p" else "Лучшее — до 1080p", selected == null && quality == option) {
                                    quality = option; selected = null; videoOnly = false
                                }
                            }
                            val formats = (state as? PreviewState.Ready)?.cached?.preview?.formats.orEmpty()
                            if (showFormats && formats.isNotEmpty()) {
                                val video = formats.filter { it.hasVideo }.sortedWith(compareByDescending<AvailableFormat> { it.height ?: 0 }.thenByDescending { it.fps ?: 0 })
                                val audio = formats.filter { !it.hasVideo && it.hasAudio }
                                    .sortedWith(compareByDescending<AvailableFormat> { it.isOriginalAudio }.thenByDescending { it.audioBitrate ?: 0 }.thenBy { it.isVolumeBalanced })
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { showAudio = false }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(if (showAudio) "Видео" else "✓ Видео") }
                                    TextButton(onClick = { showAudio = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(if (showAudio) "✓ Только звук" else "Только звук") }
                                }
                                if (!showAudio) {
                                    video.forEach { format ->
                                        ChoiceRow(formatLabel(this@ChoiceActivity, format), selected?.id == format.id) { selected = format; videoOnly = false }
                                        if (selected?.id == format.id && !format.hasAudio) {
                                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = videoOnly, onClick = { videoOnly = !videoOnly }), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = videoOnly, onCheckedChange = null)
                                                Text("Видео без звука")
                                            }
                                        }
                                    }
                                } else if (audio.isEmpty()) {
                                    Text("Для этого видео подходящий аудиоформат не найден.")
                                } else {
                                    audio.forEach { format ->
                                        ChoiceRow(formatLabel(this@ChoiceActivity, format), selected?.id == format.id) { selected = format; videoOnly = false }
                                    }
                                }
                            }
                        }
                        Button(enabled = state !is PreviewState.Loading, onClick = {
                            startDownload(this@ChoiceActivity, url, quality, usePreview = state is PreviewState.Ready,
                                formatId = selected?.id, videoOnly = videoOnly)
                            finish()
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp)) { Text("Скачать") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

@Composable
private fun PreviewCard(cached: CachedPreview) {
    val preview = cached.preview
    val thumbnail by produceState<Bitmap?>(initialValue = null, preview.thumbnailUrl) {
        value = preview.thumbnailUrl?.let { url -> withContext(Dispatchers.IO) { loadThumbnail(url) } }
    }
    thumbnail?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop) }
    Text(preview.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
    val details = listOfNotNull(preview.author, preview.durationSeconds?.let { "%d:%02d".format(it / 60, it % 60) })
    if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
}

private fun formatLabel(context: Context, format: AvailableFormat): String {
    val parts = mutableListOf<String>()
    if (format.hasVideo) {
        parts += format.height?.let { "${it}p" } ?: "Видео"
        format.fps?.let { parts += "$it к/с" }
        format.videoCodec?.let { parts += it.substringBefore('.') }
    } else {
        parts += "Аудио"
        format.language?.let {
            val locale = Locale.forLanguageTag(it)
            val russian = Locale.forLanguageTag("ru")
            val name = locale.getDisplayLanguage(russian).replaceFirstChar { char -> char.uppercase(russian) }
            parts += if (locale.country.isEmpty()) name else "$name (${locale.country})"
        }
        if (format.isOriginalAudio) parts += "оригинал"
        if (format.isVolumeBalanced) parts += "ровная громкость"
        format.audioBitrate?.let { parts += "$it Кбит/с" }
        format.audioCodec?.let { parts += it.substringBefore('.') }
    }
    parts += format.extension
    format.sizeBytes?.let { parts += "≈${Formatter.formatShortFileSize(context, it)}" }
    return parts.joinToString(" · ")
}

private fun loadThumbnail(url: String): Bitmap? = try {
    val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful || response.body.contentLength() > 3_000_000) return null
        val bytes = ByteArrayOutputStream()
        response.body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (bytes.size() + count > 3_000_000) return null
                bytes.write(buffer, 0, count)
            }
        }
        val data = bytes.toByteArray()
        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = 2 })
    }
} catch (_: IOException) { null }
