package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BackroomsColorScheme = darkColorScheme(
    primary = MustardFluorescent,
    onPrimary = OnMustard,
    primaryContainer = MustardDim,
    onPrimaryContainer = PhosphorWhite,
    secondary = PoolCyan,
    onSecondary = OnPoolCyan,
    secondaryContainer = PoolCyanDim,
    onSecondaryContainer = PhosphorWhite,
    tertiary = RecCrimson,
    onTertiary = PhosphorWhite,
    error = DangerBright,
    onError = OnDanger,
    background = VoidBlack,
    onBackground = PhosphorWhite,
    surface = CamcorderDark,
    onSurface = PhosphorWhite,
    surfaceVariant = CassetteSlate,
    onSurfaceVariant = PhosphorMuted,
    surfaceContainerHigh = CassetteSlateHigh,
    outline = HairlineOlive
)

private val BackroomsShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun BackroomsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BackroomsColorScheme,
        typography = BackroomsTypography,
        shapes = BackroomsShapes,
        content = content
    )
}
