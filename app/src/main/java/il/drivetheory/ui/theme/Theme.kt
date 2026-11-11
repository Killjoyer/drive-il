package il.drivetheory.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Palette inspired by Israeli road signs: sign blue, warning red, guide green.
private val SignBlue = Color(0xFF1E4FA3)
private val SignBlueLight = Color(0xFFADC6FF)

private val LightColors = lightColorScheme(
    primary = SignBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF565E71),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF8A5100),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFF9F9FF),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
)

private val DarkColors = darkColorScheme(
    primary = SignBlueLight,
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBEC6DC),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFFFB870),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44474F),
)

/** Semantic colours Material 3 doesn't define: right/wrong answer feedback. */
@Immutable
data class FeedbackColors(
    val correct: Color,
    val onCorrect: Color,
    val correctContainer: Color,
    val onCorrectContainer: Color,
    val wrong: Color,
    val wrongContainer: Color,
    val onWrongContainer: Color,
)

private val LightFeedback = FeedbackColors(
    correct = Color(0xFF1B6E2E),
    onCorrect = Color.White,
    correctContainer = Color(0xFFC9F0CC),
    onCorrectContainer = Color(0xFF00210A),
    wrong = Color(0xFFBA1A1A),
    wrongContainer = Color(0xFFFFDAD6),
    onWrongContainer = Color(0xFF410002),
)

private val DarkFeedback = FeedbackColors(
    correct = Color(0xFF8BD893),
    onCorrect = Color(0xFF003914),
    correctContainer = Color(0xFF0C5220),
    onCorrectContainer = Color(0xFFC9F0CC),
    wrong = Color(0xFFFFB4AB),
    wrongContainer = Color(0xFF93000A),
    onWrongContainer = Color(0xFFFFDAD6),
)

val LocalFeedbackColors = staticCompositionLocalOf { LightFeedback }

@Composable
fun DriveTheoryTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFeedbackColors provides if (darkTheme) DarkFeedback else LightFeedback) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography(),
            content = content,
        )
    }
}

object AppTheme {
    val feedback: FeedbackColors
        @Composable get() = LocalFeedbackColors.current
}
