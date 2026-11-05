package ru.skachay.app.updates

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class UpdateWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        ReleaseStore.check(applicationContext)
        return Result.success()
    }
}
