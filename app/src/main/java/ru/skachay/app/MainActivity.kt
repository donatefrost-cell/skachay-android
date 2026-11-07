package ru.skachay.app

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState as rememberHorizontalScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.skachay.app.download.EngineUpdateResult
import ru.skachay.app.download.YtDlpRuntime
import ru.skachay.app.settings.AppSettings
import ru.skachay.app.settings.EngineChannel
import ru.skachay.app.settings.Quality
import ru.skachay.app.settings.SettingsRepository
import ru.skachay.app.settings.ShareMode
import ru.skachay.app.settings.UserMode
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
    Page {
        Text("Скачай", style = MaterialTheme.typography.headlineLarge)
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

@Composable
private fun HomeScreen(settings: AppSettings, refresh: Int, onSettings: () -> Unit) {
    val context = LocalContext.current
    var showLink by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    val clipboardUrl = remember(refresh) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        SharedUrl.firstIn(text)
    }
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Скачай", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
            IconButton(onClick = onSettings, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Настройки", tint = MaterialTheme.colorScheme.primary)
            }
        }
        UpdateBanner()
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Как скачать видео", style = MaterialTheme.typography.titleLarge)
                Text("1. Откройте видео или фото")
                Text("2. Нажмите «Поделиться»")
                Text("3. Выберите «Скачай»")
            }
        }
        clipboardUrl?.let { url ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Скачать ссылку из буфера?", style = MaterialTheme.typography.titleMedium)
                    Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    FilledTonalButton(onClick = {
                        context.startActivity(Intent(context, ChoiceActivity::class.java)
                            .putExtra(DownloadService.EXTRA_URL, url)
                            .putExtra(DownloadService.EXTRA_QUALITY, settings.quality.name))
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Открыть выбор") }
                }
            }
        }
        Button(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            link = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            showLink = true
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Вставить ссылку") }
        PermissionPrompt()
        RecentMedia(refresh)
    }
    if (showLink) {
        AlertDialog(
            onDismissRequest = { showLink = false },
            title = { Text("Ссылка на видео или фото") },
            text = { OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Ссылка") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(enabled = SharedUrl.firstIn(link) != null, onClick = {
                val url = SharedUrl.firstIn(link) ?: return@TextButton
                context.startActivity(Intent(context, ChoiceActivity::class.java)
                    .putExtra(DownloadService.EXTRA_URL, url)
                    .putExtra(DownloadService.EXTRA_QUALITY, settings.quality.name))
                showLink = false
            }) { Text("Продолжить") } },
            dismissButton = { TextButton(onClick = { showLink = false }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(settings: AppSettings, repository: SettingsRepository, onBack: () -> Unit, onDiagnostics: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showQualitySheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Назад", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        }
        SettingsSection("Режим", R.drawable.ic_settings) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Выберите, сколько настроек показывать.", style = MaterialTheme.typography.bodyMedium)
                SegmentedPicker(
                    listOf(UserMode.SIMPLE to "Просто", UserMode.ADVANCED to "Для опытных"), settings.mode,
                ) { mode -> scope.launch { repository.selectMode(mode) } }
            }
        }
        SettingsSection("Качество", R.drawable.ic_quality) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Авто ограничивает видео до 720p и выбирает H.264.", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberHorizontalScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Quality.AUTO to "Авто 720p", Quality.BEST to "Лучшее 1080p",
                        Quality.P2160 to "4K", Quality.MAX to "Максимум",
                    ).forEach { (quality, label) ->
                        FilterChip(
                            selected = settings.quality == quality,
                            onClick = { scope.launch { repository.setQuality(quality) } },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                if (settings.mode == UserMode.ADVANCED) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow("Предел разрешения", settings.quality.label, R.drawable.ic_quality,
                        trailing = { Text("Изменить", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
                        onClick = { showQualitySheet = true })
                } else if (settings.quality !in listOf(Quality.AUTO, Quality.BEST, Quality.P2160, Quality.MAX)) {
                    Text("Текущий предел: ${settings.quality.label}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (settings.mode == UserMode.ADVANCED) {
            SettingsSection("Скачивание", R.drawable.ic_download) {
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
            SettingsSection("Файлы", R.drawable.ic_folder) {
                Column {
                    SettingsRow("Папка сохранения", "Фото, Movies и Music · Скачай", R.drawable.ic_folder)
                    SectionDivider()
                    SettingsRow("Имя файла", "Использовать название публикации", R.drawable.ic_quality)
                    SectionDivider()
                    SettingsRow("Формат фото", "Сохранять исходное качество", R.drawable.ic_folder)
                }
            }
            SettingsSection("Движок", R.drawable.ic_settings) {
                EngineUpdateControl(settings.engineChannel, repository)
            }
            SettingsSection("Память и история", R.drawable.ic_memory) { StorageControls() }
            SettingsSection("О приложении", R.drawable.ic_info) { AboutControls(onDiagnostics) }
            SettingsSection("Удобство", R.drawable.ic_info) {
                SettingsRow("Крупный режим", "Увеличивает текст и упрощает чтение", R.drawable.ic_info,
                    trailing = { Switch(checked = settings.largeMode, onCheckedChange = null) },
                    onClick = { scope.launch { repository.setLargeMode(!settings.largeMode) } })
            }
        } else {
            SettingsSection("О приложении", R.drawable.ic_info) { AboutControls(onDiagnostics) }
        }
    }
    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Предел разрешения", style = MaterialTheme.typography.headlineSmall)
                Text("Для выбранного качества будет показан доступный размер.", style = MaterialTheme.typography.bodyMedium)
                Quality.presets.forEach { preset ->
                    SettingsRow(preset.label, null, R.drawable.ic_quality,
                        trailing = { Text(if (settings.quality == preset) "Выбрано" else "", color = MaterialTheme.colorScheme.primary) },
                        onClick = { scope.launch { repository.setQuality(preset) }; showQualitySheet = false })
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: Int, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
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

@Preview(name = "Первый запуск · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Preview(name = "Первый запуск · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f, showSystemUi = true)
@Preview(name = "Первый запуск · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Preview(name = "Первый запуск · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, showSystemUi = true)
@Composable
private fun WelcomeScreenPreview() {
    SkachayTheme { WelcomeScreen {} }
}

@Preview(name = "Главная · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Preview(name = "Главная · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f, showSystemUi = true)
@Preview(name = "Главная · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Preview(name = "Главная · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    SkachayTheme { HomeScreen(AppSettings(onboardingComplete = true), refresh = 0, onSettings = {}) }
}

@Preview(name = "Настройки · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f, showSystemUi = true)
@Preview(name = "Настройки · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f, showSystemUi = true)
@Preview(name = "Настройки · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f, showSystemUi = true)
@Preview(name = "Настройки · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository.forContext(context) }
    SkachayTheme {
        SettingsScreen(
            settings = AppSettings(onboardingComplete = true, mode = UserMode.ADVANCED, largeMode = false),
            repository = repository,
            onBack = {},
            onDiagnostics = {},
        )
    }
}
