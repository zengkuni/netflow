package netflow.dev.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun NetflowTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val dpColors = if (isDark) DarkDPColors else LightDPColors
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = dpColors.accent,
            onPrimary = dpColors.accentOn,
            primaryContainer = HeroInk,
            onPrimaryContainer = dpColors.textPrimary,

            secondary = dpColors.info,
            onSecondary = dpColors.infoOn,

            background = dpColors.ink,
            onBackground = dpColors.textPrimary,

            surface = dpColors.ink,
            onSurface = dpColors.textPrimary,

            surfaceVariant = dpColors.surfaceMid,
            onSurfaceVariant = dpColors.textSecondary,

            surfaceContainerLowest = dpColors.ink,
            surfaceContainerLow = dpColors.surfaceLow,
            surfaceContainer = dpColors.surfaceMid,
            surfaceContainerHigh = dpColors.surfaceHigh,
            surfaceContainerHighest = dpColors.surfaceHigh,

            outline = dpColors.outlineStrong,
            outlineVariant = dpColors.outlineSoft,

            error = dpColors.danger,
            onError = dpColors.errorOn,
        )
    } else {
        lightColorScheme(
            primary = dpColors.accent,
            onPrimary = dpColors.accentOn,
            primaryContainer = HeroInk,
            onPrimaryContainer = dpColors.textPrimary,

            secondary = dpColors.info,
            onSecondary = dpColors.infoOn,

            background = dpColors.ink,
            onBackground = dpColors.textPrimary,

            surface = dpColors.ink,
            onSurface = dpColors.textPrimary,

            surfaceVariant = dpColors.surfaceMid,
            onSurfaceVariant = dpColors.textSecondary,

            surfaceContainerLowest = dpColors.ink,
            surfaceContainerLow = dpColors.surfaceLow,
            surfaceContainer = dpColors.surfaceMid,
            surfaceContainerHigh = dpColors.surfaceHigh,
            surfaceContainerHighest = dpColors.surfaceHigh,

            outline = dpColors.outlineStrong,
            outlineVariant = dpColors.outlineSoft,

            error = dpColors.danger,
            onError = dpColors.errorOn,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    // The dialog scrim is the one Material3 token ColorScheme does not expose
    // in this version, so it is set per-dialog through DialogProperties.scrim
    // at the two AlertDialog call sites in AppNav.kt. 0x661F2922 is the
    // style's ink family at 40%, which reads as a veil over warm paper instead
    // of the platform default's cold black.
    CompositionLocalProvider(LocalDPColors provides dpColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NetflowTypography,
            content = content,
        )
    }
}
