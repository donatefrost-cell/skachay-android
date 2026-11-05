package ru.skachay.app

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import ru.skachay.app.settings.AppSettings
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
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Скачай", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 56.dp)) { Text("Настройки") }
        }
        UpdateBanner()
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Как скачать видео", style = MaterialTheme.typography.titleLarge)
                Text("1. Откройте видео или фото")
                Text("2. Нажмите «Поделиться»")
                Text("3. Выберите «Скачай»")
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

@Composable
private fun SettingsScreen(settings: AppSettings, repository: SettingsRepository, onBack: () -> Unit, onDiagnostics: () -> Unit) {
    val scope = rememberCoroutineScope()
    Page {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) { Text("← Назад") }
        Text("Настройки", style = MaterialTheme.typography.headlineLarge)
        Text("Режим", style = MaterialTheme.typography.titleLarge)
        Option("Просто", settings.mode == UserMode.SIMPLE) { scope.launch { repository.selectMode(UserMode.SIMPLE) } }
        Option("Для опытных", settings.mode == UserMode.ADVANCED) { scope.launch { repository.selectMode(UserMode.ADVANCED) } }
        Text("Качество видео YouTube", style = MaterialTheme.typography.titleLarge)
        Option("Авто — до 720p", settings.quality == Quality.AUTO) { scope.launch { repository.setQuality(Quality.AUTO) } }
        Option("Лучшее — до 1080p", settings.quality == Quality.BEST) { scope.launch { repository.setQuality(Quality.BEST) } }
        UpdateCheckButton()
        if (settings.mode == UserMode.ADVANCED) {
            Text("Режим «Поделиться»", style = MaterialTheme.typography.titleLarge)
            Option("Авто, в фоне", settings.shareMode == ShareMode.AUTO) { scope.launch { repository.setShareMode(ShareMode.AUTO) } }
            Option("С выбором качества", settings.shareMode == ShareMode.CHOOSE) { scope.launch { repository.setShareMode(ShareMode.CHOOSE) } }
            Counter("Одновременных загрузок", settings.parallelDownloads, 1..4) { scope.launch { repository.setParallelDownloads(it) } }
            Counter("Соединений на загрузку", settings.connections, 1..16) { scope.launch { repository.setConnections(it) } }
            FilledTonalButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Диагностика") }
        }
    }
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

@Composable
private fun Counter(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { onChange(value - 1) }, enabled = value > range.first, modifier = Modifier.heightIn(min = 56.dp)) { Text("−") }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
        FilledTonalButton(onClick = { onChange(value + 1) }, enabled = value < range.last, modifier = Modifier.heightIn(min = 56.dp)) { Text("+") }
    }
}

@Composable
private fun Page(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
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
        dark -> darkColorScheme()
        else -> lightColorScheme()
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
