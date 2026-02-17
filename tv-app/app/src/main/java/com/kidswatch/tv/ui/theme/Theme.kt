package com.kidswatch.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = TvPrimary,
    secondary = TvAccent,
    background = TvBackground,
    surface = TvSurface,
    onPrimary = TvText,
    onSecondary = TvText,
    onBackground = TvText,
    onSurface = TvText,
    error = TvError,
)

private val TvTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TvText),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TvText),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TvText),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TvText),
    bodyLarge = TextStyle(fontSize = 16.sp, color = TvText),
    bodyMedium = TextStyle(fontSize = 14.sp, color = TvText),
    bodySmall = TextStyle(fontSize = 12.sp, color = TvTextDim),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TvText),
)

@Composable
fun KidsWatchTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = TvTypography,
        content = content,
    )
}
