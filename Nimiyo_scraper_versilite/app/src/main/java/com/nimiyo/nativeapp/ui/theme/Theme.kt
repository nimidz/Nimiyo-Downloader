package com.nimiyo.nativeapp.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nimiyo.nativeapp.core.AccentColorPref
import com.nimiyo.nativeapp.core.DarkModePref
import com.nimiyo.nativeapp.core.UiStyle

@Immutable
data class NimiyoStyleTokens(
    val uiStyle: UiStyle = UiStyle.MATERIAL,
    val isDark: Boolean = false,
    val isGlass: Boolean = false,
    val isCupertino: Boolean = false,
    val cardBorder: BorderStroke? = null,
    val cardElevation: Dp = 0.dp,
    val cornerRadius: Dp = 20.dp,
    val cardSurface: Color = Color.Unspecified,
    val navBarBg: Color = Color.Unspecified,
    val cupertinoDividerColor: Color = Color.Unspecified,
    val glassBrush: Brush? = null,
    val glassBorderBrush: Brush? = null,
    val backdropBrush: Brush? = null,
)

val LocalNimiyoStyle = staticCompositionLocalOf { NimiyoStyleTokens() }

// Warm pastel/tonal Material 3 fallback palette for API < 31 or Non-Dynamic themes
private val M3LightFallback = lightColorScheme(
    primary = Color(0xFF3A5BA9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF575E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF725572),
    onTertiary = Color.White,
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF5),
    surfaceContainerHigh = Color(0xFFE7E7EF),
    surfaceContainerHighest = Color(0xFFE1E2EA),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
)

private val M3DarkFallback = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF1E438F),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFC0C6DC),
    onSecondary = Color(0xFF293042),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFE0BBE0),
    onTertiary = Color(0xFF412742),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A30),
    surfaceContainerHighest = Color(0xFF33353B),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),
)

private data class PaletteSeed(
    val hue: Float,
    val saturation: Float,
    val tertiaryShift: Float = 48f,
    val primaryLightness: Float = 0.40f,
)

private fun AccentColorPref.paletteSeed(): PaletteSeed? = when (this) {
    AccentColorPref.DYNAMIC -> null
    AccentColorPref.GREEN -> PaletteSeed(hue = 92f, saturation = 0.44f, tertiaryShift = 44f, primaryLightness = 0.36f)
    AccentColorPref.TEAL -> PaletteSeed(hue = 166f, saturation = 0.52f, tertiaryShift = 40f, primaryLightness = 0.35f)
    AccentColorPref.CYAN -> PaletteSeed(hue = 192f, saturation = 0.62f, tertiaryShift = 38f, primaryLightness = 0.36f)
    AccentColorPref.BLUE -> PaletteSeed(hue = 218f, saturation = 0.68f, tertiaryShift = 42f)
    AccentColorPref.INDIGO -> PaletteSeed(hue = 242f, saturation = 0.56f, tertiaryShift = 46f)
    AccentColorPref.PURPLE -> PaletteSeed(hue = 272f, saturation = 0.54f, tertiaryShift = 48f)
    AccentColorPref.PINK -> PaletteSeed(hue = 332f, saturation = 0.62f, tertiaryShift = 36f)
    AccentColorPref.RED -> PaletteSeed(hue = 356f, saturation = 0.66f, tertiaryShift = 34f)
    AccentColorPref.ORANGE -> PaletteSeed(hue = 24f, saturation = 0.72f, tertiaryShift = 36f, primaryLightness = 0.38f)
    AccentColorPref.YELLOW -> PaletteSeed(hue = 44f, saturation = 0.74f, tertiaryShift = 42f, primaryLightness = 0.35f)
    AccentColorPref.BROWN -> PaletteSeed(hue = 28f, saturation = 0.36f, tertiaryShift = 32f, primaryLightness = 0.37f)
    AccentColorPref.GRAY -> PaletteSeed(hue = 220f, saturation = 0.08f, tertiaryShift = 20f)
}

