package ru.skachay.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

internal fun Context.inRussian(): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(Locale.forLanguageTag("ru"))
    return createConfigurationContext(configuration)
}
