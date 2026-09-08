package com.nova.browser.features.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nova.browser.core.theme.Dimens
import com.nova.browser.core.theme.GlassIconButton
import com.nova.browser.core.theme.GlassSurface
import com.nova.browser.core.theme.NovaColors
import com.nova.browser.core.theme.NovaShapeTokens
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.features.browser.repository.Suggestion
import com.nova.browser.features.browser.viewmodel.BrowserUiState

@Composable
fun AddressBar(
    state: BrowserUiState,
    onNavigate: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onShieldClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }

    // Sync external query changes (e.g. focus gained) into the field.
    LaunchedEffect(state.addressBarFocused) {
        if (state.addressBarFocused) {
            val text = state.addressBarQuery
            fieldValue = TextFieldValue(text, TextRange(0, text.length))
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            fieldValue = TextFieldValue("")
            focusManager.clearFocus(force = true)
        }
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.addressBarHeight),
        shape = NovaShapeTokens.pill,
        tint = NovaTheme.extended.glass
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Security / privacy indicator
            Box(
                Modifier
                    .size(Dimens.touchTarget)
                    .clip(CircleShape)
                    .clickable(onClick = onShieldClick)
                    .semantics { contentDescription = "Site security and privacy info" },
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    state.isPrivate -> Icons.Default.VisibilityOff
                    state.isHome -> Icons.Default.AutoAwesome
                    state.isSecure -> Icons.Default.Lock
                    else -> Icons.Default.LockOpen
                }
                val tint = when {
                    state.isPrivate -> NovaColors.Tertiary
                    state.isHome -> NovaColors.Accent
                    state.isSecure -> NovaColors.Success
                    else -> NovaColors.Warning
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.iconSmall))
            }

            Box(Modifier.weight(1f)) {
                if (state.addressBarFocused) {
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = {
                            fieldValue = it
                            onQueryChange(it.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focus -> if (!focus.isFocused) onFocusChange(false) }
                            .semantics { contentDescription = "Address and search bar" },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                            autoCorrect = false
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                keyboard?.hide()
                                onNavigate(fieldValue.text)
                            }
                        ),
                        decorationBox = { inner ->
                            if (fieldValue.text.isEmpty()) {
                                Text(
                                    "Search or type URL",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NovaTheme.extended.textTertiary,
                                    maxLines = 1
                                )
                            }
                            inner()
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(NovaShapeTokens.pill)
                            .clickable { onFocusChange(true) }
                            .padding(vertical = Dimens.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.isHome) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = NovaTheme.extended.textTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(Dimens.sm))
                            Text(
                                "Search or type URL",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NovaTheme.extended.textTertiary,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                state.displayUrl.ifBlank { state.url },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (state.trackersBlocked > 0 && !state.addressBarFocused) {
                Row(
                    modifier = Modifier
                        .clip(NovaShapeTokens.pill)
                        .background(NovaColors.Success.copy(alpha = 0.14f))
                        .clickable(onClick = onShieldClick)
                        .padding(horizontal = Dimens.sm, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "Trackers blocked",
                        tint = NovaColors.Success,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        state.trackersBlocked.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaColors.Success
                    )
                }
                Spacer(Modifier.width(Dimens.xs))
            }

            when {
                state.addressBarFocused && fieldValue.text.isNotEmpty() -> GlassIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Clear address bar",
                    onClick = {
                        fieldValue = TextFieldValue("")
                        onQueryChange("")
                    },
                    tint = NovaTheme.extended.textTertiary,
                    size = 40.dp
                )

                state.isLoading -> GlassIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Stop loading",
                    onClick = onStop,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 40.dp
                )

                !state.isHome -> GlassIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Reload page",
                    onClick = onReload,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 40.dp
                )
            }
        }
    }
}

@Composable
fun SearchSuggestions(
    suggestions: List<Suggestion>,
    onSelect: (Suggestion) -> Unit,
    onFill: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = NovaShapeTokens.large,
            tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                items(suggestions, key = { it.url + it.type.name }) { suggestion ->
                    SuggestionRow(suggestion, onSelect = onSelect, onFill = onFill)
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: Suggestion,
    onSelect: (Suggestion) -> Unit,
    onFill: (String) -> Unit
) {
    val (icon, tint) = when (suggestion.type) {
        Suggestion.Type.SEARCH -> Icons.Default.Search to NovaTheme.extended.textTertiary
        Suggestion.Type.HISTORY -> Icons.Default.History to NovaColors.Primary
        Suggestion.Type.BOOKMARK -> Icons.Default.Bookmark to NovaColors.Warning
        Suggestion.Type.URL -> Icons.Default.Language to NovaColors.Secondary
        Suggestion.Type.AI -> Icons.Default.AutoAwesome to NovaColors.Accent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .clickable { onSelect(suggestion) }
            .padding(horizontal = Dimens.lg, vertical = Dimens.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.iconSmall))
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(
                suggestion.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (suggestion.subtitle.isNotBlank()) {
                Text(
                    suggestion.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NovaTheme.extended.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        GlassIconButton(
            icon = Icons.Default.NorthWest,
            contentDescription = "Use this suggestion",
            onClick = { onFill(suggestion.title) },
            tint = NovaTheme.extended.textTertiary,
            size = 36.dp
        )
    }
}

@Composable
fun PageLoadingIndicator(
    progress: Int,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isLoading && progress in 1..99,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        com.nova.browser.core.theme.NovaProgressBar(
            progress = progress / 100f,
            modifier = Modifier.fillMaxWidth(),
            height = 2.5.dp
        )
    }
}
