package ru.skachay.app.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build

internal object ReleaseStore {
    private const val PREFS = "updates"

    fun available(context: Context): ReleaseInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tag = prefs.getString("tag", null) ?: return null
        val url = prefs.getString("url", null) ?: return null
        return ReleaseParser.stored(tag, url, currentVersion(context))
    }

    fun check(context: Context): ReleaseCheck {
        val result = ReleaseFetcher().check(currentVersion(context))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (result) {
            is ReleaseCheck.Found -> prefs.edit().putString("tag", result.release.tag).putString("url", result.release.downloadUrl).apply()
            ReleaseCheck.UpToDate -> prefs.edit().clear().apply()
            ReleaseCheck.Unavailable -> Unit
        }
        return result
    }

    @Suppress("DEPRECATION")
    fun currentVersion(context: Context): Int = version(context.packageManager.getPackageInfo(context.packageName, 0))

    @Suppress("DEPRECATION")
    fun version(info: PackageInfo): Int = if (Build.VERSION.SDK_INT >= 28) {
        info.longVersionCode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } else info.versionCode
}
