package ru.skachay.app

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import ru.skachay.app.download.YtDlpRuntime
import ru.skachay.app.recipes.RecipeStore
import ru.skachay.app.recipes.RecipeWorker

class SkachayApp : Application() {
    private val maintenance = Executors.newSingleThreadScheduledExecutor()

    override fun onCreate() {
        super.onCreate()
        RecipeStore.load(this)
        val recipes = PeriodicWorkRequest.Builder(RecipeWorker::class.java, 1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("recipe-update", ExistingPeriodicWorkPolicy.KEEP, recipes)
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
