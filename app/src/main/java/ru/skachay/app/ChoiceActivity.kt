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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
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
import ru.skachay.app.choice.SocialPreview
import ru.skachay.app.choice.SocialPreviewStore
import ru.skachay.app.download.AudioBitrate
import ru.skachay.app.download.AudioExport
import ru.skachay.app.download.InstagramLink
import ru.skachay.app.download.ResolvedMedia
import ru.skachay.app.download.SavedFileName
import ru.skachay.app.download.SourceType
import ru.skachay.app.download.TikTokLink
import ru.skachay.app.settings.Quality

private sealed interface PreviewState {
    data object NotNeeded : PreviewState
    data object Loading : PreviewState
    data object Failed : PreviewState
    data class Ready(val cached: CachedPreview) : PreviewState
    data class SocialReady(val cached: SocialPreview) : PreviewState
}

class ChoiceActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(newBase.inRussian())

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = SharedUrl.firstIn(intent.getStringExtra(DownloadService.EXTRA_URL))
        if (url == null) { finish(); return }
        val initial = Quality.entries.firstOrNull { it.name == intent.getStringExtra(DownloadService.EXTRA_QUALITY) } ?: Quality.AUTO
        val isYouTube = SourceType.isYouTube(url)
        val isSocial = InstagramLink.supports(url) || TikTokLink.supports(url)
        setContent {
            SkachayTheme {
                var quality by remember { mutableStateOf(initial) }
                var selected by remember { mutableStateOf<AvailableFormat?>(null) }
                var videoOnly by remember { mutableStateOf(false) }
                var showFormats by remember { mutableStateOf(false) }
                var showAudio by remember { mutableStateOf(false) }
                var audioExport by remember { mutableStateOf(AudioExport.SOURCE) }
                var audioBitrate by remember { mutableStateOf(AudioBitrate.K192) }
                var subtitleLanguage by remember { mutableStateOf<String?>(null) }
                var subtitleAutomatic by remember { mutableStateOf(false) }
                var embedSubtitles by remember { mutableStateOf(false) }
                var selectedItems by remember(url) { mutableStateOf<Set<Int>>(emptySet()) }
                var fileName by remember(url) { mutableStateOf(intent.getStringExtra(DownloadService.EXTRA_FILE_NAME).orEmpty().take(256)) }
                val cleanedFileName = remember(fileName) { SavedFileName.clean(fileName) }
                val invalidFileName = fileName.isNotBlank() && cleanedFileName == null
                var state by remember(url) { mutableStateOf<PreviewState>(if (isYouTube || isSocial) PreviewState.Loading else PreviewState.NotNeeded) }
                LaunchedEffect(url) {
                    if (state is PreviewState.Loading) {
                        state = try {
                            if (isYouTube) {
                                PreviewState.Ready(withContext(Dispatchers.IO) { PreviewStore.getOrFetch(applicationContext, url) })
                            } else {
                                val preview = withContext(Dispatchers.IO) { SocialPreviewStore.getOrFetch(applicationContext, url) }
                                selectedItems = preview.items.indices.toSet()
                                PreviewState.SocialReady(preview)
                            }
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
                        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { Text("Скачать", style = MaterialTheme.typography.headlineSmall) }
                            when (val previewState = state) {
                                PreviewState.Loading -> item { Text("Получаю сведения о публикации…") }
                                PreviewState.Failed -> item { Text("Не удалось получить список файлов. Можно попробовать скачать автоматически.") }
                                PreviewState.NotNeeded -> item { Text("Для этой ссылки приложение выберет доступный файл автоматически.") }
                                is PreviewState.Ready -> item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PreviewCard(previewState.cached)
                                        if (previewState.cached.preview.formats.isNotEmpty()) {
                                            FilledTonalButton(onClick = { showFormats = !showFormats }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                                Text(if (showFormats) "Скрыть форматы" else "Выбрать конкретный формат")
                                            }
                                        }
                                    }
                                }
                                is PreviewState.SocialReady -> {
                                    item {
                                        Column {
                                            Text("Выберите файлы публикации", style = MaterialTheme.typography.titleMedium)
                                            TextButton(onClick = {
                                                selectedItems = if (selectedItems.size == previewState.cached.items.size) emptySet() else previewState.cached.items.indices.toSet()
                                            }, modifier = Modifier.heightIn(min = 56.dp)) {
                                                Text(if (selectedItems.size == previewState.cached.items.size) "Снять все" else "Выбрать все")
                                            }
                                        }
                                    }
                                    item { FileNameField(fileName, invalidFileName) { fileName = it } }
                                    itemsIndexed(previewState.cached.items) { index, media ->
                                        SocialChoiceRow(media, index, index in selectedItems) {
                                            selectedItems = if (index in selectedItems) selectedItems - index else selectedItems + index
                                        }
                                    }
                                }
                            }
                            if (state !is PreviewState.SocialReady) item { FileNameField(fileName, invalidFileName) { fileName = it } }
                            if (isYouTube) {
                                selected?.let { format -> item { Text("Выбрано: ${formatLabel(this@ChoiceActivity, format)}", style = MaterialTheme.typography.bodyMedium) } }
                                selected?.takeIf { !it.hasVideo && it.extension == "webm" }?.let {
                                    item { Text("Звук сохранится в файле OGG без изменения качества.", style = MaterialTheme.typography.bodyMedium) }
                                }
                                item { Text("Качество видео YouTube", style = MaterialTheme.typography.titleMedium) }
                                items(listOf(Quality.AUTO, Quality.BEST) + Quality.presets) { option ->
                                    ChoiceRow(option.label, selected == null && quality == option) {
                                        quality = option; selected = null; videoOnly = false
                                    }
                                }
                                val subtitleTracks = (state as? PreviewState.Ready)?.cached?.preview?.subtitles.orEmpty()
                                if (!showAudio && subtitleTracks.isNotEmpty()) {
                                    item { Text("Субтитры (необязательно)", style = MaterialTheme.typography.titleMedium) }
                                    item { Text("Помогают читать речь или смотреть без звука. По умолчанию выключены.") }
                                    item { ChoiceRow("Без субтитров", subtitleLanguage == null) { subtitleLanguage = null } }
                                    subtitleTracks.forEach { track ->
                                        if (track.manual) item {
                                            ChoiceRow("${track.language} — авторские", subtitleLanguage == track.language && !subtitleAutomatic) {
                                                subtitleLanguage = track.language; subtitleAutomatic = false
                                            }
                                        }
                                        if (track.automatic) item {
                                            ChoiceRow("${track.language} — автоматические", subtitleLanguage == track.language && subtitleAutomatic) {
                                                subtitleLanguage = track.language; subtitleAutomatic = true
                                            }
                                        }
                                    }
                                    item {
                                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                                            selected = embedSubtitles,
                                            enabled = subtitleLanguage != null,
                                            onClick = { embedSubtitles = !embedSubtitles },
                                        ), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = embedSubtitles, enabled = subtitleLanguage != null, onCheckedChange = null)
                                            Text("Встроить в видео (файл .srt тоже сохранится)")
                                        }
                                    }
                                }
                            }
                            val formats = (state as? PreviewState.Ready)?.cached?.preview?.formats.orEmpty()
                            if (showFormats && formats.isNotEmpty()) {
                                val video = formats.filter { it.hasVideo }.sortedWith(compareByDescending<AvailableFormat> { it.height ?: 0 }
                                    .thenByDescending { it.fps ?: 0 }.thenByDescending { it.videoBitrate ?: 0 })
                                val audio = formats.filter { !it.hasVideo && it.hasAudio }
                                    .sortedWith(compareByDescending<AvailableFormat> { it.isOriginalAudio }.thenByDescending { it.audioBitrate ?: 0 }.thenBy { it.isVolumeBalanced })
                                item {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { showAudio = false }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(if (showAudio) "Видео" else "✓ Видео") }
                                        TextButton(onClick = { showAudio = true; subtitleLanguage = null; embedSubtitles = false }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(if (showAudio) "✓ Только звук" else "Только звук") }
                                    }
                                }
                                if (!showAudio) {
                                    item { Text("У каждого варианта показан доступный видеобитрейт; выберите нужный формат.") }
                                    items(video) { format ->
                                        Column {
                                            ChoiceRow(formatLabel(this@ChoiceActivity, format), selected?.id == format.id) { selected = format; videoOnly = false }
                                            if (selected?.id == format.id && !format.hasAudio) {
                                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = videoOnly, onClick = { videoOnly = !videoOnly }), verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(checked = videoOnly, onCheckedChange = null)
                                                    Text("Видео без звука")
                                                }
                                            }
                                        }
                                    }
                                } else if (audio.isEmpty()) {
                                    item { Text("Для этого видео подходящий аудиоформат не найден.") }
                                } else {
                                    items(audio) { format ->
                                        ChoiceRow(formatLabel(this@ChoiceActivity, format), selected?.id == format.id) { selected = format; videoOnly = false }
                                    }
                                    if (selected?.let { !it.hasVideo && it.hasAudio } == true) {
                                        item { Text("Формат аудио", style = MaterialTheme.typography.titleMedium) }
                                        items(AudioExport.entries.toList()) { option ->
                                            ChoiceRow(option.label, audioExport == option) { audioExport = option }
                                        }
                                        if (audioExport != AudioExport.SOURCE) {
                                            item { Text("Битрейт при перекодировании", style = MaterialTheme.typography.titleMedium) }
                                            items(AudioBitrate.entries.toList()) { option ->
                                                ChoiceRow("${option.kilobits} кбит/с", audioBitrate == option) { audioBitrate = option }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Button(enabled = state !is PreviewState.Loading && !invalidFileName && (state !is PreviewState.SocialReady || selectedItems.isNotEmpty()), onClick = {
                            val social = state as? PreviewState.SocialReady
                            startDownload(this@ChoiceActivity, url, quality, usePreview = state is PreviewState.Ready,
                                formatId = selected?.id, videoOnly = videoOnly, socialPreviewId = social?.cached?.id,
                                selectedItems = social?.let { selectedItems.sorted().toIntArray() }, fileName = cleanedFileName,
                                audioExport = audioExport, audioBitrate = audioBitrate.kilobits,
                                subtitleLanguage = subtitleLanguage, subtitleAutomatic = subtitleAutomatic,
                                embedSubtitles = embedSubtitles)
                            finish()
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp)) { Text("Скачать") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileNameField(value: String, invalid: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(256)) },
        label = { Text("Имя файла (необязательно)") },
        supportingText = { Text(if (invalid) "Введите подходящее имя." else "Имя применяется к новым загрузкам; расширение добавится автоматически.") },
        isError = invalid,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SocialChoiceRow(media: ResolvedMedia, index: Int, selected: Boolean, onClick: () -> Unit) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, media.url, media.headers) {
        value = if (media.type == ResolvedMedia.Kind.IMAGE) withContext(Dispatchers.IO) { loadThumbnail(media.url, media.headers, 160) } else null
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).selectable(selected = selected, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = selected, onCheckedChange = null)
        thumbnail?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
        val name = when (media.type) {
            ResolvedMedia.Kind.IMAGE -> "Фото"
            ResolvedMedia.Kind.VIDEO -> "Видео"
            ResolvedMedia.Kind.AUDIO -> "Аудио"
        }
        Text("$name ${index + 1}", Modifier.padding(start = 12.dp))
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
        format.videoBitrate?.let { parts += "≈$it Кбит/с" }
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

private val thumbnailClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

private fun loadThumbnail(url: String, headers: Map<String, String> = emptyMap(), maxDimension: Int = 600): Bitmap? = try {
    val request = Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }.build()
    thumbnailClient.newCall(request).execute().use { response ->
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
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth !in 1..32768 || bounds.outHeight !in 1..32768) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension && sample < 128) sample *= 2
        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
    }
} catch (_: IOException) { null }
