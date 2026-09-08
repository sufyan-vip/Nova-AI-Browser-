package com.nova.browser.core.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/* ----------------------------------------------------------------------- */
/* Surfaces                                                                 */
/* ----------------------------------------------------------------------- */

/**
 * Frosted-glass surface: translucent fill, hairline border and a soft top
 * highlight that mimics light catching the top edge of glass.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = NovaShapeTokens.large,
    tint: Color = NovaTheme.extended.glass,
    borderColor: Color = NovaTheme.extended.glassBorder,
    highlight: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint)
            .then(
                if (highlight) {
                    Modifier.background(
                        Brush.verticalGradient(
                            0f to NovaColors.GlassHighlight.copy(alpha = if (NovaTheme.extended.isDark) 0.10f else 0.04f),
                            0.45f to Color.Transparent
                        )
                    )
                } else Modifier
            )
            .border(BorderStroke(Dimens.borderThin, borderColor), shape)
    ) { content() }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = NovaShapeTokens.large,
    contentPadding: PaddingValues = PaddingValues(Dimens.lg),
    tint: Color = NovaTheme.extended.glass,
    borderColor: Color = NovaTheme.extended.glassBorder,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.97f else 1f,
        animationSpec = NovaMotion.medium(),
        label = "cardScale"
    )
    val haptics = LocalHapticFeedback.current

    GlassSurface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onClick != null && enabled) {
                    Modifier
                        .clickable(interactionSource = interaction, indication = null) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                        .semantics { contentDescription?.let { this.contentDescription = it } }
                } else Modifier
            )
            .alpha(if (enabled) 1f else 0.5f),
        shape = shape,
        tint = tint,
        borderColor = borderColor
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/* ----------------------------------------------------------------------- */
/* Buttons                                                                  */
/* ----------------------------------------------------------------------- */

enum class GlassButtonStyle { Primary, Secondary, Ghost, Danger, Accent }

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillWidth: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.96f else 1f,
        animationSpec = NovaMotion.fast(),
        label = "btnScale"
    )
    val haptics = LocalHapticFeedback.current
    val ext = NovaTheme.extended

    val container = when (style) {
        GlassButtonStyle.Primary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        GlassButtonStyle.Secondary -> ext.glass
        GlassButtonStyle.Ghost -> Color.Transparent
        GlassButtonStyle.Danger -> NovaColors.Error.copy(alpha = 0.18f)
        GlassButtonStyle.Accent -> ext.accent.copy(alpha = 0.18f)
    }
    val contentColor = when (style) {
        GlassButtonStyle.Primary -> MaterialTheme.colorScheme.primary
        GlassButtonStyle.Secondary -> MaterialTheme.colorScheme.onSurface
        GlassButtonStyle.Ghost -> MaterialTheme.colorScheme.onSurfaceVariant
        GlassButtonStyle.Danger -> NovaColors.Error
        GlassButtonStyle.Accent -> ext.accent
    }
    val borderColor = when (style) {
        GlassButtonStyle.Ghost -> Color.Transparent
        else -> contentColor.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .defaultMinSize(minHeight = Dimens.touchTarget)
            .clip(NovaShapeTokens.medium)
            .background(container)
            .border(BorderStroke(Dimens.borderThin, borderColor), NovaShapeTokens.medium)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = Dimens.lg, vertical = Dimens.md)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconSmall),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
                Spacer(Modifier.width(Dimens.sm))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(Dimens.iconSmall))
                Spacer(Modifier.width(Dimens.sm))
            }
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = Color.Transparent,
    size: Dp = Dimens.touchTarget,
    badge: Int? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        if (pressed && !reduceMotion) 0.88f else 1f,
        NovaMotion.fast(),
        label = "iconScale"
    )
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(Dimens.icon)
        )
        if (badge != null && badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badge > 99) "99" else badge.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun GlassFAB(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowing: Boolean = true,
    tint: Color = NovaColors.Accent
) {
    val pulse by rememberPulse(0.92f, 1.06f)
    val reduceMotion = LocalReduceMotion.current
    val scaleValue = if (glowing && !reduceMotion) pulse else 1f
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(Dimens.fabSize)
            .scale(scaleValue)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(tint.copy(alpha = 0.35f), tint.copy(alpha = 0.12f))
                )
            )
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.55f)), CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(28.dp))
    }
}

