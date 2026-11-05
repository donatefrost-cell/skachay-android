package ru.skachay.app.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import okhttp3.OkHttpClient
import ru.skachay.app.download.DirectDownloader

internal class InvalidUpdateException : IOException("Update package or signature does not match")

internal class UpdateDownloader(private val context: Context) {
    fun download(release: ReleaseInfo): Uri {
        val directory = File(context.cacheDir, "updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create update folder")
        val temporary = File(directory, "download.apk")
        val result = File(directory, "update.apk")
        try {
            val client = OkHttpClient.Builder().followSslRedirects(false).build()
            DirectDownloader(client).download(release.downloadUrl, temporary, connections = 4)
            if (!validateArchive(temporary, release.versionCode)) throw InvalidUpdateException()
            Files.move(temporary.toPath(), result.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return FileProvider.getUriForFile(context, "${context.packageName}.files", result)
        } finally {
            temporary.delete()
        }
    }

    @Suppress("DEPRECATION")
    internal fun validateArchive(file: File, expectedVersion: Int): Boolean {
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = manager.getPackageArchiveInfo(file.absolutePath, flags) ?: return false
        val installed = manager.getPackageInfo(context.packageName, flags)
        return ArchiveValidation.matches(
            context.packageName,
            archive.packageName,
            expectedVersion,
            ReleaseStore.version(archive),
            signers(installed),
            signers(archive),
        )
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): List<String> {
        val values = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return values?.map { it.toCharsString() }.orEmpty()
    }
}
