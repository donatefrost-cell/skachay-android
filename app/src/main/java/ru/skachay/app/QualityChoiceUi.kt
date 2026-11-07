package ru.skachay.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.choice.QualityAudioCard
import ru.skachay.app.choice.QualityAudioKind
import ru.skachay.app.choice.QualityChoiceBuilder
import ru.skachay.app.choice.QualityVideoCard
import ru.skachay.app.choice.QualityVideoTier
import ru.skachay.app.choice.VideoPreview
import ru.skachay.app.choice.formatMediaSizeRu

@Composable
internal fun QualityChoiceSheetContent(
    preview: VideoPreview?,
    source: String,
    loading: Boolean,
    slowSearch: Boolean,
    errorMessage: String?,
    advancedMode: Boolean,
    audioMode: Boolean,
    selectedVideoId: String?,
    selectedAudioKind: QualityAudioKind?,
    videoChoices: List<QualityVideoCard>,
    audioChoices: List<QualityAudioCard>,
    rememberChoice: Boolean,
    estimatedSizeBytes: Long?,
    downloadEnabled: Boolean,
    onAudioModeChanged: (Boolean) -> Unit,
    onVideoChoice: (QualityVideoCard) -> Unit,
    onAudioChoice: (QualityAudioCard) -> Unit,
    onRememberChoiceChanged: (Boolean) -> Unit,
    onOpenAdvanced: () -> Unit,
    onCancel: () -> Unit,
    onDownload: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maximumHeight = (configuration.screenHeightDp * 0.92f).dp
    Column(
        Modifier.fillMaxWidth().heightIn(max = maximumHeight).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (loading) {
            QualityHeaderSkeleton(source, slowSearch, onCancel)
        } else if (preview != null) {
            QualityChoiceHeader(preview, source)
        } else {
            QualityHeaderFallback(source)
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to "Видео", true to "Только звук").forEachIndexed { index, (audio, label) ->
                SegmentedButton(
                    selected = audioMode == audio,
                    onClick = { onAudioModeChanged(audio) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label, maxLines = 1) }
            }
        }

        Text("Качество", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                loading -> items(3) { QualityCardSkeleton() }
                errorMessage != null -> item { Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                audioMode -> {
                    if (audioChoices.isEmpty()) item { Text("Подходящий аудиоформат не найден.", style = MaterialTheme.typography.bodyMedium) }
                    items(audioChoices, key = { it.kind.name }) { choice ->
                        QualityChoiceCard(
                            title = choice.kind.title,
                            subtitle = if (choice.kind == QualityAudioKind.BEST_M4A) "Исходное качество" else "Экономит место",
                            size = formatMediaSizeRu(choice.estimatedSizeBytes),
                            selected = selectedAudioKind == choice.kind,
                            warning = choice.estimatedSizeBytes?.let { it >= ru.skachay.app.choice.LARGE_VIDEO_WARNING_BYTES } == true,
                            recommended = false,
                            onClick = { onAudioChoice(choice) },
                        )
                    }
                }
                videoChoices.isEmpty() -> item {
                    Text("Совместимый видеоформат не найден. Можно попробовать автоматическую загрузку.", style = MaterialTheme.typography.bodyMedium)
                }
                else -> items(videoChoices, key = { it.video.id }) { choice ->
                    val details = buildString {
                        append(choice.video.height?.let { "${it}p" } ?: "Видео")
                        choice.video.fps?.takeIf { it > 30 }?.let { append(" · ${it} к/с") }
                    }
                    QualityChoiceCard(
                        title = choice.tier.title,
                        subtitle = details,
                        size = formatMediaSizeRu(choice.estimatedSizeBytes),
                        selected = selectedVideoId == choice.video.id,
                        warning = choice.sizeWarning,
                        recommended = choice.recommended,
                        onClick = { onVideoChoice(choice) },
                    )
                }
            }
        }

        if (advancedMode && preview != null && !loading) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenAdvanced)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Все форматы и настройки", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(painterResource(R.drawable.ic_back), contentDescription = null,
                    modifier = Modifier.size(24.dp).rotate(180f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (preview != null && !loading) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(rememberChoice, onClick = { onRememberChoiceChanged(!rememberChoice) })
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rememberChoice, onCheckedChange = null)
                Text("Запомнить выбор", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(
            enabled = downloadEnabled && !loading,
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).navigationBarsPadding().padding(bottom = 4.dp),
        ) {
            val size = estimatedSizeBytes?.let { formatMediaSizeRu(it).removePrefix("≈") }
            Text(if (size == null) "Скачать" else "Скачать · $size", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QualityChoiceHeader(preview: VideoPreview, source: String) {
    val image by produceState<Bitmap?>(initialValue = null, preview.thumbnailUrl) {
        value = preview.thumbnailUrl?.let { withContext(Dispatchers.IO) { loadThumbnail(it, maxDimension = 320) } }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 88.dp, height = 50.dp).clip(RoundedCornerShape(12.dp))) {
            if (image == null) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            } else {
                Image(image!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            }
            preview.durationSeconds?.let { seconds ->
                Surface(
                    Modifier.align(Alignment.BottomEnd).padding(3.dp),
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    val duration = if (seconds >= 3600) {
                        "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
                    } else {
                        "%d:%02d".format(seconds / 60, seconds % 60)
                    }
                    Text(duration, Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(preview.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun QualityHeaderFallback(source: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 88.dp, height = 50.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Сведения недоступны", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun QualityHeaderSkeleton(source: String, slowSearch: Boolean, onCancel: () -> Unit) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "choice header shimmer")
    val alpha by transition.animateFloat(0.35f, 0.85f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(850), androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "choice header shimmer alpha")
    val shimmer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 88.dp, height = 50.dp).clip(RoundedCornerShape(12.dp)).background(shimmer))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.82f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
    if (slowSearch) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ищу дольше обычного…", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.TextButton(onClick = onCancel) { Text("Отмена") }
        }
    }
}

@Composable
private fun QualityCardSkeleton() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "choice cards shimmer")
    val alpha by transition.animateFloat(0.35f, 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(850), androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "choice cards shimmer alpha")
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.45f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.fillMaxWidth(0.68f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Box(Modifier.width(46.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
private fun QualityChoiceCard(
    title: String,
    subtitle: String,
    size: String,
    selected: Boolean,
    warning: Boolean,
    recommended: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f).padding(start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (recommended) Text("рекомендуем", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
            Text(size, style = MaterialTheme.typography.labelLarge,
                color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
internal fun AdvancedFormatsPage(
    preview: VideoPreview,
    source: String,
    selected: AvailableFormat?,
    videoOnly: Boolean,
    fileName: String,
    fileNameInvalid: Boolean,
    saveFolderLabel: String,
    subtitleLanguage: String?,
    subtitleAutomatic: Boolean,
    embedSubtitles: Boolean,
    embedThumbnail: Boolean,
    downloadLabel: String,
    downloadEnabled: Boolean,
    onBack: () -> Unit,
    onSelectFormat: (AvailableFormat) -> Unit,
    onVideoOnlyChanged: (Boolean) -> Unit,
    onFileNameChanged: (String) -> Unit,
    onChooseSaveFolder: () -> Unit,
    onResetSaveFolder: () -> Unit,
    onSubtitleChanged: (String?, Boolean) -> Unit,
    onEmbedSubtitlesChanged: (Boolean) -> Unit,
    onEmbedThumbnailChanged: (Boolean) -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val videoGroups = remember(preview) {
        preview.formats.filter { it.hasVideo }.groupBy { it.height ?: 0 }.toSortedMap(compareByDescending { it })
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Назад", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Все форматы и настройки", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$source · ${preview.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Text("Форматы видео", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                videoGroups.forEach { (height, formats) ->
                    item(key = "resolution-$height") {
                        Text(if (height > 0) "${height}p" else "Разрешение неизвестно", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(formats.sortedWith(compareByDescending<AvailableFormat> { it.fps ?: 0 }.thenByDescending { it.videoBitrate ?: 0 }), key = { it.id }) { format ->
                        AdvancedFormatRow(context, preview, format, selected?.id == format.id, onClick = { onSelectFormat(format) })
                    }
                }
                val audioFormats = preview.formats.filter { !it.hasVideo && it.hasAudio }
                    .sortedWith(compareByDescending<AvailableFormat> { it.extension == "m4a" }.thenByDescending { it.audioBitrate ?: 0 })
                if (audioFormats.isNotEmpty()) {
                    item { Text("Форматы звука", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
                    items(audioFormats, key = { it.id }) { format ->
                        AdvancedFormatRow(context, preview, format, selected?.id == format.id, onClick = { onSelectFormat(format) })
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                            selected = videoOnly,
                            enabled = selected?.hasVideo == true && !selected.hasAudio,
                            onClick = { onVideoOnlyChanged(!videoOnly) },
                        ).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = videoOnly, enabled = selected?.hasVideo == true && !selected.hasAudio, onCheckedChange = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text("Видео без звука", style = MaterialTheme.typography.titleSmall)
                            Text("Доступно для отдельных видеопотоков", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { Text("Настройки файла", style = MaterialTheme.typography.titleMedium) }
                item { FileNameField(value = fileName, invalid = fileNameInvalid, onChange = onFileNameChanged) }
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("Папка сохранения", style = MaterialTheme.typography.titleSmall)
                        Text(saveFolderLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.TextButton(onClick = onChooseSaveFolder) { Text("Выбрать папку") }
                            if (saveFolderLabel != DEFAULT_SAVE_FOLDER_LABEL) {
                                androidx.compose.material3.TextButton(onClick = onResetSaveFolder) { Text("По умолчанию") }
                            }
                        }
                    }
                }
                item { Text("Субтитры", style = MaterialTheme.typography.titleSmall) }
                item { AdvancedSubtitles(preview, subtitleLanguage, subtitleAutomatic, onSubtitleChanged) }
                item {
                    AdvancedSwitchRow("Встроить субтитры", subtitleLanguage != null && embedSubtitles,
                        enabled = subtitleLanguage != null, onCheckedChange = onEmbedSubtitlesChanged)
                }
                item { AdvancedSwitchRow("Добавить миниатюру в файл", embedThumbnail, onCheckedChange = onEmbedThumbnailChanged) }
                item { Spacer(Modifier.height(8.dp)) }
            }
            Button(
                enabled = downloadEnabled,
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 56.dp),
            ) { Text(downloadLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

internal const val DEFAULT_SAVE_FOLDER_LABEL = "Фото, видео и музыка · папка «Скачай»"

@Composable
private fun AdvancedFormatRow(context: Context, preview: VideoPreview, format: AvailableFormat, selected: Boolean, onClick: () -> Unit) {
    val compatible = format.extension == "mp4" && format.videoCodec?.startsWith("avc1", true) == true ||
        format.extension == "m4a" && format.audioCodec?.startsWith("mp4a", true) == true
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(advancedFormatLabel(context, format, preview.durationSeconds), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (compatible) "Работает везде" else "Может не воспроизводиться в галерее",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (compatible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        }
        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AdvancedSubtitles(
    preview: VideoPreview,
    subtitleLanguage: String?,
    subtitleAutomatic: Boolean,
    onSubtitleChanged: (String?, Boolean) -> Unit,
) {
    Column {
        AdvancedOptionRow("Без субтитров", subtitleLanguage == null) { onSubtitleChanged(null, false) }
        preview.subtitles.forEach { track ->
            if (track.manual) AdvancedOptionRow("${subtitleLanguageName(track.language)} · авторские", subtitleLanguage == track.language && !subtitleAutomatic) {
                onSubtitleChanged(track.language, false)
            }
            if (track.automatic) AdvancedOptionRow("${subtitleLanguageName(track.language)} · автоматические", subtitleLanguage == track.language && subtitleAutomatic) {
                onSubtitleChanged(track.language, true)
            }
        }
    }
}

private fun subtitleLanguageName(language: String): String {
    val locale = Locale.forLanguageTag(language)
    val russian = Locale.forLanguageTag("ru")
    return locale.getDisplayName(russian).replaceFirstChar { it.uppercase(russian) }
}

@Composable
private fun AdvancedOptionRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, onClick = onClick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AdvancedSwitchRow(title: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(checked, enabled = enabled, onClick = { onCheckedChange(!checked) }).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}

private fun advancedFormatLabel(context: Context, format: AvailableFormat, durationSeconds: Int?): String {
    val values = mutableListOf<String>()
    if (format.hasVideo) values += format.height?.let { "${it}p" } ?: "Видео"
    format.fps?.let { values += "$it к/с" }
    if (format.hasVideo) format.videoCodec?.let { values += it.substringBefore('.') }
    else format.audioCodec?.let { values += it.substringBefore('.') }
    values += format.extension.uppercase()
    if (format.hasVideo) format.videoBitrate?.let { values += "$it кбит/с" }
    else format.audioBitrate?.let { values += "$it кбит/с" }
    format.sizeBytes?.let { values += formatMediaSizeRu(it) }
        ?: durationSeconds?.let { seconds ->
            val bitrate = if (format.hasVideo) format.videoBitrate else format.audioBitrate
            bitrate?.let { values += formatMediaSizeRu(it.toLong() * 125L * seconds) }
        }
    return values.joinToString(" · ")
}

@Composable
private fun FileNameField(value: String, invalid: Boolean, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(256)) },
        label = { Text("Имя файла (необязательно)") },
        supportingText = { Text(if (invalid) "Введите подходящее имя." else "Расширение добавится автоматически.") },
        isError = invalid,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ChoiceSheetLoadedPreview() = QualityChoiceDemo()

@Composable
internal fun ChoiceSheetLoadingPreview() = QualityChoiceDemo(loading = true)

@Composable
internal fun ChoiceSheetLongTitlePreview() = QualityChoiceDemo(longTitle = true)

@Composable
internal fun ChoiceSheetAudioPreview() = QualityChoiceDemo(audioMode = true)

@Composable
private fun QualityChoiceDemo(loading: Boolean = false, longTitle: Boolean = false, audioMode: Boolean = false) {
    val sample = remember(longTitle) {
        VideoPreview(
            title = if (longTitle) "Очень длинное название ролика о поездке по красивым местам России и подготовке к путешествию" else "Путешествие по Карелии",
            author = "Канал автора",
            durationSeconds = 245,
            thumbnailUrl = null,
            formats = listOf(
                AvailableFormat("1080", "mp4", "avc1.640028", null, 1080, 30, null, 25_000_000, videoBitrate = 4_000),
                AvailableFormat("720", "mp4", "avc1.4d401f", null, 720, 30, null, 13_000_000, videoBitrate = 2_500),
                AvailableFormat("360", "mp4", "avc1.4d401e", null, 360, 30, null, 5_000_000, videoBitrate = 700),
                AvailableFormat("m4a", "m4a", null, "mp4a.40.2", null, null, 128, 4_000_000),
            ),
        )
    }
    val videos = QualityChoiceBuilder.videoCards(sample)
    val audios = QualityChoiceBuilder.audioCards(sample)
    val selectedVideo = videos.firstOrNull { it.tier == QualityVideoTier.RECOMMENDED } ?: videos.firstOrNull()
    val selectedAudio = audios.firstOrNull { it.kind == QualityAudioKind.BEST_M4A } ?: audios.firstOrNull()
    SkachayPreview {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                Surface(
                    Modifier.fillMaxWidth().fillMaxSize(0.92f),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        Box(Modifier.padding(top = 10.dp).size(width = 32.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterHorizontally))
                        QualityChoiceSheetContent(
                            preview = sample.takeUnless { loading }, source = "YouTube", loading = loading, slowSearch = false,
                            errorMessage = null, advancedMode = true, audioMode = audioMode,
                            selectedVideoId = selectedVideo?.video?.id, selectedAudioKind = selectedAudio?.kind,
                            videoChoices = videos, audioChoices = audios, rememberChoice = false,
                            estimatedSizeBytes = if (loading) null else if (audioMode) selectedAudio?.estimatedSizeBytes else selectedVideo?.estimatedSizeBytes,
                            downloadEnabled = !loading, onAudioModeChanged = {}, onVideoChoice = {}, onAudioChoice = {},
                            onRememberChoiceChanged = {}, onOpenAdvanced = {}, onCancel = {}, onDownload = {},
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Выбор качества · загрузка", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Composable
internal fun ChoiceLoadingPreview() = ChoiceSheetLoadingPreview()

@Preview(name = "Выбор качества · длинное название · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun ChoiceLongTitlePreview() = ChoiceSheetLongTitlePreview()

@Preview(name = "Выбор качества · звук · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun ChoiceAudioPreview() = ChoiceSheetAudioPreview()

@Preview(name = "Выбор качества · видео", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Composable
internal fun ChoiceLoadedPreview() = ChoiceSheetLoadedPreview()