fun palettePreviewTriple(pref: AccentColorPref, isDark: Boolean): Triple<Color, Color, Color> {
    val seed = pref.paletteSeed() ?: return if (isDark) {
        Triple(M3DarkFallback.primary, M3DarkFallback.primaryContainer, M3DarkFallback.tertiary)
    } else {
        Triple(M3LightFallback.primary, M3LightFallback.primaryContainer, M3LightFallback.tertiary)
    }
    val top = Color.hsl(seed.hue, (seed.saturation * 0.9f).coerceIn(0.12f, 0.85f), if (isDark) 0.72f else 0.48f)
    val left = Color.hsl(seed.hue, (seed.saturation * 0.65f).coerceIn(0.10f, 0.75f), if (isDark) 0.32f else 0.84f)
    val right = Color.hsl((seed.hue + seed.tertiaryShift) % 360f, (seed.saturation * 0.60f).coerceIn(0.12f, 0.70f), if (isDark) 0.54f else 0.66f)
    return Triple(top, left, right)
}

private fun buildTonalColorScheme(seed: PaletteSeed, isDark: Boolean): ColorScheme {
    val h = seed.hue
    val s = seed.saturation
    val th = (h + seed.tertiaryShift) % 360f
    val ns = (s * 0.22f).coerceIn(0.03f, 0.20f)
    val nvs = (s * 0.28f).coerceIn(0.05f, 0.26f)

    return if (isDark) {
        val primary = Color.hsl(h, (s * 0.88f).coerceIn(0.14f, 0.86f), 0.78f)
        darkColorScheme(
            primary = primary,
            onPrimary = Color.hsl(h, (s * 0.85f).coerceIn(0.14f, 0.88f), 0.17f),
            primaryContainer = Color.hsl(h, (s * 0.72f).coerceIn(0.14f, 0.76f), 0.28f),
            onPrimaryContainer = Color.hsl(h, (s * 0.88f).coerceIn(0.14f, 0.88f), 0.90f),
            secondary = Color.hsl(h, (s * 0.38f).coerceIn(0.08f, 0.40f), 0.78f),
            onSecondary = Color.hsl(h, (s * 0.40f).coerceIn(0.08f, 0.42f), 0.20f),
            secondaryContainer = Color.hsl(h, (s * 0.40f).coerceIn(0.08f, 0.42f), 0.28f),
            onSecondaryContainer = Color.hsl(h, (s * 0.44f).coerceIn(0.10f, 0.46f), 0.89f),
            tertiary = Color.hsl(th, (s * 0.60f).coerceIn(0.12f, 0.65f), 0.78f),
            onTertiary = Color.hsl(th, (s * 0.60f).coerceIn(0.12f, 0.65f), 0.20f),
            tertiaryContainer = Color.hsl(th, (s * 0.55f).coerceIn(0.12f, 0.62f), 0.28f),
            onTertiaryContainer = Color.hsl(th, (s * 0.65f).coerceIn(0.14f, 0.68f), 0.90f),
            background = Color.hsl(h, ns, 0.08f),
            onBackground = Color.hsl(h, ns, 0.90f),
            surface = Color.hsl(h, ns, 0.08f),
            onSurface = Color.hsl(h, ns, 0.90f),
            surfaceVariant = Color.hsl(h, nvs, 0.27f),
            onSurfaceVariant = Color.hsl(h, nvs, 0.78f),
            surfaceContainerLowest = Color.hsl(h, ns, 0.055f),
            surfaceContainerLow = Color.hsl(h, ns, 0.11f),
            surfaceContainer = Color.hsl(h, ns, 0.135f),
            surfaceContainerHigh = Color.hsl(h, ns, 0.17f),
            surfaceContainerHighest = Color.hsl(h, ns, 0.21f),
            outline = Color.hsl(h, nvs, 0.56f),
            outlineVariant = Color.hsl(h, nvs, 0.28f),
            surfaceTint = primary,
        )
    } else {
        val primary = Color.hsl(h, s.coerceIn(0.14f, 0.80f), seed.primaryLightness)
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = Color.hsl(h, (s * 0.85f).coerceIn(0.14f, 0.82f), 0.89f),
            onPrimaryContainer = Color.hsl(h, (s * 0.90f).coerceIn(0.16f, 0.88f), 0.14f),
            secondary = Color.hsl(h, (s * 0.36f).coerceIn(0.08f, 0.38f), 0.41f),
            onSecondary = Color.White,
            secondaryContainer = Color.hsl(h, (s * 0.45f).coerceIn(0.10f, 0.48f), 0.89f),
            onSecondaryContainer = Color.hsl(h, (s * 0.45f).coerceIn(0.10f, 0.48f), 0.15f),
            tertiary = Color.hsl(th, (s * 0.56f).coerceIn(0.12f, 0.62f), 0.41f),
            onTertiary = Color.White,
            tertiaryContainer = Color.hsl(th, (s * 0.65f).coerceIn(0.14f, 0.68f), 0.89f),
            onTertiaryContainer = Color.hsl(th, (s * 0.65f).coerceIn(0.14f, 0.68f), 0.15f),
            background = Color.hsl(h, ns, 0.975f),
            onBackground = Color.hsl(h, ns, 0.11f),
            surface = Color.hsl(h, ns, 0.975f),
            onSurface = Color.hsl(h, ns, 0.11f),
            surfaceVariant = Color.hsl(h, nvs, 0.90f),
            onSurfaceVariant = Color.hsl(h, nvs, 0.30f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.hsl(h, ns, 0.955f),
            surfaceContainer = Color.hsl(h, ns, 0.935f),
            surfaceContainerHigh = Color.hsl(h, ns, 0.915f),
            surfaceContainerHighest = Color.hsl(h, ns, 0.895f),
            outline = Color.hsl(h, nvs, 0.48f),
            outlineVariant = Color.hsl(h, nvs, 0.80f),
            surfaceTint = primary,
        )
    }
}

