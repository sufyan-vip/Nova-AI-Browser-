package com.nova.browser.features.downloads.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nova.browser.MainActivity
import com.nova.browser.R
import com.nova.browser.core.utils.Constants
import com.nova.browser.core.utils.FileUtils
import com.nova.browser.features.downloads.repository.DownloadRepository
import com.nova.browser.features.downloads.repository.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named

/**
 * Foreground service that owns every active download so transfers survive the
 * app going to the background. One coroutine per download, cancellable by id.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var engine: DownloadEngine

    @Inject
    @Named("applicationScope")
    lateinit var appScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()

    companion object {
        const val ACTION_START = "com.nova.browser.action.DOWNLOAD_START"
        const val ACTION_CANCEL = "com.nova.browser.action.DOWNLOAD_CANCEL"
        const val ACTION_PAUSE = "com.nova.browser.action.DOWNLOAD_PAUSE"
        const val EXTRA_ID = "download_id"

        private const val SUMMARY_NOTIFICATION_ID = 4200
        private const val NOTIFICATION_ID_BASE = 4300

        fun start(context: Context, id: Long) = send(context, ACTION_START, id)
        fun cancel(context: Context, id: Long) = send(context, ACTION_CANCEL, id)
        fun pause(context: Context, id: Long) = send(context, ACTION_PAUSE, id)

        private fun send(context: Context, action: String, id: Long) {
            val intent = Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_ID, id)
            }
            try {
                if (action == ACTION_START && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // The system may refuse background starts; the download stays queued.
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        when (intent?.action) {
            ACTION_START -> {
                promoteToForeground()
                if (id > 0) startDownload(id)
            }

            ACTION_CANCEL -> {
                jobs.remove(id)?.cancel()
                serviceScope.launch {
                    repository.updateStatus(id, DownloadStatus.CANCELLED)
                    dismiss(id)
                    stopIfIdle()
                }
            }

            ACTION_PAUSE -> {
                jobs.remove(id)?.cancel()
                serviceScope.launch {
                    repository.updateStatus(id, DownloadStatus.PAUSED)
                    dismiss(id)
                    stopIfIdle()
                }
            }

            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        val notification = summaryNotification("Downloading", "Preparing…")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SUMMARY_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(SUMMARY_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Foreground start can be denied; downloads still run while the app lives.
        }
    }

    private fun startDownload(id: Long) {
        if (jobs.containsKey(id)) return
        val job = serviceScope.launch {
            val entity = repository.get(id)
            if (entity == null) {
                stopIfIdle()
                return@launch
            }

            notify(id, entity.fileName, 0, entity.totalSize, 0)

            val result = engine.download(entity) { progress ->
                notify(id, entity.fileName, progress.downloaded, progress.total, progress.bytesPerSecond)
            }

            result.fold(
                onSuccess = {
                    notifyComplete(id, entity.fileName, entity.mimeType, it.absolutePath)
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) {
                        repository.updateStatus(id, DownloadStatus.PAUSED)
                    } else {
                        repository.updateStatus(
                            id,
                            DownloadStatus.FAILED,
                            throwable.message ?: "The download failed"
                        )
                        notifyFailed(id, entity.fileName, throwable.message)
                    }
                }
            )

            jobs.remove(id)
            stopIfIdle()
        }
        jobs[id] = job
    }

    private fun stopIfIdle() {
        if (jobs.isEmpty()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                // Already stopped.
            }
            stopSelf()
        }
    }

    /* ---------------------------- notifications ---------------------------- */

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("nova://downloads")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelIntent(id: Long): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getService(
            this,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun summaryNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun notify(id: Long, name: String, downloaded: Long, total: Long, speed: Long) {
        val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
        val detail = buildString {
            append(FileUtils.formatSize(downloaded))
            if (total > 0) append(" / ${FileUtils.formatSize(total)}")
            if (speed > 0) append(" · ${FileUtils.formatSpeed(speed)}")
        }
        val notification = NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(name)
            .setContentText(detail)
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .addAction(R.drawable.ic_close, "Cancel", cancelIntent(id))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        post(id, notification)
    }

    private fun notifyComplete(id: Long, name: String, mimeType: String, path: String) {
        val notification = NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Download complete")
            .setContentText(name)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        post(id, notification)
    }

    private fun notifyFailed(id: Long, name: String, reason: String?) {
        val notification = NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Download failed")
            .setContentText(reason?.take(80) ?: name)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        post(id, notification)
    }

    private fun post(id: Long, notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID_BASE + id.toInt(), notification)
        } catch (e: SecurityException) {
            // Notifications are optional; the download continues regardless.
        } catch (e: Exception) {
            // Ignore notification failures.
        }
    }

    private fun dismiss(id: Long) {
        try {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_BASE + id.toInt())
        } catch (e: Exception) {
            // Ignore.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        serviceScope.cancel()
    }
}
