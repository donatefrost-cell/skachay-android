package ru.skachay.app

import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.util.Locale
import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.choice.FormatSelection
import ru.skachay.app.choice.FormatSelectionResolver
import ru.skachay.app.choice.SubtitleTrack
import ru.skachay.app.choice.VideoPreview
import ru.skachay.app.choice.codecFamily
import ru.skachay.app.choice.formatAdvancedBitrateRu
import ru.skachay.app.choice.formatAdvancedSizeRu
import ru.skachay.app.choice.formatMediaSizeRu
import ru.skachay.app.choice.isGalleryCompatible
import ru.skachay.app.download.AudioBitrate
import ru.skachay.app.download.AudioExport
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.VideoBitrateLimit

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QualityChoiceSheetContent(
    preview: VideoPreview?,
    source: String,
    loading: Boolean,
    slowSearch: Boolean,
    errorMessage: String?,
    advancedMode: Boolean,
    selection: FormatSelection,
    selectedFormat: AvailableFormat?,
    rememberChoice: Boolean,
    estimatedSizeBytes: Long?,
    downloadEnabled: Boolean,
    fileName: String,
    fileNameInvalid: Boolean,
    saveFolderLabel: String,
    onSelectionChanged: (FormatSelection) -> Unit,
    onFormatSelected: (AvailableFormat) -> Unit,
    onRememberChoiceChanged: (Boolean) -> Unit,
    onFileNameChanged: (String) -> Unit,
    onChooseSaveFolder: () -> Unit,
    onResetSaveFolder: () -> Unit,
    onSubtitleChanged: (String?, Boolean) -> Unit,
    onDownload: () -> Unit,
    initiallyAdvancedExpanded: Boolean = false,
    onCancel: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maximumHeight = (configuration.screenHeightDp * 0.92f).dp
    var advancedExpanded by remember(preview, initiallyAdvancedExpanded) { mutableStateOf(initiallyAdvancedExpanded) }
    Column(
        Modifier.fillMaxWidth().heightIn(max = maximumHeight).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            loading -> QualityHeaderSkeleton(source, slowSearch, onCancel)
            preview != null -> QualityChoiceHeader(preview, source)
            else -> QualityHeaderFallback(source)
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "Видео", true to "Только звук").forEachIndexed { index, (audio, label) ->
                        SegmentedButton(
                            selected = selection.audio == audio,
                            onClick = {
                                onSelectionChanged(selection.withFilters(
                                    resolution = if (audio) null else selection.resolution,
                                    bitrateStop = if (audio) VideoBitrateLimit.ANY else selection.bitrateStop,
                                    codec = null,
                                    fps = null,
                                    container = null,
                                    audio = audio,
                                ).copy(videoOnly = false, audioExport = AudioExport.SOURCE))
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) { Text(label, maxLines = 1) }
                    }
                }
            }

            if (selection.audio) {
                item {
                    Text("Формат звука", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AudioExport.entries.filter { it == AudioExport.SOURCE || it == AudioExport.MP3 }.forEach { export ->
                            val label = if (export == AudioExport.SOURCE) "Исходное · m4a" else "MP3 · ${selection.audioBitrate.kilobits} кбит/с"
                            FilterChip(
                                selected = selection.audioExport == export,
                                onClick = { onSelectionChanged(selection.copy(audioExport = export)) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            } else {
                item {
                    Text("Качество", style = MaterialTheme.typography.titleMedium)
                    val resolutions = remember(preview) {
                        preview?.formats.orEmpty().filter { it.hasVideo }.mapNotNull { it.height }.distinct().sortedDescending()
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selection.resolution == null,
                            onClick = { updateFilters(selection, resolution = null, onSelectionChanged = onSelectionChanged) },
                            label = { Text("Авто", maxLines = 1) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                        resolutions.forEach { height ->
                            FilterChip(
                                selected = selection.resolution == height,
                                onClick = { updateFilters(selection, resolution = height, onSelectionChanged = onSelectionChanged) },
                                label = { Text("${height}p", maxLines = 1) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    SelectedFormatCard(selection, selectedFormat, estimatedSizeBytes)
                }
            }

            if (loading) {
                items(3) { QualityCardSkeleton() }
            } else if (errorMessage != null) {
                item { Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            } else if (preview != null && selectedFormat == null) {
                item { Text("Подходящий формат не найден. Измените фильтры или попробуйте «Авто».", style = MaterialTheme.typography.bodyMedium) }
            }

            if (selection.audio && selection.audioExport == AudioExport.MP3) {
                item {
                    FilterGroup("Битрейт MP3") {
                        listOf(AudioBitrate.K96, AudioBitrate.K128, AudioBitrate.K192, AudioBitrate.K256, AudioBitrate.K320).forEach { bitrate ->
                            FilterChip(
                                selected = selection.audioBitrate == bitrate,
                                onClick = { onSelectionChanged(selection.copy(audioBitrate = bitrate)) },
                                label = { Text("${bitrate.kilobits}", maxLines = 1) },
                                modifier = Modifier.heightIn(min = 44.dp),
                            )
                        }
                    }
                }
            }

            if (advancedMode && preview != null && !loading) {
                item {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { advancedExpanded = !advancedExpanded }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (advancedExpanded) "Скрыть дополнительно" else "Дополнительно", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("Фильтры и точные форматы", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            painterResource(R.drawable.ic_back), contentDescription = if (advancedExpanded) "Свернуть" else "Развернуть",
                            modifier = Modifier.padding(start = 6.dp).size(20.dp).rotate(if (advancedExpanded) 90f else -90f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (advancedExpanded) {
                    item {
                        AdvancedFilters(preview, selection, onSelectionChanged)
                    }
                    val visibleFormats = remember(preview, selection) { FormatSelectionResolver.candidates(preview.formats, selection) }
                    val videoGroups = remember(visibleFormats) {
                        visibleFormats.filter { it.hasVideo }.groupBy { it.height ?: 0 }.toSortedMap(compareByDescending { it })
                    }
                    videoGroups.forEach { (height, formats) ->
                        item(key = "format-resolution-$height") {
                            Text(if (height > 0) "${height}p" else "Разрешение неизвестно", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        items(formats.sortedWith(compareByDescending<AvailableFormat> { it.fps ?: 0 }.thenByDescending { it.videoBitrate ?: 0 }), key = { "format-${it.id}" }) { format ->
                            AdvancedFormatRow(preview, format, selectedFormat?.id == format.id) { onFormatSelected(format) }
                        }
                    }
                    if (selection.audio) {
                        item { Text("Форматы звука", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
                        val audioFormats = visibleFormats.filter { !it.hasVideo && it.hasAudio }
                        items(audioFormats, key = { "audio-${it.id}" }) { format ->
                            AdvancedFormatRow(preview, format, selectedFormat?.id == format.id) { onFormatSelected(format) }
                        }
                    }
                    if (!selection.audio) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                                    selected = selection.videoOnly,
                                    enabled = preview.formats.any { it.hasVideo && !it.hasAudio },
                                    onClick = { onSelectionChanged(selection.copy(videoOnly = !selection.videoOnly, pinnedFormatId = null)) },
                                ).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = selection.videoOnly, onCheckedChange = null, enabled = preview.formats.any { it.hasVideo && !it.hasAudio })
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text("Видео без звука", style = MaterialTheme.typography.titleSmall)
                                    Text("Без отдельной аудиодорожки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
                    item { Text("Настройки файла", style = MaterialTheme.typography.titleMedium) }
                    item { FileNameField(fileName, fileNameInvalid, onFileNameChanged) }
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Папка сохранения", style = MaterialTheme.typography.titleSmall)
                            Text(saveFolderLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onChooseSaveFolder, modifier = Modifier.heightIn(min = 48.dp)) { Text("Выбрать папку") }
                                if (saveFolderLabel != DEFAULT_SAVE_FOLDER_LABEL) TextButton(onClick = onResetSaveFolder, modifier = Modifier.heightIn(min = 48.dp)) { Text("По умолчанию") }
                            }
                        }
                    }
                    item { Text("Субтитры", style = MaterialTheme.typography.titleSmall) }
                    item { AdvancedSubtitles(preview, selection.subtitleLanguage, selection.subtitleAutomatic) { language, automatic ->
                        onSelectionChanged(selection.copy(
                            subtitleLanguage = language,
                            subtitleAutomatic = automatic,
                            embedSubtitles = if (language == null) false else selection.embedSubtitles,
                            pinnedFormatId = selection.pinnedFormatId,
                        ))
                    } }
                    item {
                        AdvancedSwitchRow("Встроить субтитры", selection.subtitleLanguage != null && selection.embedSubtitles,
                            enabled = selection.subtitleLanguage != null) { onSelectionChanged(selection.copy(embedSubtitles = it)) }
                    }
                    item { AdvancedSwitchRow("Добавить миниатюру в файл", selection.embedThumbnail) {
                        onSelectionChanged(selection.copy(embedThumbnail = it))
                    } }
                }
            }

            if (preview != null && !loading) {
                item {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(rememberChoice, onClick = { onRememberChoiceChanged(!rememberChoice) }).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = rememberChoice, onCheckedChange = null)
                        Text("Запомнить выбор", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
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

private fun updateFilters(selection: FormatSelection, resolution: Int?, onSelectionChanged: (FormatSelection) -> Unit) {
    onSelectionChanged(selection.withFilters(
        resolution = resolution,
        bitrateStop = selection.bitrateStop,
        codec = selection.codec,
        fps = selection.fps,
        container = selection.container,
        audio = selection.audio,
    ))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFilters(preview: VideoPreview, selection: FormatSelection, onChange: (FormatSelection) -> Unit) {
    val videoFormats = preview.formats.filter { it.hasVideo }
    val availableBitrates = videoFormats.mapNotNull { it.videoBitrate }
    val maximumBitrate = availableBitrates.maxOrNull()
    val stops = VideoBitrateLimit.stops.filter { it.maxKbps == null || maximumBitrate == null || it.maxKbps <= maximumBitrate || it == selection.bitrateStop }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!selection.audio) {
            FilterGroup("Ограничение битрейта") {
                stops.forEach { stop ->
                    val label = if (stop.maxKbps == null) "Макс" else "${stop.stopCaption} Мбит/с"
                    FilterChip(
                        selected = selection.bitrateStop == stop,
                        onClick = { onChange(selection.withFilters(selection.resolution, stop, selection.codec, selection.fps, selection.container, selection.audio)) },
                        label = { Text(label, maxLines = 1) }, modifier = Modifier.heightIn(min = 44.dp),
                    )
                }
            }
        }
        val relevantFormats = if (selection.audio) preview.formats.filter { !it.hasVideo && it.hasAudio } else videoFormats
        val families = remember(preview, selection.audio) { relevantFormats.map(::codecFamily).distinct().sorted() }
        FilterGroup("Кодек") {
            FormatFilterChip("Все", selection.codec == null) {
                onChange(selection.withFilters(selection.resolution, selection.bitrateStop, null, selection.fps, selection.container, selection.audio))
            }
            families.forEach { codec -> FormatFilterChip(codec, selection.codec == codec) {
                onChange(selection.withFilters(selection.resolution, selection.bitrateStop, codec, selection.fps, selection.container, selection.audio))
            } }
        }
        if (!selection.audio) {
            val frameRates = remember(preview) { videoFormats.mapNotNull { it.fps }.distinct().sorted() }
            FilterGroup("Частота кадров") {
                FormatFilterChip("Любая", selection.fps == null) {
                    onChange(selection.withFilters(selection.resolution, selection.bitrateStop, selection.codec, null, selection.container, selection.audio))
                }
                frameRates.forEach { fps -> FormatFilterChip("$fps к/с", selection.fps == fps) {
                    onChange(selection.withFilters(selection.resolution, selection.bitrateStop, selection.codec, fps, selection.container, selection.audio))
                } }
            }
        }
        val containers = remember(preview, selection.audio) { relevantFormats.map { it.extension }.distinct().sorted() }
        FilterGroup("Контейнер") {
            FormatFilterChip("Любой", selection.container == null) {
                onChange(selection.withFilters(selection.resolution, selection.bitrateStop, selection.codec, selection.fps, null, selection.audio))
            }
            containers.forEach { container -> FormatFilterChip(container.uppercase(Locale.forLanguageTag("ru")), selection.container == container) {
                onChange(selection.withFilters(selection.resolution, selection.bitrateStop, selection.codec, selection.fps, container, selection.audio))
            } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun FormatFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, label = { Text(label, maxLines = 1) }, modifier = Modifier.heightIn(min = 44.dp))
}

@Composable
private fun SelectedFormatCard(selection: FormatSelection, format: AvailableFormat?, size: Long?) {
    val name = when {
        format == null -> "Формат не найден"
        selection.audio -> if (selection.audioExport == AudioExport.MP3) "MP3 · ${selection.audioBitrate.kilobits} кбит/с" else "Исходное аудио · ${format.extension.uppercase(Locale.ROOT)}"
        else -> "Видео · ${format.height ?: "?"}p" + (format.fps?.takeIf { it > 30 }?.let { " · $it к/с" } ?: "")
    }
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = format != null, onClick = null)
            Column(Modifier.weight(1f).padding(start = 8.dp, end = 8.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        format == null -> "Проверьте выбранные фильтры"
                        isGalleryCompatible(format) -> "Работает в большинстве галерей"
                        else -> "Может не воспроизводиться в галерее"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (format != null && !isGalleryCompatible(format)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Text(formatMediaSizeRu(size), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                color = if (size != null && size >= 500_000_000L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun QualityChoiceHeader(preview: VideoPreview, source: String) {
    val context = LocalContext.current
    val request = remember(preview.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(preview.thumbnailUrl)
            .crossfade(true)
            .build()
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 88.dp, height = 50.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(request, contentDescription = "Миниатюра видео", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_thumbnail_placeholder), error = painterResource(R.drawable.ic_thumbnail_placeholder))
            preview.durationSeconds?.let { seconds ->
                Surface(Modifier.align(Alignment.BottomEnd).padding(3.dp), color = Color.Black.copy(alpha = 0.78f), shape = RoundedCornerShape(4.dp)) {
                    val duration = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
                    Text(duration, Modifier.padding(horizontal = 4.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(preview.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val transition = rememberInfiniteTransition(label = "choice header shimmer")
    val alpha by transition.animateFloat(0.35f, 0.85f, animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "header alpha")
    val shimmer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 88.dp, height = 50.dp).clip(RoundedCornerShape(12.dp)).background(shimmer))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.82f).height(18.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
            Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(shimmer))
        }
    }
    if (slowSearch) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ищу дольше обычного…", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onCancel) { Text("Отмена") }
        }
    } else {
        Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QualityCardSkeleton() {
    val transition = rememberInfiniteTransition(label = "choice cards shimmer")
    val alpha by transition.animateFloat(0.35f, 0.8f, animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "cards alpha")
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

internal const val DEFAULT_SAVE_FOLDER_LABEL = "Фото, видео и музыка · папка «Скачай»"

@Composable
private fun AdvancedFormatRow(preview: VideoPreview, format: AvailableFormat, selected: Boolean, onClick: () -> Unit) {
    val compatible = isGalleryCompatible(format)
    val bitrate = if (format.hasVideo) format.videoBitrate else format.audioBitrate
    val estimatedSize = format.sizeBytes ?: preview.durationSeconds?.takeIf { it > 0 }?.let { seconds ->
        bitrate?.toLong()?.times(125L)?.times(seconds.toLong())
    }
    val technical = buildList {
        format.fps?.let { add("$it к/с") }
        add(codecFamily(format))
        add(format.extension.uppercase(Locale.ROOT))
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f).padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(technical.joinToString(" · "), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatAdvancedBitrateRu(bitrate), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(if (compatible) "Работает везде" else "Может не воспроизводиться в галерее", style = MaterialTheme.typography.labelSmall,
                    color = if (compatible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(formatAdvancedSizeRu(estimatedSize), style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun AdvancedSubtitles(preview: VideoPreview, subtitleLanguage: String?, subtitleAutomatic: Boolean, onSubtitleChanged: (String?, Boolean) -> Unit) {
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
    return locale.getDisplayName(Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase(Locale.forLanguageTag("ru")) }
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

internal enum class ChoicePreviewVariant { LOADED, LOADING, LONG_TITLE, AUDIO, ADVANCED }

@Composable
internal fun ChoiceSheetPreview(variant: ChoicePreviewVariant = ChoicePreviewVariant.LOADED) {
    val longTitle = variant == ChoicePreviewVariant.LONG_TITLE
    val sample = remember(longTitle) {
        VideoPreview(
            title = if (longTitle) "Очень длинное название ролика о поездке по красивым местам России и подготовке к путешествию" else "Путешествие по Карелии",
            author = "Канал автора",
            durationSeconds = 245,
            thumbnailUrl = null,
            formats = listOf(
                AvailableFormat("2160", "mp4", "avc1.640028", null, 2160, 30, null, 220_000_000, videoBitrate = 12_000),
                AvailableFormat("1080", "mp4", "avc1.640028", null, 1080, 30, null, 90_000_000, videoBitrate = 4_000),
                AvailableFormat("720", "mp4", "avc1.4d401f", null, 720, 30, null, 30_000_000, videoBitrate = 2_500),
                AvailableFormat("720-vp9", "webm", "vp09.00.40.08", null, 720, 60, null, 40_000_000, videoBitrate = 4_000),
                AvailableFormat("360", "mp4", "avc1.4d401e", null, 360, 30, null, 10_000_000, videoBitrate = 700),
                AvailableFormat("m4a", "m4a", null, "mp4a.40.2", null, null, 128, 4_000_000),
            ),
            subtitles = listOf(SubtitleTrack("ru", manual = true, automatic = true)),
        )
    }
    val loading = variant == ChoicePreviewVariant.LOADING
    val selection = remember(variant) { FormatSelection(qualityPreset = Quality.P720, audio = variant == ChoicePreviewVariant.AUDIO, audioExport = AudioExport.SOURCE) }
    val selected = remember(selection) { FormatSelectionResolver.resolve(sample.formats, selection) }
    SkachayPreview {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                Surface(Modifier.fillMaxWidth().fillMaxSize(0.92f), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surface) {
                    Column {
                        Box(Modifier.padding(top = 10.dp).size(width = 32.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterHorizontally))
                        QualityChoiceSheetContent(
                            preview = sample.takeUnless { loading }, source = "YouTube", loading = loading, slowSearch = false, errorMessage = null,
                            advancedMode = true, selection = selection, selectedFormat = selected, rememberChoice = false,
                            estimatedSizeBytes = FormatSelectionResolver.estimateBytes(sample, selection), downloadEnabled = !loading,
                            fileName = "", fileNameInvalid = false, saveFolderLabel = DEFAULT_SAVE_FOLDER_LABEL,
                            onSelectionChanged = {}, onFormatSelected = {}, onRememberChoiceChanged = {}, onFileNameChanged = {},
                            onChooseSaveFolder = {}, onResetSaveFolder = {}, onSubtitleChanged = { _, _ -> }, onDownload = {},
                            initiallyAdvancedExpanded = variant == ChoicePreviewVariant.ADVANCED, onCancel = {},
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Выбор · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Composable
internal fun ChoiceLoadedPreview() = ChoiceSheetPreview()

@Preview(name = "Выбор · загрузка · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun ChoiceLoadingPreview() = ChoiceSheetPreview(ChoicePreviewVariant.LOADING)

@Preview(name = "Выбор · длинное название · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun ChoiceLongTitlePreview() = ChoiceSheetPreview(ChoicePreviewVariant.LONG_TITLE)

@Preview(name = "Выбор · аудио · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun ChoiceAudioPreview() = ChoiceSheetPreview(ChoicePreviewVariant.AUDIO)

@Preview(name = "Выбор · дополнительно · 200%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 2f, showSystemUi = true)
@Composable
internal fun ChoiceAdvancedPreview() = ChoiceSheetPreview(ChoicePreviewVariant.ADVANCED)
