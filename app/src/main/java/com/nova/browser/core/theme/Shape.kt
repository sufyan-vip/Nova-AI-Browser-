package com.nova.browser.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object NovaShapeTokens {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val xLarge = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)
    val bottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}
