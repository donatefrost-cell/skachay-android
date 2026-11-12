package ru.skachay.app.recipes

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class RecipeWorker(
    context: Context,
    parameters: WorkerParameters,
) : Worker(context, parameters) {
    override fun doWork(): Result {
        RecipeStore.refresh(applicationContext)
        return Result.success()
    }
}