/* ----------------------------------------------------------------------- */
/* Inputs                                                                   */
/* ----------------------------------------------------------------------- */

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    textStyle: TextStyle = LocalTextStyle.current
) {
    Column(modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.xs, start = Dimens.xs)
            )
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.touchTarget)
                .clip(NovaShapeTokens.medium)
                .border(
                    BorderStroke(
                        Dimens.borderThin,
                        if (isError) NovaColors.Error.copy(alpha = 0.7f) else NovaTheme.extended.glassBorder
                    ),
                    NovaShapeTokens.medium
                ),
            enabled = enabled,
            isError = isError,
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaTheme.extended.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = leadingIcon?.let {
                {
                    Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Dimens.iconSmall))
                }
            },
            trailingIcon = trailingContent,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction?.invoke() },
                onGo = { onImeAction?.invoke() },
                onSearch = { onImeAction?.invoke() },
                onSend = { onImeAction?.invoke() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NovaTheme.extended.glass,
                unfocusedContainerColor = NovaTheme.extended.glass,
                disabledContainerColor = NovaTheme.extended.glass.copy(alpha = 0.4f),
                errorContainerColor = NovaColors.Error.copy(alpha = 0.08f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) NovaColors.Error else NovaTheme.extended.textTertiary,
                modifier = Modifier.padding(top = Dimens.xs, start = Dimens.xs)
            )
        }
    }
}

@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search"
) {
    GlassTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = Icons.Default.Search,
        imeAction = ImeAction.Search,
        onImeAction = onSearch
    )
}

@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCheckedChange(it)
        },
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = NovaTheme.extended.textTertiary,
            uncheckedTrackColor = NovaTheme.extended.glass,
            uncheckedBorderColor = NovaTheme.extended.glassBorder
        )
    )
}

@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = NovaTheme.extended.glassStrong
        )
    )
}

@Composable
fun <T> GlassDropdown(
    label: String,
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.xs, start = Dimens.xs)
        )
        Box {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.touchTarget)
                    .clickable(enabled = enabled) { expanded = true },
                shape = NovaShapeTokens.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.lg, vertical = Dimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = optionLabel(selected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Expand $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                optionLabel(option),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingIcon = {
                            if (option == selected) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Chips / tabs                                                             */
/* ----------------------------------------------------------------------- */

@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val bg by animateColorAsState(
        if (selected) accent.copy(alpha = 0.22f) else NovaTheme.extended.glass,
        label = "chipBg"
    )
    val fg by animateColorAsState(
        if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipFg"
    )
    Row(
        modifier = modifier
            .clip(NovaShapeTokens.pill)
            .background(bg)
            .border(
                BorderStroke(Dimens.borderThin, if (selected) accent.copy(alpha = 0.5f) else NovaTheme.extended.glassBorder),
                NovaShapeTokens.pill
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Dimens.md, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Dimens.xs))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
fun GlassTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true
) {
    val content: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            modifier = Modifier.padding(Dimens.xs)
        ) {
            tabs.forEachIndexed { index, title ->
                GlassChip(
                    text = title,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
    if (scrollable) {
        Box(modifier.horizontalScroll(rememberScrollState())) { content() }
    } else {
        Box(modifier) { content() }
    }
}

/* ----------------------------------------------------------------------- */
/* Containers: top bar, nav bar, sheets, dialogs                            */
/* ----------------------------------------------------------------------- */

@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.toolbarHeight)
                .padding(horizontal = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon?.invoke()
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Dimens.sm)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTheme.extended.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
        }
    }
}

@Composable
fun GlassBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = NovaShapeTokens.bottomSheet,
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.md),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(NovaShapeTokens.pill)
                        .background(NovaTheme.extended.glassStrong)
                )
            }
        },
        modifier = modifier,
        content = content
    )
}

