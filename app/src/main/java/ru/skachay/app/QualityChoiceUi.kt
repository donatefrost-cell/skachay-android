@file:Suppress("ktlint:standard:function-naming")

package ru.skachay.app

import android.content.res.Configuration
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import ru.skachay.app.choice.AdvancedFormatFilter
import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.choice.FormatSelection
import ru.skachay.app.choice.FormatSelectionResolver
import ru.skachay.app.choice.SubtitleTrack
import ru.skachay.app.choice.VideoPreview
import ru.skachay.app.choice.availableVideoResolutions
import ru.skachay.app.choice.codecFamily
import ru.skachay.app.choice.filterAdvancedFormats
import ru.skachay.app.choice.formatAdvancedBitrateRu
import ru.skachay.app.choice.formatAdvancedSizeRu
import ru.skachay.app.choice.formatMediaSizeRu
import ru.skachay.app.choice.isGalleryCompatible
import ru.skachay.app.choice.qualityPresetForResolution
import ru.skachay.app.download.AudioBitrate
import ru.skachay.app.download.AudioExport
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.VideoBitrateLimit
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    initiallyScrollToFormats: Boolean = false,
    initiallyShowAllFormats: Boolean = false,
    onCancel: () -> Unit,
) {
    val maximumHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp() * 0.92f
        }
    var advancedExpanded by remember(preview, initiallyAdvancedExpanded) { mutableStateOf(initiallyAdvancedExpanded) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (initiallyScrollToFormats) 5 else 0)
    val visibleFormats =
        remember(preview, selection) { preview?.let { FormatSelectionResolver.candidates(it.formats, selection) }.orEmpty() }
    val resolutionChoices =
        remember(preview?.formats, selection.videoOnly) {
            preview?.formats?.let { availableVideoResolutions(it, selection.videoOnly) }.orEmpty()
        }
    var exactFormatFilter by remember(preview, initiallyShowAllFormats) {
        mutableStateOf(if (initiallyShowAllFormats) AdvancedFormatFilter.ALL else AdvancedFormatFilter.COMPATIBLE)
    }
    val videoGroups =
        remember(visibleFormats, exactFormatFilter) {
            filterAdvancedFormats(visibleFormats.filter { it.hasVideo }, exactFormatFilter)
                .groupBy { it.height ?: 0 }
                .toSortedMap(compareByDescending { it })
        }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maximumHeight)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            loading -> QualityHeaderSkeleton(source, slowSearch, onCancel)
            preview != null -> QualityChoiceHeader(preview, source)
            else -> QualityHeaderFallback(source)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "Видео", true to "Только звук").forEachIndexed { index, (audio, label) ->
                        SegmentedButton(
                            selected = selection.audio == audio,
                            onClick = {
                                onSelectionChanged(
                                    selection
                                        .withFilters(
                                            resolution = if (audio) null else selection.resolution,
                                            bitrateStop = if (audio) VideoBitrateLimit.ANY else selection.bitrateStop,
                                            codec = null,
                                            fps = null,
                                            container = null,
                                            audio = audio,
                                        ).copy(videoOnly = false, audioExport = AudioExport.SOURCE),
                                )
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) { Text(label, maxLines = 1) }
                    }
                }
            }

            if (selection.audio) {
                item {
                    Column(
                        Modifier.fillMaxWidth().selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Формат звука", style = MaterialTheme.typography.titleMedium)
                        listOf(AudioExport.SOURCE, AudioExport.MP3).forEach { export ->
                            val compact = export == AudioExport.MP3
                            val option = selection.copy(audioExport = export)
                            val size = preview?.let { FormatSelectionResolver.estimateBytes(it, option) }
                            AudioQualityCard(
                                title = if (compact) "Компактное" else "Лучшее",
                                subtitle =
                                    if (compact) {
                                        "mp3 · ${selection.audioBitrate.kilobits}\u00a0кбит/с"
                                    } else {
                                        audioSourceSubtitle(selectedFormat)
                                    },
                                size = formatMediaSizeRu(size),
                                selected = selection.audioExport == export,
                                onClick = { onSelectionChanged(option) },
                            )
                        }
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Качество", style = MaterialTheme.typography.titleMedium)
                        val selectedResolution =
                            selection.resolution?.takeIf { it in resolutionChoices }
                                ?: selectedFormat?.height?.takeIf { it in resolutionChoices }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val columns = if (maxWidth < 350.dp || LocalDensity.current.fontScale > 1.2f) 1 else 2
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                maxItemsInEachRow = columns,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                resolutionChoices.forEach { height ->
                                    val option =
                                        selection
                                            .withFilters(
                                                resolution = height,
                                                bitrateStop = selection.bitrateStop,
                                                codec = null,
                                                fps = null,
                                                container = null,
                                                audio = false,
                                            ).copy(
                                                qualityPreset = qualityPresetForResolution(height),
                                                videoOnly = selection.videoOnly,
                                            )
                                    val optionFormat =
                                        remember(preview, selection.videoOnly, height) {
                                            preview?.let { FormatSelectionResolver.resolve(it.formats, option) }
                                        }
                                    val optionSize =
                                        remember(preview, selection.videoOnly, height) {
                                            preview?.let { FormatSelectionResolver.estimateBytes(it, option) }
                                        }
                                    val detail = resolutionDetail(height, optionFormat)
                                    VideoResolutionCard(
                                        height = height,
                                        detail = detail,
                                        size = formatMediaSizeRu(optionSize),
                                        largeFile = optionSize != null && optionSize >= 500_000_000L,
                                        selected = selectedResolution == height,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        onSelectionChanged(option)
                                    }
                                }
                            }
                        }
                        if (resolutionChoices.isEmpty()) {
                            Text(
                                "Разрешения не найдены. Попробуйте повторить запрос к видео.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SelectedFormatCard(selection, selectedFormat, estimatedSizeBytes)
                }
            }

            if (loading) {
                items(3) { QualityCardSkeleton() }
            } else if (errorMessage != null) {
                item { Text(errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            } else if (preview != null && selectedFormat == null) {
                item {
                    Text(
                        "Подходящий формат не найден. Измените фильтры или попробуйте «Авто».",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (selection.audio && selection.audioExport == AudioExport.MP3) {
                item {
                    FilterGroup("Битрейт MP3") {
                        listOf(
                            AudioBitrate.K96,
                            AudioBitrate.K128,
                            AudioBitrate.K192,
                            AudioBitrate.K256,
                            AudioBitrate.K320,
                        ).forEach { bitrate ->
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
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { advancedExpanded = !advancedExpanded }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Все форматы и настройки", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (advancedExpanded) "Свернуть настройки" else "Битрейт, точные форматы и параметры файла",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = if (advancedExpanded) "Свернуть настройки" else "Открыть форматы и настройки",
                            modifier = Modifier.padding(start = 6.dp).size(20.dp).rotate(if (advancedExpanded) 90f else -90f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (advancedExpanded) {
                    item {
                        AdvancedFilters(preview, selection, onSelectionChanged)
                    }
                    if (!selection.audio) {
                        item {
                            VideoBitrateSlider(preview, selection, selectedFormat, estimatedSizeBytes, onSelectionChanged)
                        }
                    }
                    item {
                        Text("Точный формат", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormatFilterChip(
                                "Работает везде",
                                exactFormatFilter == AdvancedFormatFilter.COMPATIBLE,
                            ) { exactFormatFilter = AdvancedFormatFilter.COMPATIBLE }
                            FormatFilterChip(
                                "Все форматы",
                                exactFormatFilter == AdvancedFormatFilter.ALL,
                            ) { exactFormatFilter = AdvancedFormatFilter.ALL }
                        }
                    }
                    videoGroups.forEach { (height, formats) ->
                        stickyHeader(key = "format-resolution-$height") {
                            Text(
                                if (height >
                                    0
                                ) {
                                    "${height}p"
                                } else {
                                    "Разрешение неизвестно"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(
                            formats.sortedWith(
                                compareByDescending<AvailableFormat> { it.fps ?: 0 }.thenByDescending {
                                    it.videoBitrate
                                        ?: 0
                                },
                            ),
                            key = { "format-${it.id}" },
                        ) { format ->
                            AdvancedFormatRow(preview, format, selectedFormat?.id == format.id) { onFormatSelected(format) }
                        }
                    }
                    if (selection.audio) {
                        item {
                            Text(
                                "Форматы звука",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        val audioCandidates = visibleFormats.filter { !it.hasVideo && it.hasAudio }
                        val audioFormats =
                            if (exactFormatFilter == AdvancedFormatFilter.COMPATIBLE) {
                                audioCandidates.filter(::isGalleryCompatible)
                            } else {
                                audioCandidates
                            }
                        items(audioFormats, key = { "audio-${it.id}" }) { format ->
                            AdvancedFormatRow(preview, format, selectedFormat?.id == format.id) { onFormatSelected(format) }
                        }
                    }
                    if (!selection.audio) {
                        item {
                            AdvancedSwitchRow(
                                "Видео без звука",
                                selection.videoOnly,
                                enabled = preview.formats.any { it.hasVideo && !it.hasAudio },
                            ) {
                                onSelectionChanged(selection.copy(videoOnly = it, pinnedFormatId = null))
                            }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(vertical = 6.dp)) }
                    item { Text("Настройки файла", style = MaterialTheme.typography.titleMedium) }
                    item { FileNameField(fileName, fileNameInvalid, onFileNameChanged) }
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Папка сохранения", style = MaterialTheme.typography.titleSmall)
                            Text(
                                saveFolderLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = onChooseSaveFolder,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text("Выбрать папку") }
                                if (saveFolderLabel !=
                                    DEFAULT_SAVE_FOLDER_LABEL
                                ) {
                                    TextButton(
                                        onClick = onResetSaveFolder,
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) { Text("По умолчанию") }
                                }
                            }
                        }
                    }
                    item { Text("Субтитры", style = MaterialTheme.typography.titleSmall) }
                    item {
                        AdvancedSubtitles(preview, selection.subtitleLanguage, selection.subtitleAutomatic) { language, automatic ->
                            onSelectionChanged(
                                selection.copy(
                                    subtitleLanguage = language,
                                    subtitleAutomatic = automatic,
                                    embedSubtitles = if (language == null) false else selection.embedSubtitles,
                                    pinnedFormatId = selection.pinnedFormatId,
                                ),
                            )
                        }
                    }
                    item {
                        AdvancedSwitchRow(
                            "Встроить субтитры",
                            selection.subtitleLanguage != null && selection.embedSubtitles,
                            enabled = selection.subtitleLanguage != null,
                        ) { onSelectionChanged(selection.copy(embedSubtitles = it)) }
                    }
                    item {
                        AdvancedSwitchRow("Добавить миниатюру в файл", selection.embedThumbnail) {
                            onSelectionChanged(selection.copy(embedThumbnail = it))
                        }
                    }
                }
            }

            if (preview != null && !loading) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(rememberChoice, onClick = {
                                onRememberChoiceChanged(!rememberChoice)
                            })
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Запомнить выбор", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = rememberChoice, onCheckedChange = null, modifier = Modifier.size(48.dp))
                    }
                }
            }
        }

        if (selection.pinnedFormatId != null) {
            TextButton(
                onClick = {
                    onSelectionChanged(
                        selection.copy(
                            resolution = null,
                            qualityPreset = Quality.AUTO,
                            bitrateStop = VideoBitrateLimit.ANY,
                            customBitrateKbps = null,
                            codec = null,
                            fps = null,
                            container = null,
                            audio = false,
                            videoOnly = false,
                            pinnedFormatId = null,
                            audioExport = AudioExport.SOURCE,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Сбросить точный формат") }
        }

        Button(
            enabled = downloadEnabled && !loading,
            onClick = onDownload,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 4.dp),
        ) {
            val size = estimatedSizeBytes?.let { formatMediaSizeRu(it).removePrefix("≈") }
            Text(if (size == null) "Скачать" else "Скачать · $size", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun updateFilters(
    selection: FormatSelection,
    resolution: Int?,
    onSelectionChanged: (FormatSelection) -> Unit,
) {
    onSelectionChanged(
        selection.withFilters(
            resolution = resolution,
            bitrateStop = selection.bitrateStop,
            codec = selection.codec,
            fps = selection.fps,
            container = selection.container,
            audio = selection.audio,
        ),
    )
}

internal data class VideoBitrateOption(
    val caption: String,
    val maxKbps: Int?,
    val preset: VideoBitrateLimit?,
)

internal fun videoBitrateOptions(
    preview: VideoPreview?,
    selection: FormatSelection,
): List<VideoBitrateOption> {
    val actualRates =
        buildList {
            preview
                ?.formats
                .orEmpty()
                .asSequence()
                .filter {
                    val maximumHeight = selection.resolution ?: selection.qualityPreset.maximumHeight
                    it.hasVideo && (maximumHeight == null || (it.height ?: Int.MAX_VALUE) <= maximumHeight)
                }.mapNotNull { it.videoBitrate?.takeIf { bitrate -> bitrate > 0 } }
                .forEach(::add)
            selection.customBitrateKbps?.let(::add)
        }.distinct().sorted()
    val useActualRates = selection.customBitrateKbps != null || (actualRates.size >= 2 && selection.bitrateStop == VideoBitrateLimit.ANY)
    if (useActualRates) {
        val representativeRates =
            if (actualRates.size <= 4) {
                actualRates
            } else {
                listOf(
                    actualRates.first(),
                    actualRates[actualRates.lastIndex / 3],
                    actualRates[(actualRates.lastIndex * 2) / 3],
                    actualRates.last(),
                ).distinct()
            }
        return (representativeRates + listOfNotNull(selection.customBitrateKbps))
            .distinct()
            .sorted()
            .map { bitrate -> VideoBitrateOption(formatBitrateNumber(bitrate), bitrate, null) } +
            VideoBitrateOption("Макс", null, VideoBitrateLimit.ANY)
    }
    return VideoBitrateLimit.stops.map { stop -> VideoBitrateOption(stop.stopCaption, stop.maxKbps, stop) }
}

private fun formatBitrateNumber(kilobitsPerSecond: Int): String =
    formatAdvancedBitrateRu(kilobitsPerSecond)
        .removeSuffix("\u00a0Мбит/с")
        .removeSuffix("\u00a0кбит/с")

@Composable
private fun VideoBitrateSlider(
    preview: VideoPreview?,
    selection: FormatSelection,
    selectedFormat: AvailableFormat?,
    estimatedSizeBytes: Long?,
    onSelectionChanged: (FormatSelection) -> Unit,
) {
    val options =
        remember(
            preview,
            selection.resolution,
            selection.bitrateStop,
            selection.customBitrateKbps,
        ) { videoBitrateOptions(preview, selection) }
    val selectedIndex =
        options
            .indexOfFirst { option ->
                if (selection.customBitrateKbps != null) {
                    option.preset == null && option.maxKbps == selection.customBitrateKbps
                } else {
                    option.preset == selection.bitrateStop
                }
            }.coerceAtLeast(0)
    val selected = options[selectedIndex]
    val currentLabel =
        when {
            selection.customBitrateKbps != null -> formatAdvancedBitrateRu(selection.customBitrateKbps)
            selected.maxKbps == null -> "Макс"
            else -> "До ${formatAdvancedBitrateRu(selected.maxKbps)}"
        }
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val showAllStops = maxWidth >= (options.size * 52).dp * density.fontScale
        val shownIndices =
            if (showAllStops) {
                options.indices.toList()
            } else {
                options.indices.filter { it % 2 == 0 || it == selectedIndex }
            }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Битрейт", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    currentLabel,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(formatMediaSizeRu(estimatedSizeBytes ?: selectedFormat?.sizeBytes), style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = selectedIndex.toFloat(),
                onValueChange = { value ->
                    val option = options[value.toInt().coerceIn(options.indices)]
                    onSelectionChanged(
                        selection.copy(
                            bitrateStop = option.preset ?: VideoBitrateLimit.ANY,
                            customBitrateKbps = if (option.preset == null) option.maxKbps else null,
                            pinnedFormatId = null,
                        ),
                    )
                },
                valueRange = 0f..options.lastIndex.toFloat(),
                steps = (options.size - 2).coerceAtLeast(0),
                modifier =
                    Modifier.semantics {
                        contentDescription = "Битрейт"
                        stateDescription = currentLabel
                    },
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                shownIndices.forEach { index ->
                    val option = options[index]
                    val isSelected = index == selectedIndex
                    TextButton(
                        onClick = {
                            onSelectionChanged(
                                selection.copy(
                                    bitrateStop = option.preset ?: VideoBitrateLimit.ANY,
                                    customBitrateKbps = if (option.preset == null) option.maxKbps else null,
                                    pinnedFormatId = null,
                                ),
                            )
                        },
                        modifier =
                            Modifier.heightIn(min = 48.dp).semantics {
                                contentDescription = "${option.caption}, ${if (isSelected) "выбрано" else "выбрать"}"
                                stateDescription = if (isSelected) "выбрано" else "не выбрано"
                            },
                    ) {
                        Text(
                            option.caption,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFilters(
    preview: VideoPreview,
    selection: FormatSelection,
    onChange: (FormatSelection) -> Unit,
) {
    val videoFormats = preview.formats.filter { it.hasVideo }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val relevantFormats = if (selection.audio) preview.formats.filter { !it.hasVideo && it.hasAudio } else videoFormats
        val families = remember(preview, selection.audio) { relevantFormats.map(::codecFamily).distinct().sorted() }
        FilterGroup("Кодек") {
            FormatFilterChip("Все", selection.codec == null) {
                onChange(
                    selection.withFilters(
                        selection.resolution,
                        selection.bitrateStop,
                        null,
                        selection.fps,
                        selection.container,
                        selection.audio,
                    ),
                )
            }
            families.forEach { codec ->
                FormatFilterChip(codec, selection.codec == codec) {
                    onChange(
                        selection.withFilters(
                            selection.resolution,
                            selection.bitrateStop,
                            codec,
                            selection.fps,
                            selection.container,
                            selection.audio,
                        ),
                    )
                }
            }
        }
        if (!selection.audio) {
            val frameRates = remember(preview) { videoFormats.mapNotNull { it.fps }.distinct().sorted() }
            FilterGroup("Частота кадров") {
                FormatFilterChip("Любая", selection.fps == null) {
                    onChange(
                        selection.withFilters(
                            selection.resolution,
                            selection.bitrateStop,
                            selection.codec,
                            null,
                            selection.container,
                            selection.audio,
                        ),
                    )
                }
                frameRates.forEach { fps ->
                    FormatFilterChip("$fps к/с", selection.fps == fps) {
                        onChange(
                            selection.withFilters(
                                selection.resolution,
                                selection.bitrateStop,
                                selection.codec,
                                fps,
                                selection.container,
                                selection.audio,
                            ),
                        )
                    }
                }
            }
        }
        val containers = remember(preview, selection.audio) { relevantFormats.map { it.extension }.distinct().sorted() }
        FilterGroup("Контейнер") {
            FormatFilterChip("Любой", selection.container == null) {
                onChange(
                    selection.withFilters(
                        selection.resolution,
                        selection.bitrateStop,
                        selection.codec,
                        selection.fps,
                        null,
                        selection.audio,
                    ),
                )
            }
            containers.forEach { container ->
                FormatFilterChip(
                    container.uppercase(Locale.forLanguageTag("ru")),
                    selection.container == container,
                ) {
                    onChange(
                        selection.withFilters(
                            selection.resolution,
                            selection.bitrateStop,
                            selection.codec,
                            selection.fps,
                            container,
                            selection.audio,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun FormatFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(selected, onClick, label = { Text(label, maxLines = 1) }, modifier = Modifier.heightIn(min = 44.dp))
}

@Composable
private fun AudioQualityCard(
    title: String,
    subtitle: String,
    size: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .semantics {
                    contentDescription = "$title, $subtitle, $size"
                    stateDescription = if (selected) "выбрано" else "не выбрано"
                },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f).padding(start = 8.dp, end = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(size, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun audioSourceSubtitle(format: AvailableFormat?): String {
    if (format == null) return "Исходное аудио"
    if (
        format.extension.equals("m4a", ignoreCase = true) &&
        format.audioCodec?.startsWith("mp4a", ignoreCase = true) == true
    ) {
        return "m4a · исходное качество"
    }
    val codec = format.audioCodec?.substringBefore('.')?.uppercase(Locale.ROOT)
    val container = format.extension.uppercase(Locale.ROOT)
    return listOfNotNull(codec, container).joinToString(" · ") + " · исходный формат"
}

@Composable
private fun VideoResolutionCard(
    height: Int,
    detail: String,
    size: String,
    largeFile: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier
            .heightIn(min = 68.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics {
                contentDescription = "${height}p, $detail, $size"
                stateDescription = if (selected) "выбрано" else "не выбрано"
            },
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            ),
        border =
            BorderStroke(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .padding(5.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
            )
            Column(Modifier.weight(1f).padding(start = 8.dp, end = 4.dp)) {
                Text(
                    "${height}p",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                size,
                style = MaterialTheme.typography.labelLarge,
                color =
                    when {
                        largeFile -> MaterialTheme.colorScheme.error
                        size.contains("неизвестен") -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun resolutionDetail(
    height: Int,
    format: AvailableFormat?,
): String =
    format?.fps?.takeIf { it > 30 }?.let { "$it к/с" }
        ?: when {
            height >= 4320 -> "8K"
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "Full HD"
            height >= 720 -> "Рекомендуем"
            height <= 360 -> "Меньше трафика"
            else -> "Стандартное качество"
        }

@Composable
private fun SelectedFormatCard(
    selection: FormatSelection,
    format: AvailableFormat?,
    size: Long?,
) {
    val name =
        when {
            format == null -> {
                "Формат не найден"
            }

            selection.audio -> {
                if (selection.audioExport ==
                    AudioExport.MP3
                ) {
                    "MP3 · ${selection.audioBitrate.kilobits}\u00a0кбит/с"
                } else {
                    "Исходное аудио · ${format.extension.uppercase(Locale.ROOT)}"
                }
            }

            else -> {
                "${format.extension.uppercase(Locale.ROOT)} · ${codecFamily(format)}"
            }
        }
    val videoDetails =
        if (format == null || selection.audio) {
            null
        } else {
            listOfNotNull(
                format.fps?.takeIf { it > 30 }?.let { "$it к/с" },
                if (isGalleryCompatible(format)) "Работает в большинстве галерей" else "Может не воспроизводиться в галерее",
            ).joinToString(" · ")
        }
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    videoDetails ?: if (format == null) "Проверьте выбранные фильтры" else "Исходный формат аудио",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (format != null &&
                            !isGalleryCompatible(format)
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 2,
                )
            }
            Text(
                formatMediaSizeRu(size),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                color = if (size != null && size >= 500_000_000L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun QualityChoiceHeader(
    preview: VideoPreview,
    source: String,
) {
    val context = LocalContext.current
    val request =
        remember(preview.thumbnailUrl) {
            ImageRequest
                .Builder(context)
                .data(preview.thumbnailUrl)
                .crossfade(true)
                .build()
        }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(
                    width = 88.dp,
                    height = 50.dp,
                ).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                request,
                contentDescription = "Миниатюра видео",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_thumbnail_placeholder),
                error = painterResource(R.drawable.ic_thumbnail_placeholder),
            )
            preview.durationSeconds?.let { seconds ->
                Surface(
                    Modifier.align(Alignment.BottomEnd).padding(3.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    val duration =
                        if (seconds >=
                            3600
                        ) {
                            "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
                        } else {
                            "%d:%02d".format(
                                seconds / 60,
                                seconds % 60,
                            )
                        }
                    Text(
                        duration,
                        Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(preview.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                source,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QualityHeaderFallback(source: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(
                    width = 88.dp,
                    height = 50.dp,
                ).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Сведения недоступны", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun QualityHeaderSkeleton(
    source: String,
    slowSearch: Boolean,
    onCancel: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "choice header shimmer")
    val alpha by transition.animateFloat(
        0.35f,
        0.85f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "header alpha",
    )
    val shimmer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 88.dp, height = 50.dp).clip(RoundedCornerShape(12.dp)).background(shimmer))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmer),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmer),
            )
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
    val alpha by transition.animateFloat(
        0.35f,
        0.8f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "cards alpha",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(
                min = 64.dp,
            ).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(
                        0.45f,
                    ).height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Box(
                Modifier
                    .fillMaxWidth(
                        0.68f,
                    ).height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Box(
            Modifier
                .width(46.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

internal const val DEFAULT_SAVE_FOLDER_LABEL = "Фото, видео и музыка · папка «Скачай»"

@Composable
private fun AdvancedFormatRow(
    preview: VideoPreview,
    format: AvailableFormat,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val compatible = isGalleryCompatible(format)
    val bitrate = if (format.hasVideo) format.videoBitrate else format.audioBitrate
    val estimatedSize =
        format.sizeBytes ?: preview.durationSeconds?.takeIf { it > 0 }?.let { seconds ->
            bitrate?.toLong()?.times(125L)?.times(seconds.toLong())
        }
    val compatibilityHint = if (compatible) "Работает везде" else "Может не воспроизводиться в галерее"
    val sizeLabel = if (estimatedSize == null) "—" else formatAdvancedSizeRu(estimatedSize)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
        shape = RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                if (selected) 1.5.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        colors =
            CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .semantics(mergeDescendants = true) {
                    stateDescription = if (selected) "Выбрано. $compatibilityHint" else compatibilityHint
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f).padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FormatMetadataChip(format.fps?.let { "$it к/с" } ?: "— к/с")
                    FormatMetadataChip(codecFamily(format))
                    FormatMetadataChip(format.extension.uppercase(Locale.ROOT))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (bitrate == null || bitrate <= 0) "—" else formatAdvancedBitrateRu(bitrate),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        painter = painterResource(if (compatible) R.drawable.ic_format_compatible else R.drawable.ic_format_warning),
                        contentDescription = compatibilityHint,
                        modifier = Modifier.padding(start = 4.dp).size(18.dp),
                        tint = if (compatible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        sizeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatMetadataChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
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
            if (track.manual) {
                AdvancedOptionRow(
                    "${subtitleLanguageName(track.language)} · авторские",
                    subtitleLanguage == track.language && !subtitleAutomatic,
                ) {
                    onSubtitleChanged(track.language, false)
                }
            }
            if (track.automatic) {
                AdvancedOptionRow(
                    "${subtitleLanguageName(track.language)} · автоматические",
                    subtitleLanguage == track.language && subtitleAutomatic,
                ) {
                    onSubtitleChanged(track.language, true)
                }
            }
        }
    }
}

private fun subtitleLanguageName(language: String): String {
    val locale = Locale.forLanguageTag(language)
    return locale.getDisplayName(Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase(Locale.forLanguageTag("ru")) }
}

@Composable
private fun AdvancedOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AdvancedSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(checked, enabled = enabled, onClick = {
                onCheckedChange(!checked)
            })
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}

@Composable
private fun FileNameField(
    value: String,
    invalid: Boolean,
    onChange: (String) -> Unit,
) {
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

internal enum class ChoicePreviewVariant { LOADED, LOADING, LONG_TITLE, AUDIO, ADVANCED, ADVANCED_ALL }

@Composable
internal fun ChoiceSheetPreview(variant: ChoicePreviewVariant = ChoicePreviewVariant.LOADED) {
    val maximumHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp() * 0.92f
        }
    val longTitle = variant == ChoicePreviewVariant.LONG_TITLE
    val sample =
        remember(longTitle) {
            VideoPreview(
                title =
                    if (longTitle) {
                        "Очень длинное название ролика о поездке по красивым местам России и подготовке к путешествию"
                    } else {
                        "Путешествие по Карелии"
                    },
                author = "Канал автора",
                durationSeconds = 245,
                thumbnailUrl = null,
                formats =
                    listOf(
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
    val advanced = variant == ChoicePreviewVariant.ADVANCED || variant == ChoicePreviewVariant.ADVANCED_ALL
    val selection =
        remember(variant) {
            val quality = if (advanced) Quality.P720 else Quality.AUTO
            val resolution = if (advanced) null else quality.maximumHeight
            FormatSelection(
                resolution = resolution,
                qualityPreset = quality,
                audio = variant == ChoicePreviewVariant.AUDIO,
                audioExport = AudioExport.SOURCE,
            )
        }
    val selected = remember(selection) { FormatSelectionResolver.resolve(sample.formats, selection) }
    SkachayPreview {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                Surface(
                    Modifier.fillMaxWidth().heightIn(max = maximumHeight),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        Box(
                            Modifier
                                .padding(
                                    top = 10.dp,
                                ).size(
                                    width = 32.dp,
                                    height = 4.dp,
                                ).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                                .align(Alignment.CenterHorizontally),
                        )
                        QualityChoiceSheetContent(
                            preview = sample.takeUnless { loading },
                            source = "YouTube",
                            loading = loading,
                            slowSearch = false,
                            errorMessage = null,
                            advancedMode = advanced,
                            selection = selection,
                            selectedFormat = selected,
                            rememberChoice = false,
                            estimatedSizeBytes = FormatSelectionResolver.estimateBytes(sample, selection),
                            downloadEnabled = !loading,
                            fileName = "",
                            fileNameInvalid = false,
                            saveFolderLabel = DEFAULT_SAVE_FOLDER_LABEL,
                            onSelectionChanged = {},
                            onFormatSelected = {},
                            onRememberChoiceChanged = {},
                            onFileNameChanged = {},
                            onChooseSaveFolder = {},
                            onResetSaveFolder = {},
                            onSubtitleChanged = { _, _ -> },
                            onDownload = {},
                            initiallyAdvancedExpanded = advanced,
                            initiallyScrollToFormats = advanced,
                            initiallyShowAllFormats = variant == ChoicePreviewVariant.ADVANCED_ALL,
                            onCancel = {},
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
