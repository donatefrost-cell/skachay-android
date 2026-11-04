package ru.skachay.app

import android.app.Application
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import ru.skachay.app.download.YtDlpRuntime

class SkachayApp : Application() {
    private val maintenance = Executors.newSingleThreadScheduledExecutor()

    override fun onCreate() {
        super.onCreate()
        maintenance.execute {
            try {
                YtDlpRuntime.initialize(this)
            } catch (error: Exception) {
                Log.w("Skachay", "Не удалось подготовить движок", error)
            }
        }
        maintenance.scheduleWithFixedDelay({
            try {
                YtDlpRuntime.updateIfDue(this)
            } catch (error: Exception) {
                Log.w("Skachay", "Не удалось обновить движок", error)
            }
        }, 1, 24 * 60L, TimeUnit.MINUTES)
    }
}