@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = "Cancel",
    destructive: Boolean = false,
    content: @Composable (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = NovaShapeTokens.xLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(Dimens.borderThin, NovaTheme.extended.glassBorder),
            modifier = modifier.widthIn(max = 420.dp)
        ) {
            Column(Modifier.padding(Dimens.xl)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (message != null) {
                    Spacer(Modifier.height(Dimens.md))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (content != null) {
                    Spacer(Modifier.height(Dimens.lg))
                    content()
                }
                Spacer(Modifier.height(Dimens.xl))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (dismissText != null) {
                        GlassButton(
                            text = dismissText,
                            onClick = onDismiss,
                            style = GlassButtonStyle.Ghost
                        )
                        Spacer(Modifier.width(Dimens.sm))
                    }
                    if (confirmText != null && onConfirm != null) {
                        GlassButton(
                            text = confirmText,
                            onClick = onConfirm,
                            style = if (destructive) GlassButtonStyle.Danger else GlassButtonStyle.Primary
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Effects                                                                  */
/* ----------------------------------------------------------------------- */

/** Animated moving highlight used for skeleton loaders. */
@Composable
fun Modifier.shimmer(active: Boolean = true): Modifier {
    if (!active) return this
    val progress by rememberInfiniteProgress()
    var width by remember { mutableStateOf(1) }
    return this
        .onSizeChanged { width = it.width.coerceAtLeast(1) }
        .drawWithContent {
            drawContent()
            val start = (progress * width * 2f) - width
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                    start = Offset(start, 0f),
                    end = Offset(start + width * 0.4f, size.height)
                )
            )
        }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = NovaShapeTokens.medium
) {
    Box(
        modifier
            .clip(shape)
            .background(NovaTheme.extended.glass)
            .shimmer()
    )
}

/** Animated glowing border used for AI-active surfaces. */
@Composable
fun GlowingBorder(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    color: Color = NovaColors.Accent,
    shape: Shape = NovaShapeTokens.large,
    content: @Composable () -> Unit
) {
    val pulse by rememberPulse(0.25f, 0.75f, 2200)
    val alpha = if (active && !LocalReduceMotion.current) pulse else 0.35f
    Box(
        modifier
            .border(BorderStroke(1.5.dp, color.copy(alpha = alpha)), shape)
            .clip(shape)
    ) { content() }
}

@Composable
fun AnimatedGradientBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        NovaColors.Background,
        Color(0xFF101a33),
        NovaColors.Background
    ),
    content: @Composable () -> Unit
) {
    val progress by rememberInfiniteProgress(durationMillis = 12000, reverse = true)
    Box(
        modifier.background(
            Brush.linearGradient(
                colors = colors,
                start = Offset(0f, progress * 800f),
                end = Offset(900f, 1400f - progress * 600f)
            )
        )
    ) { content() }
}

/* ----------------------------------------------------------------------- */
/* Feedback                                                                 */
/* ----------------------------------------------------------------------- */

@Composable
fun NovaLoadingIndicator(
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 32.dp
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.5.dp
        )
        if (label != null) {
            Spacer(Modifier.height(Dimens.md))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = NovaTheme.extended.textTertiary
            )
        }
    }
}

@Composable
fun NovaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = NovaTheme.extended.glass,
    height: Dp = 4.dp
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = NovaMotion.medium(),
        label = "progress"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(NovaShapeTokens.pill)
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(NovaShapeTokens.pill)
                .background(Brush.horizontalGradient(listOf(color, NovaColors.Secondary)))
        )
    }
}

/** Full-bleed empty state used by every list in the app. */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(NovaTheme.extended.glass),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = NovaTheme.extended.textTertiary, modifier = Modifier.size(Dimens.iconLarge))
        }
        Spacer(Modifier.height(Dimens.lg))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (message != null) {
            Spacer(Modifier.height(Dimens.sm))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = NovaTheme.extended.textTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Dimens.xl))
            GlassButton(text = actionText, onClick = onAction, style = GlassButtonStyle.Secondary)
        }
    }
}

/** Standard error state with retry. */
@Composable
fun ErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = NovaColors.Error,
            modifier = Modifier.size(Dimens.iconLarge)
        )
        Spacer(Modifier.height(Dimens.md))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (message != null) {
            Spacer(Modifier.height(Dimens.xs))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = NovaTheme.extended.textTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(Dimens.lg))
            GlassButton(text = "Retry", onClick = onRetry, style = GlassButtonStyle.Secondary)
        }
    }
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.lg, vertical = Dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = NovaTheme.extended.textTertiary
        )
        action?.invoke()
    }
}

@Composable
fun FadeVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(NovaMotion.medium()),
        exit = fadeOut(NovaMotion.fast()),
        modifier = modifier
    ) { content() }
}
