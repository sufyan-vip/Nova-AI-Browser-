package com.nova.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.webkit.WebView
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nova.browser.core.utils.Constants
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NovaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        installCrashGuard()
        createNotificationChannels()
        configureWebView()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    /**
     * Last-resort handler so an unexpected exception on a background thread
     * doesn't silently kill the process without a log (spec 24 → crash recovery).
     */
    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("NOVA", "Uncaught exception on ${thread.name}", throwable)
                getSharedPreferences("nova_crash", Context.MODE_PRIVATE).edit()
                    .putLong("last_crash_at", System.currentTimeMillis())
                    .putString("last_crash", throwable.toString().take(500))
                    .apply()
            } catch (e: Exception) {
                // Nothing more we can do.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun configureWebView() {
        try {
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val processName = getProcessName()
                if (packageName != processName) {
                    WebView.setDataDirectorySuffix(processName)
                }
            }
        } catch (e: Exception) {
            // WebView may be updating; the browser layer handles a missing provider.
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIF_CHANNEL_DOWNLOADS,
                    getString(R.string.downloads_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.downloads_channel_desc)
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIF_CHANNEL_AI,
                    getString(R.string.ai_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = getString(R.string.ai_channel_desc) }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    Constants.NOTIF_CHANNEL_SECURITY,
                    getString(R.string.security_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = getString(R.string.security_channel_desc) }
            )
        } catch (e: Exception) {
            // Channel creation failure is non-fatal.
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        MemoryPressure.notifyLow()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) MemoryPressure.notifyLow()
    }
}

/** Simple broadcast point so the tab host can hibernate WebViews under pressure. */
object MemoryPressure {
    private val listeners = mutableListOf<() -> Unit>()

    @Synchronized
    fun register(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { unregister(listener) }
    }

    @Synchronized
    fun unregister(listener: () -> Unit) {
        listeners -= listener
    }

    @Synchronized
    fun notifyLow() {
        listeners.toList().forEach { runCatching { it() } }
    }
}
