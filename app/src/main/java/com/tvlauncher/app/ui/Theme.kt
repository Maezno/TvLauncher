package com.tvlauncher.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.tvlauncher.app.data.IconLoader

object LauncherColors {
    val Background = Brush.verticalGradient(listOf(Color(0xFF0B0F1A), Color(0xFF151C2E)))
    val Card = Color(0xFF1B2236)
    val CardFocused = Color(0xFF2B3654)
    val Panel = Color(0xFF141A2B)
    val Scrim = Color(0xCC000000)
    val TextDim = Color(0xFFB4BCD0)
}

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("IconLoader not provided") }

@Composable
fun TvLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0B0F1A),
            surface = LauncherColors.Card,
        ),
        content = content,
    )
}
