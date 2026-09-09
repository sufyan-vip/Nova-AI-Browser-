package com.nova.browser.features.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.GlassBottomSheet
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.theme.SectionHeader
import com.nova.browser.features.browser.viewmodel.BrowserUiState

/** Actions the menu can raise. */
enum class MenuAction {
    NewTab, NewPrivateTab, Bookmark, Bookmarks, History, Downloads, FindInPage,
    ReaderMode, DesktopMode, Screenshot, FullPageScreenshot, Share, Print, SavePage,
    OpenExternal, Translate, Summarize, Agent, DevTools, CodeWorkspace, Automation,
    Passwords, Privacy, Memory, Notes, Workspaces, Settings
}

@Composable
fun BrowserMenuSheet(
    state: BrowserUiState,
    onDismiss: () -> Unit,
    onAction: (MenuAction) -> Unit
) {
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Dimens.xxl)
        ) {
            // Quick row of icon actions
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickTile(
                    icon = Icons.Default.Add,
                    label = "New tab",
                    onClick = { onAction(MenuAction.NewTab) }
                )
                QuickTile(
                    icon = Icons.Default.VisibilityOff,
                    label = "Private",
                    tint = NovaColors.Tertiary,
                    onClick = { onAction(MenuAction.NewPrivateTab) }
                )
                QuickTile(
                    icon = if (state.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    label = if (state.isBookmarked) "Saved" else "Bookmark",
                    tint = if (state.isBookmarked) NovaColors.Warning else null,
                    onClick = { onAction(MenuAction.Bookmark) }
                )
                QuickTile(
                    icon = Icons.Default.Share,
                    label = "Share",
                    onClick = { onAction(MenuAction.Share) }
                )
            }

            SectionHeader("AI")
            MenuRow(Icons.Default.AutoAwesome, "Summarize this page", "TL;DR with key points", NovaColors.Accent) {
                onAction(MenuAction.Summarize)
            }
            MenuRow(Icons.Default.MenuBook, "Translate page", "Any language pair", NovaColors.Accent) {
                onAction(MenuAction.Translate)
            }
            MenuRow(Icons.Default.SmartToy, "AI Agent", "Automate multi-step tasks", NovaColors.Accent) {
                onAction(MenuAction.Agent)
            }

            SectionHeader("Page")
            MenuRow(Icons.Default.FindInPage, "Find in page") { onAction(MenuAction.FindInPage) }
            MenuRow(
                Icons.Default.MenuBook,
                "Reader mode",
                if (state.isReaderMode) "On" else "Off"
            ) { onAction(MenuAction.ReaderMode) }
            MenuRow(
                Icons.Default.DesktopWindows,
                "Desktop site",
                if (state.isDesktopMode) "On" else "Off"
            ) { onAction(MenuAction.DesktopMode) }
            MenuRow(Icons.Default.Screenshot, "Screenshot", "Visible area") { onAction(MenuAction.Screenshot) }
            MenuRow(Icons.Default.Screenshot, "Full page capture", "Scroll capture") {
                onAction(MenuAction.FullPageScreenshot)
            }
            MenuRow(Icons.Default.Save, "Save page", "Offline MHTML archive") { onAction(MenuAction.SavePage) }
            MenuRow(Icons.Default.Print, "Print") { onAction(MenuAction.Print) }
            MenuRow(Icons.Default.OpenInBrowser, "Open in another app") { onAction(MenuAction.OpenExternal) }

            SectionHeader("Library")
            MenuRow(Icons.Default.Bookmarks, "Bookmarks") { onAction(MenuAction.Bookmarks) }
            MenuRow(Icons.Default.History, "History") { onAction(MenuAction.History) }
            MenuRow(Icons.Default.Download, "Downloads") { onAction(MenuAction.Downloads) }
            MenuRow(Icons.Default.Note, "Notes & research") { onAction(MenuAction.Notes) }
            MenuRow(Icons.Default.Work, "Workspaces") { onAction(MenuAction.Workspaces) }
            MenuRow(Icons.Default.Widgets, "AI memory") { onAction(MenuAction.Memory) }

            SectionHeader("Tools")
            MenuRow(Icons.Default.Terminal, "Developer tools", "Console, network, DOM") {
                onAction(MenuAction.DevTools)
            }
            MenuRow(Icons.Default.Code, "Code workspace", "Editor, preview, API tester") {
                onAction(MenuAction.CodeWorkspace)
            }
            MenuRow(Icons.Default.SmartToy, "Automation studio", "Build workflows") {
                onAction(MenuAction.Automation)
            }

            SectionHeader("Protection")
            MenuRow(
                Icons.Default.Shield,
                "Privacy dashboard",
                "${state.trackersBlocked} blocked on this page",
                NovaColors.Success
            ) { onAction(MenuAction.Privacy) }
            MenuRow(Icons.Default.Password, "Passwords") { onAction(MenuAction.Passwords) }
            MenuRow(Icons.Default.Settings, "Settings") { onAction(MenuAction.Settings) }
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color? = null
) {
    val color = tint ?: MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(NovaShapeTokens.medium)
            .clickable(onClick = onClick)
            .padding(vertical = Dimens.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(NovaTheme.extended.glass),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(Dimens.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget + 4.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.icon)
        )
        Spacer(Modifier.width(Dimens.lg))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NovaTheme.extended.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FindInPageBar(
    query: String,
    matches: Int,
    activeMatch: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = NovaShapeTokens.medium
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.sm, vertical = Dimens.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.nova.browser.core.theme.GlassTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = "Find in page",
                leadingIcon = Icons.Default.FindInPage,
                onImeAction = onNext
            )
            Spacer(Modifier.width(Dimens.sm))
            Text(
                text = if (matches == 0) "0/0" else "$activeMatch/$matches",
                style = MaterialTheme.typography.labelMedium,
                color = NovaTheme.extended.textTertiary
            )
            com.nova.browser.core.theme.GlassIconButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous match",
                onClick = onPrevious,
                size = 40.dp
            )
            com.nova.browser.core.theme.GlassIconButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Next match",
                onClick = onNext,
                size = 40.dp
            )
            com.nova.browser.core.theme.GlassIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close find bar",
                onClick = onClose,
                size = 40.dp
            )
        }
    }
}
