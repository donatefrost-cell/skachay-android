package ru.skachay.app.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** Keeps retired folder grants alive until downloads already targeting them finish. */
internal object DownloadFolderGrantRegistry {
    private var initialized = false
    private var selectedFolder: String? = null
    private val activeDownloads = mutableMapOf<String, Int>()
    private val retiredFolders = mutableSetOf<String>()

    @Synchronized
    fun initialize(
        context: Context,
        uri: String?,
    ) {
        if (initialized) return
        initializeLocked(context, uri, null)
    }

    @Synchronized
    fun initializeAndAcquire(
        context: Context,
        settingsUri: String?,
        preferredUri: String? = null,
    ): String? {
        if (!initialized) initializeLocked(context, settingsUri, preferredUri)
        val target = preferredUri ?: selectedFolder
        if (target != null) {
            activeDownloads[target] = (activeDownloads[target] ?: 0) + 1
            if (target != selectedFolder) retiredFolders += target
        }
        return target
    }

    private fun initializeLocked(
        context: Context,
        uri: String?,
        preserveUri: String?,
    ) {
        initialized = true
        selectedFolder = uri
        context.applicationContext.contentResolver.persistedUriPermissions.forEach { grant ->
            val oldUri = grant.uri.toString()
            if (oldUri == uri) return@forEach
            if (oldUri == preserveUri) {
                retiredFolders += oldUri
                return@forEach
            }
            if (activeDownloads.containsKey(oldUri)) {
                retiredFolders += oldUri
            } else {
                releaseGrant(context.applicationContext, grant.uri)
            }
        }
    }

    @Synchronized
    fun setSelected(
        context: Context,
        uri: String?,
    ) {
        if (!initialized) {
            initialize(context, uri)
            return
        }
        if (selectedFolder == uri) return
        val previousFolder = selectedFolder
        selectedFolder = uri
        previousFolder?.takeIf { it != uri }?.let { old ->
            retiredFolders += old
            releaseIfUnused(context.applicationContext, old)
        }
        uri?.let(retiredFolders::remove)
    }

    @Synchronized
    fun acquire(uri: String?) {
        if (uri != null) activeDownloads[uri] = (activeDownloads[uri] ?: 0) + 1
    }

    @Synchronized
    fun acquireSelected(): String? =
        selectedFolder?.also { uri ->
            activeDownloads[uri] = (activeDownloads[uri] ?: 0) + 1
        }

    @Synchronized
    fun release(
        context: Context,
        uri: String?,
    ) {
        if (uri == null) return
        val count = activeDownloads[uri] ?: 0
        if (count <= 1) activeDownloads.remove(uri) else activeDownloads[uri] = count - 1
        releaseIfUnused(context.applicationContext, uri)
    }

    private fun releaseIfUnused(
        context: Context,
        uriString: String,
    ) {
        if (uriString !in retiredFolders || uriString == selectedFolder || activeDownloads.containsKey(uriString)) return
        val uri = uriString.toUri()
        val grant = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        if (grant != null) releaseGrant(context, grant.uri)
        retiredFolders.remove(uriString)
    }

    private fun releaseGrant(
        context: Context,
        uri: Uri,
    ) {
        val grant = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        var flags = 0
        if (grant.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (grant.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags != 0) {
            try {
                context.contentResolver.releasePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
            }
        }
    }
}
