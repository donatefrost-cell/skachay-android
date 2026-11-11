package ru.skachay.app

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import ru.skachay.app.download.YtDlpRuntime
import ru.skachay.app.choice.createThumbnailImageLoader
import ru.skachay.app.recipes.RecipeStore
import ru.skachay.app.recipes.RecipeWorker
import ru.skachay.app.updates.UpdateWorker
import ru.skachay.app.diagnostics.DiagnosticsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.skachay.app.queue.DownloadQueueDatabase

class SkachayApp : Application(), SingletonImageLoader.Factory {
    private val maintenance = Executors.newSingleThreadScheduledExecutor()

    override fun newImageLoader(context: Context): ImageLoader = createThumbnailImageLoader(context)

    override fun onCreate() {
        super.onCreate()
        DiagnosticsStore.markInterrupted(this)
        RecipeStore.load(this)
        maintenance.execute {
            runCatching {
                runBlocking(Dispatchers.IO) {
                    DownloadQueueDatabase.get(this@SkachayApp).queue().markInterrupted(System.currentTimeMillis())
                }
            }.onFailure { Log.w("Skachay", "Не удалось восстановить очередь после перезапуска", it) }
        }
        val recipes = PeriodicWorkRequest.Builder(RecipeWorker::class.java, 1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("recipe-update", ExistingPeriodicWorkPolicy.KEEP, recipes)
        val updates = PeriodicWorkRequest.Builder(UpdateWorker::class.java, 1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("app-update", ExistingPeriodicWorkPolicy.KEEP, updates)
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
