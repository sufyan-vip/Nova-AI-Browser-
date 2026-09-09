package com.nova.browser.features.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nova.browser.core.theme.NovaTheme
import com.nova.browser.core.utils.UrlUtils
import kotlin.math.absoluteValue

/**
 * Site icon with graceful degradation: remote favicon → a deterministic
 * coloured monogram tile → a generic globe. Never blocks the list.
 */
@Composable
fun FaviconImage(
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    faviconUrl: String? = null
) {
    val host = UrlUtils.host(url)
    val source = faviconUrl?.takeIf { it.isNotBlank() } ?: UrlUtils.faviconUrl(url).orEmpty()
    val shape = RoundedCornerShape(size / 4)

    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(NovaTheme.extended.glass),
        contentAlignment = Alignment.Center
    ) {
        if (source.isBlank()) {
            Monogram(host, size)
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(source)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
                loading = { Monogram(host, size) },
                error = { Monogram(host, size) }
            )
        }
    }
}

@Composable
private fun Monogram(host: String, size: Dp) {
    if (host.isBlank()) {
        Icon(
            Icons.Default.Language,
            contentDescription = null,
            tint = NovaTheme.extended.textTertiary,
            modifier = Modifier.size(size * 0.6f)
        )
        return
    }
    val letter = host.removePrefix("www.").firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val palette = listOf(
        Color(0xFF6366F1), Color(0xFF06B6D4), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFA855F7)
    )
    val color = palette[(host.hashCode().absoluteValue) % palette.size]
    Box(
        Modifier
            .size(size)
            .background(color.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = Color.White,
            fontSize = (size.value * 0.5f).sp,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
