package ru.skachay.app

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import ru.skachay.app.diagnostics.DiagnosticsScreenPreview

@PreviewTest
@Preview(name = "Первый запуск · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Preview(name = "Первый запуск · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f)
@Preview(name = "Первый запуск · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Preview(name = "Первый запуск · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
fun WelcomeScreenshot() = WelcomeScreenPreview()

@PreviewTest
@Preview(name = "Главная · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Preview(name = "Главная · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f)
@Preview(name = "Главная · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Preview(name = "Главная · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
fun HomeScreenshot() = HomeScreenPreview()

@PreviewTest
@Preview(name = "Настройки · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Preview(name = "Настройки · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f)
@Preview(name = "Настройки · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Preview(name = "Настройки · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
fun SettingsScreenshot() = SettingsScreenPreview()

@PreviewTest
@Preview(name = "Выбор файла · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Preview(name = "Выбор файла · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f)
@Preview(name = "Выбор файла · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Preview(name = "Выбор файла · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
fun ChoiceScreenshot() = ChoiceSheetPreview()

@PreviewTest
@Preview(name = "Выбор качества · загрузка", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Composable
fun ChoiceLoadingScreenshot() = ChoiceSheetLoadingPreview()

@PreviewTest
@Preview(name = "Выбор качества · длинный заголовок · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Composable
fun ChoiceLongTitleScreenshot() = ChoiceSheetLongTitlePreview()

@PreviewTest
@Preview(name = "Выбор качества · звук · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
fun ChoiceAudioScreenshot() = ChoiceSheetAudioPreview()

@PreviewTest
@Preview(name = "Диагностика · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Preview(name = "Диагностика · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f)
@Preview(name = "Диагностика · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Preview(name = "Диагностика · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
fun DiagnosticsScreenshot() = DiagnosticsScreenPreview()
