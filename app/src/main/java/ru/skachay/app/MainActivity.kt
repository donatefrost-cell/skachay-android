package ru.skachay.app

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.skachay.app.download.EngineUpdateResult
import ru.skachay.app.download.InstagramLink
import ru.skachay.app.download.ResolvedMedia
import ru.skachay.app.download.SourceType
import ru.skachay.app.download.TikTokLink
import ru.skachay.app.download.YtDlpRuntime
import ru.skachay.app.choice.PreviewStore
import ru.skachay.app.choice.SocialPreviewStore
import ru.skachay.app.choice.VideoPreview
import ru.skachay.app.settings.AppSettings
import ru.skachay.app.settings.DownloadPlatform
import ru.skachay.app.settings.EngineChannel
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.SettingsRepository
import ru.skachay.app.settings.ShareMode
import ru.skachay.app.settings.UserMode
import ru.skachay.app.settings.VideoBitrateLimit
import ru.skachay.app.settings.qualityFor
import ru.skachay.app.updates.UpdateBanner
import ru.skachay.app.updates.UpdateCheckButton
import ru.skachay.app.diagnostics.DiagnosticsScreen

class MainActivity : ComponentActivity() {
    private var refresh by mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(newBase.inRussian())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SkachayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val repository = remember { SettingsRepository.forContext(this) }
                    val settings by repository.settings.collectAsState(initial = null)
                    val scope = rememberCoroutineScope()
                    var showSettings by remember { mutableStateOf(false) }
                    var showDiagnostics by remember { mutableStateOf(false) }
                    BackHandler(enabled = showSettings || showDiagnostics) {
                        if (showDiagnostics) showDiagnostics = false else showSettings = false
                    }
                    val currentDensity = LocalDensity.current
                    val largeMode = settings?.largeMode == true
                    CompositionLocalProvider(LocalDensity provides Density(currentDensity.density, maxOf(currentDensity.fontScale, if (largeMode) 1.2f else 1f))) {
                        when (val current = settings) {
                            null -> Unit
                            else -> if (!current.onboardingComplete) {
                                WelcomeScreen { mode -> scope.launch { repository.selectMode(mode) } }
                            } else if (showDiagnostics) {
                                DiagnosticsScreen(onBack = { showDiagnostics = false })
                            } else if (showSettings) {
                                SettingsScreen(current, repository, onBack = { showSettings = false }, onDiagnostics = { showDiagnostics = true })
                            } else {
                                HomeScreen(current, refresh, onSettings = { showSettings = true })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }
}

@Composable
private fun WelcomeScreen(onChoose: (UserMode) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Скачай", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
            if (page > 0) IconButton(onClick = { page-- }, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Назад", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (page < 3) {
            Text("Знакомство · ${page + 1} из 3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                listOf("Найдите видео или фото", "Откройте меню «Поделиться»", "Выберите «Скачай»")[page],
                style = MaterialTheme.typography.headlineMedium,
            )
            OnboardingIllustration(page)
            Text(
                listOf(
                    "Откройте публикацию, которую хотите сохранить.",
                    "Нажмите «Поделиться» в приложении с видео или фотографией.",
                    "«Скачай» получит ссылку и покажет доступные варианты.",
                )[page],
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (page == 2) "Попробовать" else "Далее")
            }
            TextButton(onClick = { page = 3 }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Пропустить") }
        } else {
            Text("Как вам удобнее?", style = MaterialTheme.typography.headlineMedium)
            Text("Режим можно изменить в настройках в любое время.")
            Button(onClick = { onChoose(UserMode.SIMPLE) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Column { Text("Просто", style = MaterialTheme.typography.titleLarge); Text("Всё необходимое — автоматически") }
            }
            FilledTonalButton(onClick = { onChoose(UserMode.ADVANCED) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                Column { Text("Для опытных", style = MaterialTheme.typography.titleLarge); Text("Больше настроек скачивания") }
            }
        }
    }
}

@Composable
private fun OnboardingIllustration(step: Int) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        painterResource(if (step == 0) R.drawable.ic_download else R.drawable.ic_share),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(18.dp).size(40.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (step == 0) "Видео или фото" else "Поделиться", style = MaterialTheme.typography.titleLarge)
                    Text(if (step == 0) "Откройте публикацию" else "Выберите приложение", style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Text("Скачай", Modifier.padding(start = 16.dp).weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("Готово", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HomeScreen(settings: AppSettings, refresh: Int, onSettings: () -> Unit) {
    val context = LocalContext.current
    var link by remember { mutableStateOf("") }
    val clipboardUrls = remember(refresh) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        SharedUrl.allIn(text).take(20)
    }
    val snackbar = remember { SnackbarHostState() }
    val firstUrl = remember(link) { SharedUrl.firstIn(link) }
    val urls = remember(link) { SharedUrl.allIn(link).take(20) }
    var previewState by remember(firstUrl) { mutableStateOf<HomePreviewState>(HomePreviewState.Empty) }

    LaunchedEffect(refresh, clipboardUrls) {
        if (link.isBlank() && clipboardUrls.isNotEmpty()) {
            link = clipboardUrls.joinToString("\n")
            snackbar.showSnackbar("Ссылка из буфера")
        }
    }
    LaunchedEffect(firstUrl) {
        val url = firstUrl
        if (url == null || !(SourceType.isYouTube(url) || InstagramLink.supports(url) || TikTokLink.supports(url))) {
            previewState = HomePreviewState.Empty
            return@LaunchedEffect
        }
        previewState = HomePreviewState.Loading
        delay(350)
        previewState = try {
            withContext(Dispatchers.IO) {
                when {
                    SourceType.isYouTube(url) -> HomePreviewState.Video(PreviewStore.getOrFetch(context.applicationContext, url).preview)
                    InstagramLink.supports(url) || TikTokLink.supports(url) -> {
                        val preview = SocialPreviewStore.getOrFetch(context.applicationContext, url)
                        HomePreviewState.Social(
                            count = preview.items.size,
                            thumbnailUrl = preview.items.firstOrNull { it.type == ResolvedMedia.Kind.IMAGE }?.url,
                        )
                    }
                    else -> HomePreviewState.Empty
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            HomePreviewState.Failed
        }
    }

    fun openChooser(url: String) {
        context.startActivity(Intent(context, ChoiceActivity::class.java)
            .putExtra(DownloadService.EXTRA_URL, url)
            .putExtra(DownloadService.EXTRA_QUALITY, settings.qualityForUrl(url).name))
    }

    Box(Modifier.fillMaxSize()) {
        Page {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Скачай", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
                IconButton(onClick = onSettings, modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Настройки", tint = MaterialTheme.colorScheme.primary)
                }
            }
            UpdateBanner()
            Text("Вставьте ссылку или выберите «Поделиться» в приложении с видео или фото.", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = link,
                onValueChange = { link = it.take(20_000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ссылка на видео или фото") },
                placeholder = { Text("https://…") },
                minLines = 1,
                maxLines = 4,
                trailingIcon = {
                    if (link.isNotBlank()) {
                        IconButton(onClick = { link = "" }) {
                            Icon(painterResource(R.drawable.ic_clear), contentDescription = "Очистить ссылку", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            link = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().take(20_000)
                        }) {
                            Icon(painterResource(R.drawable.ic_paste), contentDescription = "Вставить ссылку", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                supportingText = {
                    when {
                        urls.size > 1 -> Text("Ссылок: ${urls.size}")
                        firstUrl != null -> Text("Ссылка распознана")
                        link.isNotBlank() -> Text("В этой ссылке не найден поддерживаемый адрес")
                    }
                },
            )
            when {
                firstUrl != null && urls.size == 1 -> HomePreviewCard(
                    url = firstUrl,
                    state = previewState,
                    quality = settings.qualityForUrl(firstUrl).label,
                    onOpen = { openChooser(firstUrl) },
                )
                urls.size > 1 -> Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Несколько ссылок", style = MaterialTheme.typography.titleMedium)
                        Text("Будет добавлено до ${urls.size} загрузок.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Button(
                enabled = urls.isNotEmpty(),
                onClick = {
                    if (urls.size > 1) startBatchDownloads(context, urls, settings) else openChooser(urls.single())
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(when {
                    urls.size > 1 -> "Продолжить · ${urls.size} ссылок"
                    urls.size == 1 -> "Выбрать качество"
                    else -> "Вставьте ссылку"
                })
            }
            PermissionPrompt()
            RecentMedia(refresh)
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
        )
    }
}

private sealed interface HomePreviewState {
    data object Empty : HomePreviewState
    data object Loading : HomePreviewState
    data object Failed : HomePreviewState
    data class Video(val preview: VideoPreview) : HomePreviewState
    data class Social(val count: Int, val thumbnailUrl: String?) : HomePreviewState
}

@Composable
private fun HomePreviewCard(url: String, state: HomePreviewState, quality: String, onOpen: () -> Unit) {
    val source = when {
        SourceType.isYouTube(url) -> "YouTube"
        InstagramLink.supports(url) -> "Instagram"
        TikTokLink.supports(url) -> "TikTok"
        else -> android.net.Uri.parse(url).host.orEmpty()
    }
    val thumbnail = when (state) {
        is HomePreviewState.Video -> state.preview.thumbnailUrl
        is HomePreviewState.Social -> state.thumbnailUrl
        else -> null
    }
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PreviewThumbnail(thumbnail)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (state) {
                        HomePreviewState.Empty -> Text("Подготовим выбор файла", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        HomePreviewState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeSkeletonLine(0.82f, 16.dp)
                            HomeSkeletonLine(0.58f, 12.dp)
                            Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HomePreviewState.Failed -> {
                            Text("Предпросмотр недоступен", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            Text("Продолжите — приложение попробует скачать файл.", style = MaterialTheme.typography.bodySmall)
                        }
                        is HomePreviewState.Video -> {
                            Text(state.preview.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(state.preview.author ?: source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        is HomePreviewState.Social -> {
                            Text(if (state.count == 1) "Публикация готова" else "Файлов: ${state.count}", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            Text("$source · оригинальное качество", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            if (SourceType.isYouTube(url)) {
                Text("По умолчанию · $quality", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (InstagramLink.supports(url) || TikTokLink.supports(url)) {
                Text("Оригинал · качество доступно по ссылке", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (InstagramLink.supports(url) || TikTokLink.supports(url)) "Выбрать файлы" else "Открыть выбор качества")
            }
        }
    }
}

@Composable
private fun HomeSkeletonLine(widthFraction: Float, height: androidx.compose.ui.unit.Dp) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "home preview shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(850),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "home preview shimmer alpha",
    )
    Box(
        Modifier.fillMaxWidth(widthFraction).heightIn(min = height).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha)),
    )
}

@Composable
private fun PreviewThumbnail(url: String?) {
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = url?.let { imageUrl -> withContext(Dispatchers.IO) { loadThumbnail(imageUrl, maxDimension = 320) } }
    }
    Box(
        Modifier.size(width = 104.dp, height = 60.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(painterResource(R.drawable.ic_download), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(settings: AppSettings, repository: SettingsRepository, onBack: () -> Unit, onDiagnostics: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showQualitySheet by remember { mutableStateOf(false) }
    var showPlatformQualitySheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Назад", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        }
        SettingsSection("Режим") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Выберите, сколько настроек показывать.", style = MaterialTheme.typography.bodyMedium)
                SegmentedPicker(
                    listOf(UserMode.SIMPLE to "Просто", UserMode.ADVANCED to "Для опытных"), settings.mode,
                ) { mode -> scope.launch { repository.selectMode(mode) } }
            }
        }
        SettingsSection("Качество") {
            Column {
                SettingsRow(
                    "Качество по умолчанию",
                    qualityShortName(settings.quality),
                    R.drawable.ic_quality,
                    trailing = { SettingsChevron() },
                    onClick = { showQualitySheet = true },
                )
                if (settings.mode == UserMode.ADVANCED) {
                    SectionDivider()
                    SettingsRow(
                        "Своё качество для каждого приложения",
                        "Отдельный предел для YouTube",
                        R.drawable.ic_info,
                        trailing = { Switch(checked = settings.platformQualityEnabled, onCheckedChange = null) },
                        onClick = { scope.launch { repository.setPlatformQualityEnabled(!settings.platformQualityEnabled) } },
                    )
                    if (settings.platformQualityEnabled) {
                        SectionDivider()
                        SettingsRow(
                            "YouTube",
                            qualityShortName(settings.qualityFor(DownloadPlatform.YOUTUBE)),
                            R.drawable.ic_download,
                            trailing = { SettingsChevron() },
                            onClick = { showPlatformQualitySheet = true },
                        )
                    }
                }
            }
        }
        if (settings.mode == UserMode.ADVANCED) {
            SettingsSection("Скачивание") {
                Column {
                    SegmentSetting("При отправке ссылки", "", listOf(ShareMode.AUTO to "В фоне", ShareMode.CHOOSE to "С выбором"), settings.shareMode) {
                        scope.launch { repository.setShareMode(it) }
                    }
                    SectionDivider()
                    SegmentSetting("Параллельные загрузки", "", (1..4).map { it to it.toString() }, settings.parallelDownloads) {
                        scope.launch { repository.setParallelDownloads(it) }
                    }
                    SectionDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Соединений на загрузку · ${settings.connections}", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = settings.connections.toFloat(), onValueChange = { value ->
                                scope.launch { repository.setConnections(value.roundToInt()) }
                            }, valueRange = 1f..16f, steps = 14,
                        )
                    }
                    SectionDivider()
                    SegmentSetting("Повторов при ошибке", "", (0..3).map { it to it.toString() }, settings.retries.coerceIn(0, 3)) {
                        scope.launch { repository.setRetries(it) }
                    }
                    SectionDivider()
                    SettingsRow("Только по Wi-Fi", "Не расходовать мобильный интернет", R.drawable.ic_wifi,
                        trailing = { Switch(checked = settings.wifiOnly, onCheckedChange = null) },
                        onClick = { scope.launch { repository.setWifiOnly(!settings.wifiOnly) } })
                }
            }
            SettingsSection("Файлы") {
                Column {
                    SettingsRow("Папка сохранения", "По умолчанию: фото, видео и музыка · Скачай", R.drawable.ic_folder)
                    SectionDivider()
                    SettingsRow("Имя файла", "Использовать название публикации", R.drawable.ic_quality)
                    SectionDivider()
                    SettingsRow("Формат фото", "Сохранять исходное качество", R.drawable.ic_folder)
                }
            }
            SettingsSection("Движок") {
                EngineUpdateControl(settings.engineChannel, repository)
            }
            SettingsSection("Память и история") { StorageControls() }
        }
        SettingsSection("Удобство") {
            SettingsRow("Крупный режим", "Увеличивает текст и упрощает чтение", R.drawable.ic_info,
                trailing = { Switch(checked = settings.largeMode, onCheckedChange = null) },
                onClick = { scope.launch { repository.setLargeMode(!settings.largeMode) } })
        }
        SettingsSection("О приложении") { AboutControls(onDiagnostics) }
    }
    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Качество по умолчанию", style = MaterialTheme.typography.headlineSmall)
                Text("Авто выбирает совместимое видео до 720p. Для этой ссылки можно будет выбрать точный формат.", style = MaterialTheme.typography.bodyMedium)
                QualityPresetGrid(settings.quality) { preset -> scope.launch { repository.setQuality(preset) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Предел битрейта", style = MaterialTheme.typography.titleMedium)
                Text("Для автоматического выбора исходного видео. Точный формат имеет приоритет.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val bitrateLimits = VideoBitrateLimit.entries
                val bitrateIndex = bitrateLimits.indexOf(settings.videoBitrateLimit).coerceAtLeast(0)
                Slider(
                    value = bitrateIndex.toFloat(),
                    onValueChange = { value -> scope.launch { repository.setVideoBitrateLimit(bitrateLimits[value.roundToInt()]) } },
                    valueRange = 0f..bitrateLimits.lastIndex.toFloat(),
                    steps = bitrateLimits.size - 2,
                )
                Text(bitrateLimits[bitrateIndex].label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                androidx.compose.material3.TextButton(onClick = { showQualitySheet = false }, modifier = Modifier.align(Alignment.End)) { Text("Готово") }
            }
        }
    }
    if (showPlatformQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlatformQualitySheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Качество по приложениям", style = MaterialTheme.typography.headlineSmall)
                Text("YouTube сохраняет выбранный предел. Instagram и TikTok отдают исходный файл.", style = MaterialTheme.typography.bodyMedium)
                PlatformQualityControl("YouTube", settings.qualityFor(DownloadPlatform.YOUTUBE)) {
                    scope.launch { repository.setPlatformQuality(DownloadPlatform.YOUTUBE, it) }
                }
                SettingsRow("Instagram", "Исходное качество публикации", R.drawable.ic_quality)
                SettingsRow("TikTok", "Исходное качество публикации", R.drawable.ic_quality)
            }
        }
    }
}

@Composable
private fun PlatformQualityControl(title: String, selected: Quality, onSelect: (Quality) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        QualityPresetGrid(selected, onSelect)
    }
}

@Composable
private fun QualityPresetGrid(selected: Quality, onSelect: (Quality) -> Unit) {
    val rows = listOf(
        listOf(Quality.AUTO, Quality.P144, Quality.P240, Quality.P360, Quality.P480),
        listOf(Quality.P720, Quality.P1080, Quality.P1440, Quality.P2160, Quality.MAX),
    )
    val effectiveSelection = if (selected == Quality.BEST) Quality.P1080 else selected
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { quality ->
                    FilterChip(
                        selected = effectiveSelection == quality,
                        onClick = { onSelect(quality) },
                        modifier = Modifier.weight(1f),
                        label = { Text(qualityShortName(quality), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}

private fun qualityShortName(quality: Quality): String = when (quality) {
    Quality.AUTO -> "Авто · 720p"
    Quality.P144 -> "144p"
    Quality.P240 -> "240p"
    Quality.P360 -> "360p"
    Quality.P480 -> "480p"
    Quality.P720 -> "720p"
    Quality.P1080, Quality.BEST -> "1080p"
    Quality.P1440 -> "1440p"
    Quality.P2160 -> "4K"
    Quality.MAX -> "Макс"
}

@Composable
private fun SettingsChevron() {
    Icon(
        painterResource(R.drawable.ic_back),
        contentDescription = "Открыть",
        modifier = Modifier.size(20.dp).rotate(180f),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) { Column(content = content) }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    supporting: String?,
    icon: Int,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(clickable.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp, end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun <T> SegmentedPicker(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun <T> SegmentSetting(title: String, supporting: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (supporting.isNotEmpty()) Text(supporting, style = MaterialTheme.typography.bodyMedium)
        SegmentedPicker(options, selected, onSelect)
    }
}

@Composable
private fun AboutControls(onDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—" }
    Column {
        SettingsRow("Скачай", "Версия $version", R.drawable.ic_info)
        SectionDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UpdateCheckButton()
            FilledTonalButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Диагностика") }
        }
    }
}

@Composable
private fun EngineUpdateControl(channel: EngineChannel, repository: SettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var message by remember(channel) { mutableStateOf<String?>(null) }
    val lastAttempt = remember {
        context.getSharedPreferences("engine", Context.MODE_PRIVATE).getLong("last_update_attempt", 0L)
    }
    val lastUpdated = if (lastAttempt > 0) java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.forLanguageTag("ru")).format(java.util.Date(lastAttempt)) else "пока не обновлялся"
    Column {
        SegmentSetting("Канал", "Стабильный — проверенный; ночной — новее", listOf(EngineChannel.STABLE to "Стабильный", EngineChannel.NIGHTLY to "Ночной"), channel) { selected ->
            if (!updating) scope.launch { repository.setEngineChannel(selected) }
        }
        SectionDivider()
        SettingsRow("Обновить сейчас", "Последняя проверка: $lastUpdated", R.drawable.ic_settings,
            trailing = { Text(if (updating) "…" else "Обновить", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
            onClick = {
        if (updating) return@SettingsRow
        updating = true
        message = null
        scope.launch {
            try {
                message = when (withContext(Dispatchers.IO) { YtDlpRuntime.updateNow(context.applicationContext, channel) }) {
                    EngineUpdateResult.UPDATED -> "Движок скачивания обновлён."
                    EngineUpdateResult.ALREADY_CURRENT -> "Установлена последняя версия выбранного канала."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "Не удалось обновить движок. Проверьте интернет и попробуйте снова."
            } finally {
                updating = false
            }
        }
    })
        message?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Page(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
        Spacer(Modifier.heightIn(min = 24.dp))
    }
}

@Composable
private fun PermissionPrompt() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("permissions", 0) }
    var resultVersion by remember { mutableIntStateOf(0) }
    val permissions = remember(resultVersion) {
        buildList {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 28 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    var showExplanation by remember { mutableStateOf(permissions.isNotEmpty() && !preferences.getBoolean("explained", false)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultVersion++ }
    if (permissions.isNotEmpty()) TextButton(onClick = { showExplanation = true }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Настроить разрешения") }
    if (showExplanation && permissions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text("Чтобы вам было удобно") },
            text = { Text("Уведомления покажут ход и результат скачивания. На старых версиях Android нужен доступ к памяти, чтобы сохранить файл в галерею.") },
            confirmButton = { TextButton(onClick = {
                preferences.edit().putBoolean("explained", true).apply()
                showExplanation = false
                launcher.launch(permissions.toTypedArray())
            }) { Text("Продолжить") } },
            dismissButton = { TextButton(onClick = { showExplanation = false }) { Text("Позже") } },
        )
    }
}

@Composable
internal fun SkachayTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFCECBF6), onPrimary = Color(0xFF201650), secondary = Color(0xFFC7C0FF), tertiary = Color(0xFFB8C8FF))
        else -> lightColorScheme(primary = Color(0xFF534AB7), onPrimary = Color.White, secondary = Color(0xFF635BBA), tertiary = Color(0xFF485D92))
    }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
internal fun SkachayPreview(content: @Composable () -> Unit) {
    SkachayTheme {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Preview(name = "Первый запуск · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Preview(name = "Первый запуск · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f, showSystemUi = true)
@Preview(name = "Первый запуск · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Preview(name = "Первый запуск · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun WelcomeScreenPreview() {
    SkachayPreview { WelcomeScreen {} }
}

@Preview(name = "Главная · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Preview(name = "Главная · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f, showSystemUi = true)
@Preview(name = "Главная · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Preview(name = "Главная · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun HomeScreenPreview() {
    SkachayPreview { HomeScreen(AppSettings(onboardingComplete = true), refresh = 0, onSettings = {}) }
}

@Preview(name = "Главная · крупный режим · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun HomeLargeModePreview() {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, maxOf(density.fontScale, 1.2f))) {
        SkachayPreview { HomeScreen(AppSettings(onboardingComplete = true, largeMode = true), refresh = 0, onSettings = {}) }
    }
}

@Preview(name = "Главная · загруженный предпросмотр · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun HomePreviewCardPreview() {
    val preview = VideoPreview(
        title = "Пример очень длинного названия видео для проверки переноса текста и отсутствия обрезания заголовка",
        author = "Семейный канал",
        durationSeconds = 125,
        thumbnailUrl = null,
        formats = emptyList(),
    )
    SkachayPreview {
        HomePreviewCard(
            url = "https://www.youtube.com/watch?v=preview",
            state = HomePreviewState.Video(preview),
            quality = "Авто · 720p",
            onOpen = {},
        )
    }
}

@Preview(name = "Настройки · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Preview(name = "Настройки · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f, showSystemUi = true)
@Preview(name = "Настройки · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Preview(name = "Настройки · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun SettingsScreenPreview() {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository.forContext(context) }
    SkachayPreview {
        SettingsScreen(
            settings = AppSettings(onboardingComplete = true, mode = UserMode.ADVANCED, largeMode = false),
            repository = repository,
            onBack = {},
            onDiagnostics = {},
        )
    }
}

@Preview(name = "Настройки · крупный режим · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Composable
internal fun SettingsLargeModePreview() {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository.forContext(context) }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, maxOf(density.fontScale, 1.2f))) {
        SkachayPreview {
            SettingsScreen(
                settings = AppSettings(onboardingComplete = true, mode = UserMode.ADVANCED, largeMode = true),
                repository = repository,
                onBack = {},
                onDiagnostics = {},
            )
        }
    }
}