@Composable
fun NimiyoTheme(
    uiStyle: UiStyle = UiStyle.MATERIAL,
    darkModePref: DarkModePref = DarkModePref.SYSTEM,
    accentColor: AccentColorPref = AccentColorPref.DYNAMIC,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isDark = when (darkModePref) {
        DarkModePref.LIGHT -> false
        DarkModePref.DARK -> true
        DarkModePref.SYSTEM -> isSystemInDarkTheme()
    }

    val useDynamic = accentColor == AccentColorPref.DYNAMIC &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        uiStyle != UiStyle.CUPERTINO
    val seed = accentColor.paletteSeed()

    val colorScheme = when {
        uiStyle == UiStyle.CUPERTINO -> {
            val cupertinoBase = if (isDark) {
                M3DarkFallback.copy(
                    primary = Color(0xFF0A84FF),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFF003B73),
                    onPrimaryContainer = Color(0xFFD6EAFF),
                    secondary = Color(0xFF5E5CE6),
                    background = Color(0xFF000000),
                    onBackground = Color(0xFFFFFFFF),
                    surface = Color(0xFF000000),
                    onSurface = Color(0xFFFFFFFF),
                    surfaceContainerLowest = Color(0xFF000000),
                    surfaceContainerLow = Color(0xFF1C1C1E),
                    surfaceContainer = Color(0xFF161618),
                    surfaceContainerHigh = Color(0xFF2C2C2E),
                    surfaceContainerHighest = Color(0xFF3A3A3C),
                    surfaceVariant = Color(0xFF2C2C2E),
                    onSurfaceVariant = Color(0xFF8E8E93),
                    outline = Color(0xFF48484A),
                    outlineVariant = Color(0xFF38383A),
                )
            } else {
                M3LightFallback.copy(
                    primary = Color(0xFF007AFF),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE1F0FF),
                    onPrimaryContainer = Color(0xFF003266),
                    secondary = Color(0xFF5856D6),
                    background = Color(0xFFF2F2F7),
                    onBackground = Color(0xFF000000),
                    surface = Color(0xFFF2F2F7),
                    onSurface = Color(0xFF000000),
                    surfaceContainerLowest = Color(0xFFFFFFFF),
                    surfaceContainerLow = Color(0xFFFFFFFF),
                    surfaceContainer = Color(0xFFF9F9F9),
                    surfaceContainerHigh = Color(0xFFEFEFF4),
                    surfaceContainerHighest = Color(0xFFE5E5EA),
                    surfaceVariant = Color(0xFFE5E5EA),
                    onSurfaceVariant = Color(0xFF6C6C70),
                    outline = Color(0xFFC7C7CC),
                    outlineVariant = Color(0xFFE5E5EA),
                )
            }
            if (seed != null) {
                val tonal = buildTonalColorScheme(seed, isDark)
                cupertinoBase.copy(
                    primary = tonal.primary,
                    onPrimary = tonal.onPrimary,
                    primaryContainer = tonal.primaryContainer,
                    onPrimaryContainer = tonal.onPrimaryContainer,
                    secondary = tonal.secondary,
                    onSecondary = tonal.onSecondary,
                    secondaryContainer = tonal.secondaryContainer,
                    onSecondaryContainer = tonal.onSecondaryContainer,
                    tertiary = tonal.tertiary,
                    surfaceTint = tonal.surfaceTint,
                )
            } else {
                cupertinoBase
            }
        }
        seed != null -> buildTonalColorScheme(seed, isDark)
        useDynamic && isDark -> dynamicDarkColorScheme(context)
        useDynamic && !isDark -> dynamicLightColorScheme(context)
        isDark -> M3DarkFallback
        else -> M3LightFallback
    }

    val tokens = when (uiStyle) {
        UiStyle.GLASSMORPHISM -> {
            val glassCardBrush = Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        colorScheme.surfaceContainerHighest.copy(alpha = 0.56f),
                        colorScheme.surfaceContainerLow.copy(alpha = 0.34f),
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.72f),
                        colorScheme.surfaceContainerLow.copy(alpha = 0.46f),
                    )
                }
            )
            val glassEdgeBrush = Brush.linearGradient(
                colors = if (isDark) {
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        colorScheme.primary.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.06f),
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.92f),
                        colorScheme.primary.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.32f),
                    )
                }
            )
            val ambientBackdrop = Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(
                        colorScheme.primary.copy(alpha = 0.22f),
                        colorScheme.background,
                        colorScheme.tertiary.copy(alpha = 0.16f),
                    )
                } else {
                    listOf(
                        colorScheme.primary.copy(alpha = 0.16f),
                        colorScheme.background,
                        colorScheme.tertiary.copy(alpha = 0.12f),
                    )
                }
            )
            NimiyoStyleTokens(
                uiStyle = uiStyle,
                isDark = isDark,
                isGlass = true,
                cardBorder = BorderStroke(1.dp, glassEdgeBrush),
                cardElevation = 0.dp,
                cornerRadius = 24.dp,
                cardSurface = colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.42f else 0.56f),
                navBarBg = colorScheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.52f else 0.68f),
                glassBrush = glassCardBrush,
                glassBorderBrush = glassEdgeBrush,
                backdropBrush = ambientBackdrop,
            )
        }
        UiStyle.CUPERTINO -> {
            val iosDivider = if (isDark) Color(0xFF38383A) else Color(0xFFD1D1D6)
            val iosCardBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
            val iosNavBg = if (isDark) Color(0xEB161618) else Color(0xEBF9F9F9)
            NimiyoStyleTokens(
                uiStyle = uiStyle,
                isDark = isDark,
                isCupertino = true,
                cardBorder = BorderStroke(0.5.dp, iosDivider),
                cardElevation = 0.dp,
                cornerRadius = 14.dp,
                cardSurface = iosCardBg,
                navBarBg = iosNavBg,
                cupertinoDividerColor = iosDivider,
            )
        }
        UiStyle.MATERIAL -> NimiyoStyleTokens(
            uiStyle = uiStyle,
            isDark = isDark,
            cardBorder = null,
            cardElevation = 0.dp,
            cornerRadius = 20.dp,
            cardSurface = colorScheme.surfaceContainerLow,
            navBarBg = colorScheme.surfaceContainer,
        )
    }

    val shapes = Shapes(
        small = RoundedCornerShape((tokens.cornerRadius * 0.6f)),
        medium = RoundedCornerShape(tokens.cornerRadius),
        large = RoundedCornerShape((tokens.cornerRadius * 1.25f)),
    )

    CompositionLocalProvider(
        LocalNimiyoStyle provides tokens,
        LocalContentColor provides colorScheme.onBackground,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = shapes,
            typography = Typography(),
            content = content,
        )
    }
}
