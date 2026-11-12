package ru.skachay.app.queue

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "download_queue")
internal data class DownloadQueueEntry(
    @PrimaryKey val id: Int,
    val url: String,
    val title: String,
    val requestJson: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val percent: Int? = null,
    val bytesReceived: Long = 0,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Long? = null,
    val resultUri: String? = null,
    val error: String? = null,
)

internal object DownloadQueueStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val COMPLETE = "COMPLETE"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

@Dao
internal interface DownloadQueueDao {
    @Query("SELECT * FROM download_queue ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<DownloadQueueEntry>>

    @Query("SELECT * FROM download_queue WHERE id = :id LIMIT 1")
    suspend fun get(id: Int): DownloadQueueEntry?

    @Query("SELECT * FROM download_queue WHERE status IN ('QUEUED', 'RUNNING', 'PAUSED', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getRecoverable(): List<DownloadQueueEntry>

    @Query(
        "UPDATE download_queue SET status = 'FAILED', error = 'Загрузка была прервана. Нажмите «Повторить».', updatedAt = :updatedAt WHERE status IN ('QUEUED', 'RUNNING')",
    )
    suspend fun markInterrupted(updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DownloadQueueEntry)

    @Query("UPDATE download_queue SET status = :status, updatedAt = :updatedAt, error = :error WHERE id = :id")
    suspend fun setStatus(
        id: Int,
        status: String,
        updatedAt: Long,
        error: String? = null,
    )

    @Query(
        "UPDATE download_queue SET percent = :percent, bytesReceived = :bytesReceived, totalBytes = :totalBytes, speedBytesPerSecond = :speed, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateProgress(
        id: Int,
        percent: Int?,
        bytesReceived: Long,
        totalBytes: Long?,
        speed: Long?,
        updatedAt: Long,
    )

    @Query("UPDATE download_queue SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(
        id: Int,
        title: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE download_queue SET status = 'QUEUED', percent = NULL, bytesReceived = 0, totalBytes = NULL, speedBytesPerSecond = NULL, resultUri = NULL, error = NULL, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun restart(
        id: Int,
        updatedAt: Long,
    )

    @Query(
        "UPDATE download_queue SET status = 'COMPLETE', resultUri = :resultUri, title = :title, percent = 100, updatedAt = :updatedAt, error = NULL WHERE id = :id",
    )
    suspend fun setComplete(
        id: Int,
        resultUri: String?,
        title: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM download_queue WHERE status IN ('COMPLETE', 'CANCELLED')")
    suspend fun clearFinished()
}

@Database(entities = [DownloadQueueEntry::class], version = 1, exportSchema = true)
internal abstract class DownloadQueueDatabase : RoomDatabase() {
    abstract fun queue(): DownloadQueueDao

    companion object {
        @Volatile private var instance: DownloadQueueDatabase? = null

        fun get(context: Context): DownloadQueueDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, DownloadQueueDatabase::class.java, "download-queue.db")
                    .build()
                    .also { instance = it }
            }
    }
}
