package ru.skachay.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.skachay.app.choice.AvailableFormat
import ru.skachay.app.choice.CachedPreview
import ru.skachay.app.choice.PreviewStore
import ru.skachay.app.choice.SocialPreview
import ru.skachay.app.choice.SocialPreviewStore
import ru.skachay.app.choice.VideoPreview
import ru.skachay.app.choice.QualityAudioKind
import ru.skachay.app.choice.QualityChoiceBuilder
import ru.skachay.app.choice.QualityVideoCard
import ru.skachay.app.choice.QualityVideoTier
import ru.skachay.app.download.AudioBitrate
import ru.skachay.app.download.AudioExport
import ru.skachay.app.download.InstagramLink
import ru.skachay.app.download.ResolvedMedia
import ru.skachay.app.download.SavedFileName
import ru.skachay.app.download.SourceType
import ru.skachay.app.download.TikTokLink
import ru.skachay.app.download.YouTubeFormatSelector
import ru.skachay.app.download.YouTubePlaylistLink
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.SettingsRepository
import ru.skachay.app.settings.UserMode
import ru.skachay.app.settings.VideoBitrateLimit
import ru.skachay.app.SkachayTheme

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
        val isPurePlaylist = YouTubePlaylistLink.isPurePlaylist(url)
        val isSocial = InstagramLink.supports(url) || TikTokLink.supports(url)
        if (isPurePlaylist) {
            val requestedQuality = Quality.entries.firstOrNull { it.name == intent.getStringExtra(DownloadService.EXTRA_QUALITY) }
            setContent {
                SkachayTheme {
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text("Скачать плейлист целиком?") },
                        text = {
                            Text(if (requestedQuality == null) {
                                "Будут загружены все доступные видео с качеством из настроек. Размер списка заранее неизвестен."
                            } else {
                                "Будут загружены все доступные видео в качестве «${requestedQuality.label}». Размер списка заранее неизвестен."
                            })
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                startDownload(this@ChoiceActivity, url, requestedQuality, usePreview = false, allowPlaylist = true)
                                finish()
                            }) { Text("Скачать плейлист") }
                        },
                        dismissButton = { TextButton(onClick = { finish() }) { Text("Отмена") } },
                    )
                }
            }
            return
        }
        setContent {
            SkachayTheme {
                val settingsRepository = remember { SettingsRepository.forContext(applicationContext) }
                val coroutineScope = rememberCoroutineScope()
                var quality by remember { mutableStateOf(initial) }
                var videoBitrateLimit by remember { mutableStateOf(VideoBitrateLimit.ANY) }
                var selected by remember { mutableStateOf<AvailableFormat?>(null) }
                var selectedVideoTier by remember { mutableStateOf<QualityVideoTier?>(null) }
                var videoOnly by remember { mutableStateOf(false) }
                var audioMode by remember { mutableStateOf(false) }
                var showAdvancedPage by remember { mutableStateOf(false) }
                var slowSearch by remember { mutableStateOf(false) }
                var rememberChoice by remember { mutableStateOf(false) }
                var advancedMode by remember { mutableStateOf(false) }
                var selectedAudioKind by remember { mutableStateOf<QualityAudioKind?>(null) }
                var audioExport by remember { mutableStateOf(AudioExport.SOURCE) }
                var audioBitrate by remember { mutableStateOf(AudioBitrate.K128) }
                var subtitleLanguage by remember { mutableStateOf<String?>(null) }
                var subtitleAutomatic by remember { mutableStateOf(false) }
                var embedSubtitles by remember { mutableStateOf(false) }
                var embedThumbnail by remember { mutableStateOf(false) }
                var selectedItems by remember(url) { mutableStateOf<Set<Int>>(emptySet()) }
                var fileName by remember(url) { mutableStateOf(intent.getStringExtra(DownloadService.EXTRA_FILE_NAME).orEmpty().take(256)) }
                val cleanedFileName = remember(fileName) { SavedFileName.clean(fileName) }
                val invalidFileName = fileName.isNotBlank() && cleanedFileName == null
                var state by remember(url) { mutableStateOf<PreviewState>(if (isYouTube || isSocial) PreviewState.Loading else PreviewState.NotNeeded) }
                LaunchedEffect(url) {
                    if (state is PreviewState.Loading) {
                        state = try {
                            val appSettings = withContext(Dispatchers.IO) { settingsRepository.settings.first() }
                            videoBitrateLimit = appSettings.videoBitrateLimit
                            rememberChoice = appSettings.rememberQualityChoice
                            advancedMode = appSettings.mode == UserMode.ADVANCED
                            if (isYouTube) {
                                val cached = withContext(Dispatchers.IO) { PreviewStore.getOrFetch(applicationContext, url) }
                                val cards = QualityChoiceBuilder.videoCards(cached.preview)
                                val requestedId = intent.getStringExtra(DownloadService.EXTRA_FORMAT_ID)
                                val initialVideo = QualityChoiceBuilder.initialVideoChoice(
                                    cards, quality, appSettings.rememberQualityChoice, appSettings.lastVideoQualityTier,
                                )
                                val initialAudio = QualityChoiceBuilder.initialAudioChoice(
                                    QualityChoiceBuilder.audioCards(cached.preview), appSettings.rememberQualityChoice, appSettings.lastAudioQualityKind,
                                )
                                selectedAudioKind = initialAudio?.kind
                                val restoreAudioMode = appSettings.rememberQualityChoice && appSettings.lastQualityWasAudio
                                val requestedAudio = cached.preview.formats.firstOrNull { it.id == requestedId && !it.hasVideo && it.hasAudio }
                                val requestedVideo = cards.firstOrNull { it.video.id == requestedId }
                                if (requestedAudio != null || restoreAudioMode) {
                                    audioMode = true
                                    selected = requestedAudio ?: initialAudio?.source
                                } else {
                                    selected = requestedVideo?.video ?: initialVideo?.video
                                }
                                selectedVideoTier = requestedVideo?.tier ?: initialVideo?.tier
                                if (audioMode) {
                                    audioExport = if (selectedAudioKind == QualityAudioKind.COMPACT_MP3) AudioExport.MP3 else AudioExport.SOURCE
                                }
                                PreviewState.Ready(cached)
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
                LaunchedEffect(url) {
                    delay(8_000)
                    if (state is PreviewState.Loading) slowSearch = true
                }
                LaunchedEffect(state) { if (state !is PreviewState.Loading) slowSearch = false }
                var confirmLargeDownload by remember(url) { mutableStateOf(false) }
                val previewForEstimate = (state as? PreviewState.Ready)?.cached?.preview
                val videoChoices = remember(previewForEstimate) { previewForEstimate?.let { QualityChoiceBuilder.videoCards(it) }.orEmpty() }
                val audioChoices = remember(previewForEstimate) { previewForEstimate?.let { QualityChoiceBuilder.audioCards(it) }.orEmpty() }
                val selectedVideoChoice = videoChoices.firstOrNull { it.video.id == selected?.id }
                val selectedAudioChoice = audioChoices.firstOrNull { it.kind == selectedAudioKind }
                val estimatedSize = when {
                    isYouTube && audioMode -> selectedAudioChoice?.estimatedSizeBytes
                    isYouTube && !videoOnly && selectedVideoChoice != null -> selectedVideoChoice.estimatedSizeBytes
                    isYouTube -> previewForEstimate?.let {
                        YouTubeFormatSelector.estimate(it, quality, selected, videoOnly, videoBitrateLimit)
                    }
                    else -> null
                }
                val beginDownload: (Boolean) -> Unit = { downloadPlaylist ->
                    val social = state as? PreviewState.SocialReady
                    val playlistQuality = if (downloadPlaylist && !audioMode) when (selected?.height ?: Int.MAX_VALUE) {
                        in 0..360 -> Quality.P360
                        in 361..480 -> Quality.P480
                        in 481..720 -> Quality.P720
                        in 721..1080 -> Quality.P1080
                        in 1081..1440 -> Quality.P1440
                        in 1441..2160 -> Quality.P2160
                        else -> Quality.MAX
                    } else quality
                    startDownload(this@ChoiceActivity, url, playlistQuality, usePreview = state is PreviewState.Ready && !downloadPlaylist,
                        formatId = selected?.id.takeUnless { downloadPlaylist }, videoOnly = videoOnly && !downloadPlaylist, socialPreviewId = social?.cached?.id,
                        selectedItems = social?.let { selectedItems.sorted().toIntArray() }, fileName = cleanedFileName,
                        audioExport = audioExport, audioBitrate = audioBitrate.kilobits,
                        subtitleLanguage = subtitleLanguage, subtitleAutomatic = subtitleAutomatic,
                        embedSubtitles = embedSubtitles, embedThumbnail = embedThumbnail,
                        allowPlaylist = downloadPlaylist, playlistAudioOnly = downloadPlaylist && audioMode)
                    finish()
                }
                val requestDownload = {
                    when {
                        estimatedSize != null && estimatedSize > 500_000_000L -> confirmLargeDownload = true
                        else -> beginDownload(false)
                    }
                }
                BackHandler(enabled = showAdvancedPage) { showAdvancedPage = false }
                val selectedTier = selectedVideoTier ?: selectedVideoChoice?.tier
                val downloadLabel = "Скачать" + estimatedSize?.let { " · ${ru.skachay.app.choice.formatMediaSizeRu(it).removePrefix("≈")}" }.orEmpty()
                if (showAdvancedPage && previewForEstimate != null) {
                    AdvancedFormatsPage(
                        preview = previewForEstimate,
                        source = "YouTube",
                        selected = selected,
                        videoOnly = videoOnly,
                        fileName = fileName,
                        fileNameInvalid = invalidFileName,
                        subtitleLanguage = subtitleLanguage,
                        subtitleAutomatic = subtitleAutomatic,
                        embedSubtitles = embedSubtitles,
                        embedThumbnail = embedThumbnail,
                        downloadLabel = downloadLabel,
                        downloadEnabled = !invalidFileName,
                        onBack = { showAdvancedPage = false },
                        onSelectFormat = { format ->
                            selected = format
                            videoOnly = false
                            if (!format.hasVideo && format.hasAudio) {
                                audioMode = true
                                selectedAudioKind = audioChoices.firstOrNull { it.source.id == format.id }?.kind ?: QualityAudioKind.BEST_M4A
                                audioExport = AudioExport.SOURCE
                            } else {
                                audioMode = false
                                audioExport = AudioExport.SOURCE
                                selectedVideoTier = videoChoices.firstOrNull { it.video.id == format.id }?.tier ?: selectedVideoTier
                            }
                        },
                        onVideoOnlyChanged = { videoOnly = it },
                        onFileNameChanged = { fileName = it },
                        onSubtitleChanged = { language, automatic -> subtitleLanguage = language; subtitleAutomatic = automatic; if (language == null) embedSubtitles = false },
                        onEmbedSubtitlesChanged = { embedSubtitles = it },
                        onEmbedThumbnailChanged = { embedThumbnail = it },
                        onDownload = requestDownload,
                    )
                    if (confirmLargeDownload && estimatedSize != null) {
                        AlertDialog(
                            onDismissRequest = { confirmLargeDownload = false },
                            title = { Text("Большой файл") },
                            text = { Text("Размер видео примерно ${android.text.format.Formatter.formatShortFileSize(this@ChoiceActivity, estimatedSize)}. Продолжить загрузку?") },
                            confirmButton = { TextButton(onClick = { confirmLargeDownload = false; beginDownload(false) }) { Text("Скачать всё равно") } },
                            dismissButton = { TextButton(onClick = { confirmLargeDownload = false }) { Text("Отмена") } },
                        )
                    }
                } else if (isYouTube) {
                    ModalBottomSheet(
                        onDismissRequest = { finish() },
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    ) {
                        QualityChoiceSheetContent(
                            preview = previewForEstimate,
                            source = previewForEstimate?.author?.takeIf { it.isNotBlank() } ?: "YouTube",
                            loading = state is PreviewState.Loading,
                            slowSearch = slowSearch,
                            errorMessage = if (state is PreviewState.Failed) "Не удалось получить список форматов. Можно скачать автоматически." else null,
                            advancedMode = advancedMode,
                            audioMode = audioMode,
                            selectedVideoId = selected?.id,
                            selectedAudioKind = selectedAudioKind,
                            videoChoices = videoChoices,
                            audioChoices = audioChoices,
                            rememberChoice = rememberChoice,
                            estimatedSizeBytes = estimatedSize,
                            downloadEnabled = state !is PreviewState.Loading && !invalidFileName && (!audioMode || selectedAudioChoice != null),
                            onAudioModeChanged = { enableAudio ->
                                audioMode = enableAudio
                                videoOnly = false
                                if (enableAudio) {
                                    val choice = selectedAudioChoice ?: audioChoices.firstOrNull()
                                    selectedAudioKind = choice?.kind
                                    selected = choice?.source
                                    audioExport = if (choice?.kind == QualityAudioKind.COMPACT_MP3) AudioExport.MP3 else AudioExport.SOURCE
                                    audioBitrate = AudioBitrate.K128
                                    subtitleLanguage = null
                                    embedSubtitles = false
                                    if (rememberChoice) coroutineScope.launch {
                                        settingsRepository.setLastQualityChoices(selectedTier?.name, choice?.kind?.name, wasAudio = true)
                                    }
                                } else {
                                    val choice = selectedVideoChoice ?: videoChoices.firstOrNull { it.recommended } ?: videoChoices.firstOrNull()
                                    selected = choice?.video
                                    selectedVideoTier = choice?.tier
                                    audioExport = AudioExport.SOURCE
                                    if (rememberChoice) coroutineScope.launch {
                                        settingsRepository.setLastQualityChoices(choice?.tier?.name, selectedAudioKind?.name, wasAudio = false)
                                    }
                                }
                            },
                            onVideoChoice = { choice ->
                                selected = choice.video
                                selectedVideoTier = choice.tier
                                audioMode = false
                                videoOnly = false
                                audioExport = AudioExport.SOURCE
                                if (rememberChoice) coroutineScope.launch {
                                    settingsRepository.setLastQualityChoices(choice.tier.name, selectedAudioKind?.name, wasAudio = false)
                                }
                            },
                            onAudioChoice = { choice ->
                                selectedAudioKind = choice.kind
                                selected = choice.source
                                audioMode = true
                                audioExport = if (choice.kind == QualityAudioKind.COMPACT_MP3) AudioExport.MP3 else AudioExport.SOURCE
                                audioBitrate = AudioBitrate.K128
                                if (rememberChoice) coroutineScope.launch {
                                    settingsRepository.setLastQualityChoices(selectedTier?.name, choice.kind.name, wasAudio = true)
                                }
                            },
                            onRememberChoiceChanged = { enabled ->
                                rememberChoice = enabled
                                coroutineScope.launch {
                                    settingsRepository.setRememberQualityChoice(enabled)
                                    if (enabled) settingsRepository.setLastQualityChoices(selectedTier?.name, selectedAudioKind?.name, wasAudio = audioMode)
                                }
                            },
                            onOpenAdvanced = { showAdvancedPage = true },
                            onCancel = { finish() },
                            onDownload = requestDownload,
                        )
                        if (confirmLargeDownload && estimatedSize != null) {
                            AlertDialog(
                                onDismissRequest = { confirmLargeDownload = false },
                                title = { Text("Большой файл") },
                                text = { Text("Размер видео примерно ${android.text.format.Formatter.formatShortFileSize(this@ChoiceActivity, estimatedSize)}. Продолжить загрузку?") },
                                confirmButton = { TextButton(onClick = { confirmLargeDownload = false; beginDownload(false) }) { Text("Скачать всё равно") } },
                                dismissButton = { TextButton(onClick = { confirmLargeDownload = false }) { Text("Отмена") } },
                            )
                        }
                    }
                } else {
                    ModalBottomSheet(onDismissRequest = { finish() }, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.92f).dp).padding(horizontal = 20.dp)) {
                            Text("Скачать", style = MaterialTheme.typography.headlineSmall)
                            when (val previewState = state) {
                                PreviewState.Loading -> LoadingPreview(slowSearch) { finish() }
                                PreviewState.Failed -> Text("Не удалось получить список файлов. Можно попробовать скачать автоматически.")
                                PreviewState.NotNeeded -> Text("Для этой ссылки приложение выберет доступный файл автоматически.")
                                is PreviewState.Ready -> PreviewCard(previewState.cached, "YouTube")
                                is PreviewState.SocialReady -> {
                                    Text("Выберите файлы публикации", style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = {
                                        selectedItems = if (selectedItems.size == previewState.cached.items.size) emptySet() else previewState.cached.items.indices.toSet()
                                    }, modifier = Modifier.heightIn(min = 56.dp)) {
                                        Text(if (selectedItems.size == previewState.cached.items.size) "Снять все" else "Выбрать все")
                                    }
                                    FileNameField(fileName, invalidFileName) { fileName = it }
                                    LazyColumn(Modifier.weight(1f)) {
                                        itemsIndexed(previewState.cached.items) { index, media ->
                                            SocialChoiceRow(media, index, index in selectedItems) {
                                                selectedItems = if (index in selectedItems) selectedItems - index else selectedItems + index
                                            }
                                        }
                                    }
                                }
                            }
                            if (state !is PreviewState.SocialReady && !isYouTube) {
                                FileNameField(fileName, invalidFileName) { fileName = it }
                            }
                            Button(
                                enabled = state !is PreviewState.Loading && !invalidFileName && (state !is PreviewState.SocialReady || selectedItems.isNotEmpty()),
                                onClick = { beginDownload(false) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp),
                            ) { Text("Скачать") }
                        }
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
private fun LoadingPreview(slowSearch: Boolean, onCancel: () -> Unit) {
    val shimmer = rememberInfiniteTransition(label = "preview shimmer")
    val alpha by shimmer.animateFloat(
        initialValue = 0.35f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "preview alpha",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
        Spacer(Modifier.fillMaxWidth(0.78f).height(24.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Spacer(Modifier.size(width = 84.dp, height = 30.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
            }
        }
        LinearProgressIndicator(Modifier.fillMaxWidth())
        if (slowSearch) {
            Text("Ищу дольше обычного…", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 56.dp)) { Text("Отмена") }
        }
    }
}

@Composable
private fun SocialChoiceRow(media: ResolvedMedia, index: Int, selected: Boolean, onClick: () -> Unit) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, media.url, media.headers) {
        value = if (media.type == ResolvedMedia.Kind.IMAGE) withContext(Dispatchers.IO) { loadThumbnail(media.url, media.headers, 160) } else null
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).selectable(selected = selected, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = selected, onCheckedChange = null)
        thumbnail?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
        val name = when (media.type) {
            ResolvedMedia.Kind.IMAGE -> "Фото"
            ResolvedMedia.Kind.VIDEO -> "Видео"
            ResolvedMedia.Kind.AUDIO -> "Аудио"
        }
        Text("$name ${index + 1}", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PreviewCard(cached: CachedPreview, source: String) {
    val preview = cached.preview
    val thumbnail by produceState<Bitmap?>(initialValue = null, preview.thumbnailUrl) {
        value = preview.thumbnailUrl?.let { url -> withContext(Dispatchers.IO) { loadThumbnail(url) } }
    }
    PreviewCardContent(preview, source, thumbnail)
}

@Composable
private fun PreviewCardContent(preview: VideoPreview, source: String, thumbnail: Bitmap?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))) {
            if (thumbnail != null) Image(thumbnail!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Text(preview.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetadataChip(source)
            preview.durationSeconds?.let { MetadataChip("%d:%02d".format(it / 60, it % 60)) }
            preview.author?.let { MetadataChip(it) }
        }
    }
}

@Composable
private fun MetadataChip(label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
    }
}

@Preview(name = "Выбор файла · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Preview(name = "Выбор файла · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f, showSystemUi = true)
@Preview(name = "Выбор файла · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Preview(name = "Выбор файла · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun ChoiceSheetPreview() = ChoiceSheetLoadedPreview()

private fun formatLabel(context: Context, format: AvailableFormat, durationSeconds: Int? = null): String {
    val parts = mutableListOf<String>()
    if (format.hasVideo) {
        parts += format.height?.let { "${it}p" } ?: "Видео"
        format.fps?.let { parts += "$it к/с" }
        format.videoCodec?.let { parts += it.substringBefore('.') }
        format.videoBitrate?.let { parts += "≈$it Кбит/с" }
    } else {
        parts += if (format.extension == "m4a") "Только звук" else "Аудио"
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
    YouTubeFormatSelector.estimatedFormatBytes(format, durationSeconds)?.let { parts += "≈${Formatter.formatShortFileSize(context, it)}" }
    return parts.joinToString(" · ")
}

private val thumbnailClient: OkHttpClient by lazy {
    OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
}

internal fun loadThumbnail(url: String, headers: Map<String, String> = emptyMap(), maxDimension: Int = 600): Bitmap? = try {
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
