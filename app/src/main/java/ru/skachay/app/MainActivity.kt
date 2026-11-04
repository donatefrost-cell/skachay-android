package ru.skachay.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkachayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Скачай", style = MaterialTheme.typography.headlineLarge)
                        Text("Приложение находится в разработке.")
                        PermissionPrompt()
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("permissions", 0) }
    var resultVersion by remember { mutableIntStateOf(0) }
    val permissions = remember(resultVersion) {
        buildList {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT <= 28 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    var showExplanation by remember { mutableStateOf(permissions.isNotEmpty() && !preferences.getBoolean("explained", false)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultVersion++ }
    if (permissions.isNotEmpty()) {
        TextButton(onClick = { showExplanation = true }) { Text("Настроить разрешения") }
    }
    if (showExplanation && permissions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text("Чтобы вам было удобно") },
            text = { Text("Уведомления покажут ход и результат скачивания. На старых версиях Android нужен доступ к памяти, чтобы сохранить файл в галерею.") },
            confirmButton = {
                TextButton(onClick = {
                    preferences.edit().putBoolean("explained", true).apply()
                    showExplanation = false
                    launcher.launch(permissions.toTypedArray())
                }) { Text("Продолжить") }
            },
            dismissButton = { TextButton(onClick = { showExplanation = false }) { Text("Позже") } },
        )
    }
}

@Composable
private fun SkachayTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
