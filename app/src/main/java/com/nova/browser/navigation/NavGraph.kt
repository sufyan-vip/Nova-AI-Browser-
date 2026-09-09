package com.nova.browser.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nova.browser.core.theme.NovaMotion
import com.nova.browser.features.automation.ui.AutomationStudioScreen
import com.nova.browser.features.bookmarks.ui.BookmarksScreen
import com.nova.browser.features.browser.ui.BrowserScreen
import com.nova.browser.features.browser.viewmodel.BrowserViewModel
import com.nova.browser.features.codeworkspace.ui.CodeWorkspaceScreen
import com.nova.browser.features.downloads.ui.DownloadsScreen
import com.nova.browser.features.history.ui.HistoryScreen
import com.nova.browser.features.memory.ui.AIMemoryScreen
import com.nova.browser.features.notes.ui.NotesScreen
import com.nova.browser.features.passwords.ui.PasswordManagerScreen
import com.nova.browser.features.pdf.ui.PdfViewerScreen
import com.nova.browser.features.privacy.ui.PrivacyDashboardScreen
import com.nova.browser.features.settings.ui.SettingsScreen
import com.nova.browser.features.tabs.ui.TabManagerScreen
import com.nova.browser.features.workspaces.ui.WorkspacesScreen

/** Every destination in the app. */
object Routes {
    const val BROWSER = "browser"
    const val TABS = "tabs"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val PASSWORDS = "passwords"
    const val DEVTOOLS = "devtools"
    const val CODE = "code"
    const val AUTOMATION = "automation"
    const val MEMORY = "memory"
    const val NOTES = "notes"
    const val WORKSPACES = "workspaces"
    const val AGENT = "agent"
    const val PDF = "pdf"

    fun pdf(path: String): String = "$PDF?path=${android.net.Uri.encode(path)}"
}

private const val DURATION = NovaMotion.DurationSlow

@Composable
fun NovaNavGraph(
    navController: NavHostController,
    browserViewModel: BrowserViewModel,
    modifier: Modifier = Modifier,
    startDestination: String = Routes.BROWSER
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(tween(DURATION)) { it / 6 } + fadeIn(tween(DURATION))
        },
        exitTransition = { fadeOut(tween(NovaMotion.DurationFast)) },
        popEnterTransition = { fadeIn(tween(NovaMotion.DurationMedium)) },
        popExitTransition = {
            slideOutHorizontally(tween(DURATION)) { it / 6 } + fadeOut(tween(DURATION))
        }
    ) {
        composable(
            route = Routes.BROWSER,
            enterTransition = { fadeIn(tween(NovaMotion.DurationMedium)) },
            exitTransition = { fadeOut(tween(NovaMotion.DurationFast)) }
        ) {
            BrowserScreen(
                viewModel = browserViewModel,
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(
            route = Routes.TABS,
            enterTransition = { slideInVertically(tween(DURATION)) { it / 4 } + fadeIn(tween(DURATION)) },
            popExitTransition = { slideOutVertically(tween(DURATION)) { it / 4 } + fadeOut(tween(DURATION)) }
        ) {
            TabManagerScreen(
                browserViewModel = browserViewModel,
                onClose = { navController.popBackStack() },
                onOpenTab = {
                    navController.popBackStack(Routes.BROWSER, inclusive = false)
                },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(Routes.BOOKMARKS) {
            BookmarksScreen(
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    browserViewModel.navigate(url)
                    navController.popBackStack(Routes.BROWSER, inclusive = false)
                }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    browserViewModel.navigate(url)
                    navController.popBackStack(Routes.BROWSER, inclusive = false)
                }
            )
        }

        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onOpenPdf = { path -> navController.navigate(Routes.pdf(path)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyDashboardScreen(
                browserViewModel = browserViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PASSWORDS) {
            PasswordManagerScreen(onBack = { navController.popBackStack() })
        }

        // DevTools needs the live WebView, so it opens as an overlay on the
        // browser surface; this route simply raises the flag and returns there.
        composable(Routes.DEVTOOLS) {
            LaunchedEffect(Unit) {
                browserViewModel.requestDevTools()
                navController.popBackStack(Routes.BROWSER, inclusive = false)
            }
        }

        composable(Routes.CODE) {
            CodeWorkspaceScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.AUTOMATION) {
            AutomationStudioScreen(
                onBack = { navController.popBackStack() },
                onRun = { id ->
                    browserViewModel.requestAutomation(id)
                    navController.popBackStack(Routes.BROWSER, inclusive = false)
                }
            )
        }

        composable(Routes.MEMORY) {
            AIMemoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.NOTES) {
            NotesScreen(
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    browserViewModel.navigate(url)
                    navController.popBackStack(Routes.BROWSER, inclusive = false)
                }
            )
        }

        composable(Routes.WORKSPACES) {
            WorkspacesScreen(
                onBack = { navController.popBackStack() },
                onOpenBrowser = {
                    navController.popBackStack(Routes.BROWSER, inclusive = false)
                }
            )
        }

        // The agent drives the live WebView, so it appears as a panel on the
        // browser surface; this route just raises the request and returns there.
        composable(Routes.AGENT) {
            LaunchedEffect(Unit) {
                browserViewModel.requestAgent()
                navController.popBackStack(Routes.BROWSER, inclusive = false)
            }
        }

        composable(
            route = "${Routes.PDF}?path={path}",
            arguments = listOf(
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            PdfViewerScreen(
                filePath = entry.arguments?.getString("path").orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
