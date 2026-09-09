package com.nova.browser

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.UrlUtils
import com.nova.browser.features.browser.viewmodel.BrowserViewModel
import com.nova.browser.features.settings.repository.SettingsRepository
import com.nova.browser.navigation.NovaNavGraph
import com.nova.browser.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Single-activity host. FragmentActivity is required by BiometricPrompt.
 * Configuration changes (rotation) are handled by Compose state + ViewModels,
 * and the activity itself declares configChanges so WebViews are never recreated.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val pendingIntentUrl = MutableStateFlow<String?>(null)
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Use the normal theme after the splash theme has shown.
        setTheme(R.style.Theme_Nova)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        handleIntent(intent)

        setContent {
            val settings by settingsRepository.settings.collectAsState(
                initial = com.nova.browser.features.settings.repository.NovaSettings()
            )
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            NovaTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                reduceMotion = settings.reduceMotion
            ) {
                val navController = rememberNavController()
                val browserViewModel: BrowserViewModel = hiltViewModel()
                val intentUrl by pendingIntentUrl.asStateFlow().collectAsState()
                val route by pendingRoute.asStateFlow().collectAsState()

                LaunchedEffect(intentUrl) {
                    intentUrl?.let { url ->
                        browserViewModel.newTab(url)
                        pendingIntentUrl.value = null
                    }
                }
                LaunchedEffect(route) {
                    route?.let { target ->
                        navController.navigate(target)
                        pendingRoute.value = null
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    NovaNavGraph(
                        navController = navController,
                        browserViewModel = browserViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Handles VIEW / SEND / WEB_SEARCH intents and the launcher shortcuts. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        try {
            when (intent.action) {
                Intent.ACTION_VIEW -> {
                    val data = intent.dataString.orEmpty()
                    when {
                        data.startsWith("nova://new-tab") -> pendingIntentUrl.value = com.nova.browser.core.utils.Constants.HOME_URL
                        data.startsWith("nova://private-tab") -> pendingIntentUrl.value = com.nova.browser.core.utils.Constants.HOME_URL
                        data.startsWith("nova://ai") -> pendingRoute.value = Routes.AGENT
                        data.isNotBlank() -> pendingIntentUrl.value = UrlUtils.normalize(data)
                    }
                }

                Intent.ACTION_SEND -> {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
                    if (text.isNotBlank()) {
                        pendingIntentUrl.value = if (UrlUtils.isUrl(text)) {
                            UrlUtils.normalize(text)
                        } else {
                            UrlUtils.searchUrl(text, com.nova.browser.core.utils.SearchEngines.all[0])
                        }
                    }
                }

                Intent.ACTION_WEB_SEARCH -> {
                    val query = intent.getStringExtra("query").orEmpty()
                    if (query.isNotBlank()) {
                        pendingIntentUrl.value =
                            UrlUtils.searchUrl(query, com.nova.browser.core.utils.SearchEngines.all[0])
                    }
                }
            }
        } catch (e: Exception) {
            // A malformed external intent must never crash the browser.
        }
    }

    /** Enters PiP when a video is playing and the user leaves (spec 19). */
    fun enterPictureInPicture(aspectWidth: Int = 16, aspectHeight: Int = 9) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(aspectWidth.coerceAtLeast(1), aspectHeight.coerceAtLeast(1)))
                .build()
            enterPictureInPictureMode(params)
        } catch (e: Exception) {
            // Device may not support PiP.
        }
    }
}
