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
@Preview(name = "Главная · крупный режим · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Composable
fun HomeLargeModeScreenshot() = HomeLargeModePreview()

@PreviewTest
@Preview(name = "Главная · загруженный предпросмотр · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Composable
fun HomePreviewCardScreenshot() = HomePreviewCardPreview()

@PreviewTest
@Preview(name = "Настройки · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Preview(name = "Настройки · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f)
@Preview(name = "Настройки · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Preview(name = "Настройки · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Preview(name = "Настройки · светлая · 200%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 2f)
@Preview(name = "Настройки · тёмная · 200%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
fun SettingsScreenshot() = SettingsScreenPreview()

@PreviewTest
@Preview(name = "Пресеты качества · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Preview(name = "Пресеты качества · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f)
@Preview(name = "Пресеты качества · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Preview(name = "Пресеты качества · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Preview(name = "Пресеты качества · светлая · 200%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 2f)
@Preview(name = "Пресеты качества · тёмная · 200%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
fun QualityPresetSettingsScreenshot() = QualityPresetSettingsPreview()

@PreviewTest
@Preview(name = "Настройки · крупный режим · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Composable
fun SettingsLargeModeScreenshot() = SettingsLargeModePreview()

@PreviewTest
@Preview(name = "Настройки · режим · 100%", fontScale = 1f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · режим · 150%", fontScale = 1.5f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · режим · 200%", fontScale = 2f, widthDp = 430, heightDp = 1000)
@Composable
fun SettingsModeScreenshot() = SettingsCardPreview(SettingsPreviewSection.MODE)

@PreviewTest
@Preview(name = "Настройки · качество · 100%", fontScale = 1f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · качество · 150%", fontScale = 1.5f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · качество · 200%", fontScale = 2f, widthDp = 430, heightDp = 1000)
@Composable
fun SettingsQualityScreenshot() = SettingsCardPreview(SettingsPreviewSection.QUALITY)

@PreviewTest
@Preview(name = "Настройки · скачивание · 100%", fontScale = 1f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · скачивание · 150%", fontScale = 1.5f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · скачивание · 200%", fontScale = 2f, widthDp = 430, heightDp = 1000)
@Composable
fun SettingsDownloadScreenshot() = SettingsCardPreview(SettingsPreviewSection.DOWNLOADS)

@PreviewTest
@Preview(name = "Настройки · файлы · 100%", fontScale = 1f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · файлы · 150%", fontScale = 1.5f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · файлы · 200%", fontScale = 2f, widthDp = 430, heightDp = 1000)
@Composable
fun SettingsFilesScreenshot() = SettingsCardPreview(SettingsPreviewSection.FILES)

@PreviewTest
@Preview(name = "Настройки · движок · 100%", fontScale = 1f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · движок · 150%", fontScale = 1.5f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · движок · 200%", fontScale = 2f, widthDp = 430, heightDp = 1000)
@Composable
fun SettingsEngineScreenshot() = SettingsCardPreview(SettingsPreviewSection.ENGINE)

@PreviewTest
@Preview(name = "Настройки · память и история · 100%", fontScale = 1f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · память и история · 150%", fontScale = 1.5f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · память и история · 200%", fontScale = 2f, widthDp = 430, heightDp = 1000)
@Composable
fun SettingsStorageScreenshot() = SettingsCardPreview(SettingsPreviewSection.STORAGE)

@PreviewTest
@Preview(name = "Настройки · удобство · 100%", fontScale = 1f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · удобство · 150%", fontScale = 1.5f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · удобство · 200%", fontScale = 2f, widthDp = 430, heightDp = 1000)
@Composable
fun SettingsConvenienceScreenshot() = SettingsCardPreview(SettingsPreviewSection.CONVENIENCE)

@PreviewTest
@Preview(name = "Настройки · о приложении · 100%", fontScale = 1f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · о приложении · 150%", fontScale = 1.5f, widthDp = 430, heightDp = 1000)
@Preview(name = "Настройки · о приложении · 200%", fontScale = 2f, widthDp = 430, heightDp = 1000)
@Composable
fun SettingsAboutScreenshot() = SettingsCardPreview(SettingsPreviewSection.ABOUT)

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
@Preview(name = "Все форматы · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Preview(name = "Все форматы · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
fun AdvancedFormatsScreenshot() = AdvancedFormatsPagePreview()

@PreviewTest
@Preview(name = "Диагностика · светлая · 100%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1f)
@Preview(name = "Диагностика · тёмная · 100%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1f)
@Preview(name = "Диагностика · светлая · 150%", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 1.5f)
@Preview(name = "Диагностика · тёмная · 150%", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
fun DiagnosticsScreenshot() = DiagnosticsScreenPreview()
