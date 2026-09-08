package com.nova.browser.features.downloads.repository

import android.content.Context
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import com.nova.browser.core.database.dao.DownloadDao
import com.nova.browser.core.database.entities.DownloadEntity
import com.nova.browser.core.utils.FileUtils
import com.nova.browser.core.utils.UrlUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Download states persisted in Room. */
object DownloadStatus {
    const val PENDING = "pending"
    const val DOWNLOADING = "downloading"
    const val PAUSED = "paused"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {
    val downloads: Flow<List<DownloadEntity>> = downloadDao.observeAll().catch { emit(emptyList()) }
    val active: Flow<List<DownloadEntity>> = downloadDao.observeActive().catch { emit(emptyList()) }
    val activeCount: Flow<Int> = downloadDao.observeActiveCount().catch { emit(0) }

    fun search(query: String): Flow<List<DownloadEntity>> =
        downloadDao.observeSearch(query).catch { emit(emptyList()) }

    fun byCategory(category: String): Flow<List<DownloadEntity>> =
        downloadDao.observeByCategory(category).catch { emit(emptyList()) }

    /** Root directory for a download of the given category. */
    fun downloadDir(category: FileUtils.Category? = null): File =
        FileUtils.downloadDir(context, category)

    /** Creates a queued download row and reserves a unique file path. */
    suspend fun enqueue(
        url: String,
        suggestedName: String?,
        mimeType: String?,
        contentDisposition: String?,
        totalSize: Long,
        threadCount: Int
    ): DownloadEntity {
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: guessMime(url)
        val rawName = FileUtils.sanitize(
            suggestedName?.takeIf { it.isNotBlank() }
                ?: guessName(url, contentDisposition, resolvedMime)
        )
        val category = FileUtils.categoryOf(rawName, resolvedMime)
        val file = FileUtils.uniqueFile(downloadDir(category), rawName)
        val entity = DownloadEntity(
            url = url,
            fileName = file.name,
            filePath = file.absolutePath,
            mimeType = resolvedMime,
            totalSize = totalSize.coerceAtLeast(0),
            status = DownloadStatus.PENDING,
            threadCount = threadCount.coerceIn(1, 8),
            category = category.label
        )
        val id = downloadDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun get(id: Long): DownloadEntity? = downloadDao.getById(id)

    suspend fun updateProgress(id: Long, downloaded: Long, total: Long) =
        downloadDao.updateProgress(id, downloaded, total)

    suspend fun updateStatus(id: Long, status: String, error: String? = null) =
        downloadDao.updateStatus(id, status, error)

    suspend fun markCompleted(id: Long, hash: String?) =
        downloadDao.markCompleted(id, System.currentTimeMillis(), hash)

    suspend fun delete(id: Long, deleteFile: Boolean) {
        val entity = downloadDao.getById(id)
        if (deleteFile && entity != null) {
            runCatching { File(entity.filePath).delete() }
        }
        downloadDao.deleteById(id)
    }

    suspend fun clearCompleted() = downloadDao.clearCompleted()

    private fun guessMime(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url).lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private fun guessName(url: String, contentDisposition: String?, mimeType: String): String {
        val guessed = runCatching {
            URLUtil.guessFileName(url, contentDisposition, mimeType)
        }.getOrNull()
        return guessed?.takeIf { it.isNotBlank() && it != "downloadfile.bin" }
            ?: UrlUtils.fileNameFromUrl(url).ifBlank { "download_${System.currentTimeMillis()}" }
    }

}
