package ru.skachay.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.skachay.app.settings.Quality

class ChoiceActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(newBase.inRussian())

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = SharedUrl.firstIn(intent.getStringExtra(DownloadService.EXTRA_URL))
        if (url == null) {
            finish()
            return
        }
        val initial = Quality.entries.firstOrNull { it.name == intent.getStringExtra(DownloadService.EXTRA_QUALITY) } ?: Quality.AUTO
        setContent {
            SkachayTheme {
                var quality by remember { mutableStateOf(initial) }
                ModalBottomSheet(onDismissRequest = { finish() }) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Скачать", style = MaterialTheme.typography.headlineSmall)
                        Text("Качество видео YouTube", style = MaterialTheme.typography.titleMedium)
                        Quality.entries.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = quality == option, onClick = { quality = option }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = quality == option, onClick = null)
                                Text(if (option == Quality.AUTO) "Авто — до 720p" else "Лучшее — до 1080p")
                            }
                        }
                        Text("Для фотографий и других сайтов приложение выбирает доступный файл автоматически.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { startDownload(this@ChoiceActivity, url, quality); finish() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Скачать")
                        }
                    }
                }
            }
        }
    }
}
