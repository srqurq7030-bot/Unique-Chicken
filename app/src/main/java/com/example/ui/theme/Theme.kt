package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val UniqueChickenColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = PureWhite,
    primaryContainer = DeepGreenContainer,
    onPrimaryContainer = ForestGreen,
    secondary = LeafGreen,
    onSecondary = PureWhite,
    secondaryContainer = SoftGreenSurface,
    onSecondaryContainer = ForestGreen,
    background = PureWhite,
    onBackground = DarkInk,
    surface = PureWhite,
    onSurface = DarkInk,
    surfaceVariant = SoftGreenSurface,
    onSurfaceVariant = SecondaryInk,
    outline = MintBorder,
    outlineVariant = SubtleDivider,
    error = LossRed,
    onError = PureWhite,
    errorContainer = LossBg,
    onErrorContainer = LossRed,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun UniqueChickenTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = UniqueChickenColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
