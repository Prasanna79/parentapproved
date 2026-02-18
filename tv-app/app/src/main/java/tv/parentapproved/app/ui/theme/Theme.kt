package tv.parentapproved.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Use system default (Roboto on Android) — renders well on TV at all sizes
val NunitoSans = FontFamily.Default

private val KidColorScheme = darkColorScheme(
    primary = KidAccent,
    secondary = KidAccent,
    background = KidBackground,
    surface = KidSurface,
    onPrimary = KidText,
    onSecondary = KidText,
    onBackground = KidText,
    onSurface = KidText,
    error = StatusError,
)

private val ParentColorScheme = lightColorScheme(
    primary = ParentAccent,
    secondary = ParentAccent,
    background = ParentBackground,
    surface = ParentSurface,
    onPrimary = ParentSurface,
    onSecondary = ParentText,
    onBackground = ParentText,
    onSurface = ParentText,
    error = StatusError,
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodySmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun ParentApprovedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KidColorScheme,
        typography = AppTypography,
        content = content,
    )
}

@Composable
fun ParentThemeOverlay(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ParentColorScheme,
        typography = AppTypography,
        content = content,
    )
}
