package com.nova.browser.features.downloads.service

import com.nova.browser.core.database.entities.DownloadEntity
import com.nova.browser.core.utils.FileUtils
import com.nova.browser.features.downloads.repository.DownloadRepository
import com.nova.browser.features.downloads.repository.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Segmented (multi-thread) downloader with resume support.
 *
 * The server is probed with a ranged request; if it advertises
 * `Accept-Ranges: bytes` and a known length, the file is split into N
 * segments downloaded in parallel into one pre-allocated file. Otherwise it
 * falls back to a single resumable stream.
 */
@Singleton
class DownloadEngine @Inject constructor(
    @Named("download") private val client: OkHttpClient,
    private val repository: DownloadRepository
) {
    data class Progress(val downloaded: Long, val total: Long, val bytesPerSecond: Long)

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MIN_SEGMENT_SIZE = 1024 * 1024L // don't split files under 1 MB
        const val PROGRESS_INTERVAL_MS = 400L
    }

    /**
     * Runs the download to completion. [onProgress] is throttled to roughly
     * one update every 400 ms. Cancellation is cooperative.
     */
    suspend fun download(
        entity: DownloadEntity,
        onProgress: (Progress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val file = File(entity.filePath)
        try {
            file.parentFile?.mkdirs()

            val probe = probe(entity.url)
            val total = if (probe.length > 0) probe.length else entity.totalSize
            if (total > 0) {
                val free = FileUtils.availableBytes(file.parentFile ?: file)
                if (free in 1 until total) {
                    return@withContext Result.failure(
                        IllegalStateException("Not enough free space for ${FileUtils.formatSize(total)}")
                    )
                }
            }

            repository.updateProgress(entity.id, entity.downloadedSize, total)
            repository.updateStatus(entity.id, DownloadStatus.DOWNLOADING)

            val threads = when {
                !probe.supportsRanges -> 1
                total <= MIN_SEGMENT_SIZE -> 1
                else -> entity.threadCount.coerceIn(1, 8)
            }

            val counter = AtomicLong(0)
            val reporter = ProgressReporter(total, onProgress) { downloaded, totalBytes ->
                repository.updateProgress(entity.id, downloaded, totalBytes)
            }

            if (threads > 1 && total > 0) {
                downloadSegmented(entity.url, file, total, threads, counter, reporter)
            } else {
                downloadSingle(entity.url, file, total, counter, reporter)
            }

            reporter.flush(counter.get())

            val finalSize = if (file.exists()) file.length() else 0L
            if (total > 0 && finalSize < total) {
                return@withContext Result.failure(
                    IllegalStateException("The connection closed before the file finished")
                )
            }

            repository.updateProgress(entity.id, finalSize, if (total > 0) total else finalSize)
            repository.markCompleted(entity.id, FileUtils.sha256(file))
            Result.success(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /* ------------------------------ internals ------------------------------ */

    private data class Probe(val length: Long, val supportsRanges: Boolean)

    private fun probe(url: String): Probe = try {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .header("Accept-Encoding", "identity")
            .build()
        client.newCall(request).execute().use { response ->
            val contentRange = response.header("Content-Range")
            val acceptRanges = response.header("Accept-Ranges")?.contains("bytes", true) == true
            val length = when {
                response.code == 206 && contentRange != null ->
                    contentRange.substringAfter('/', "").toLongOrNull() ?: -1L

                else -> response.header("Content-Length")?.toLongOrNull() ?: -1L
            }
            Probe(length.coerceAtLeast(-1L), acceptRanges || response.code == 206)
        }
    } catch (e: Exception) {
        Probe(-1L, false)
    }

    private suspend fun downloadSegmented(
        url: String,
        file: File,
        total: Long,
        threads: Int,
        counter: AtomicLong,
        reporter: ProgressReporter
    ) = coroutineScope {
        // Pre-allocate so segments can seek freely.
        RandomAccessFile(file, "rw").use { it.setLength(total) }

        val segmentSize = total / threads
        val jobs = (0 until threads).map { index ->
            val start = index * segmentSize
            val end = if (index == threads - 1) total - 1 else (start + segmentSize - 1)
            async(Dispatchers.IO) {
                fetchRange(url, file, start, end, counter, reporter)
            }
        }
        jobs.awaitAll()
    }

    private suspend fun fetchRange(
        url: String,
        file: File,
        start: Long,
        end: Long,
        counter: AtomicLong,
        reporter: ProgressReporter
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .header("Accept-Encoding", "identity")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Server returned HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            RandomAccessFile(file, "rw").use { output ->
                output.seek(start)
                val buffer = ByteArray(BUFFER_SIZE)
                body.byteStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        reporter.report(counter.addAndGet(read.toLong()))
                    }
                }
            }
        }
    }

    private suspend fun downloadSingle(
        url: String,
        file: File,
        total: Long,
        counter: AtomicLong,
        reporter: ProgressReporter
    ) {
        val existing = if (file.exists()) file.length() else 0L
        val resumable = existing > 0 && total > 0 && existing < total
        counter.set(if (resumable) existing else 0L)

        val builder = Request.Builder().url(url).header("Accept-Encoding", "identity")
        if (resumable) builder.header("Range", "bytes=$existing-")

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Server returned HTTP ${response.code}")
            }
            val appending = resumable && response.code == 206
            if (!appending) counter.set(0L)

            val body = response.body ?: throw IllegalStateException("Empty response body")
            java.io.FileOutputStream(file, appending).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                body.byteStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        reporter.report(counter.addAndGet(read.toLong()))
                    }
                }
                output.flush()
            }
        }
    }

    /** Throttles UI + database progress updates and computes transfer speed. */
    private class ProgressReporter(
        private val total: Long,
        private val onProgress: (Progress) -> Unit,
        private val persist: suspend (Long, Long) -> Unit
    ) {
        private var lastEmitAt = 0L
        private var lastBytes = 0L

        suspend fun report(downloaded: Long) {
            val now = System.currentTimeMillis()
            if (now - lastEmitAt < PROGRESS_INTERVAL_MS) return
            val elapsed = (now - lastEmitAt).coerceAtLeast(1L)
            val speed = ((downloaded - lastBytes) * 1000 / elapsed).coerceAtLeast(0L)
            lastEmitAt = now
            lastBytes = downloaded
            onProgress(Progress(downloaded, total, speed))
            runCatching { persist(downloaded, total) }
        }

        suspend fun flush(downloaded: Long) {
            onProgress(Progress(downloaded, total, 0))
            runCatching { persist(downloaded, total) }
        }
    }
}
