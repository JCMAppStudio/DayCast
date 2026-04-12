package com.example.daycast.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =========================================
// Light Color Scheme
// =========================================

private val DayCastLightColorScheme = lightColorScheme(
    primary                = DayCastPrimary,
    onPrimary              = DayCastOnPrimary,
    primaryContainer       = DayCastPrimaryContainer,
    onPrimaryContainer     = DayCastOnPrimaryContainer,

    secondary              = DayCastSecondary,
    onSecondary            = DayCastOnSecondary,
    secondaryContainer     = DayCastSecondaryContainer,
    onSecondaryContainer   = DayCastOnSecondaryContainer,

    tertiary               = DayCastTertiary,
    onTertiary             = DayCastOnTertiary,
    tertiaryContainer      = DayCastTertiaryContainer,
    onTertiaryContainer    = DayCastOnTertiaryContainer,

    error                  = DayCastError,
    onError                = DayCastOnError,
    errorContainer         = DayCastErrorContainer,
    onErrorContainer       = DayCastOnErrorContainer,

    background             = DayCastBackground,
    onBackground           = DayCastOnBackground,
    surface                = DayCastSurface,
    onSurface              = DayCastOnSurface,
    surfaceVariant         = DayCastSurfaceVariant,
    onSurfaceVariant       = DayCastOnSurfaceVariant,
    outline                = DayCastOutline,
    outlineVariant         = DayCastOutlineVariant,
    surfaceContainer       = DayCastSurfaceContainer
)

// =========================================
// Dark Color Scheme
// =========================================

private val DayCastDarkColorScheme = darkColorScheme(
    primary                = DayCastPrimaryDark,
    onPrimary              = DayCastOnPrimaryDark,
    primaryContainer       = DayCastPrimaryContainerDark,
    onPrimaryContainer     = DayCastOnPrimaryContainerDark,

    secondary              = DayCastSecondaryDark,
    onSecondary            = DayCastOnSecondaryDark,
    secondaryContainer     = DayCastSecondaryContainerDark,
    onSecondaryContainer   = DayCastOnSecondaryContainerDark,

    tertiary               = DayCastTertiaryDark,
    onTertiary             = DayCastOnTertiaryDark,
    tertiaryContainer      = DayCastTertiaryContainerDark,
    onTertiaryContainer    = DayCastOnTertiaryContainerDark,

    error                  = DayCastErrorDark,
    onError                = DayCastOnErrorDark,
    errorContainer         = DayCastErrorContainerDark,
    onErrorContainer       = DayCastOnErrorContainerDark,

    background             = DayCastBackgroundDark,
    onBackground           = DayCastOnBackgroundDark,
    surface                = DayCastSurfaceDark,
    onSurface              = DayCastOnSurfaceDark,
    surfaceVariant         = DayCastSurfaceVariantDark,
    onSurfaceVariant       = DayCastOnSurfaceVariantDark,
    outline                = DayCastOutlineDark,
    outlineVariant         = DayCastOutlineVariantDark,
    surfaceContainer       = DayCastSurfaceContainerDark
)

// =========================================
// DayCast Theme
// =========================================

/**
 * DayCast Material 3 theme.
 *
 * - On Android 12+ (API 31+): supports dynamic color (follows the user's wallpaper palette).
 *   Set [dynamicColor] to false to always use the DayCast brand palette.
 * - Automatically switches between light and dark based on system setting.
 * - Updates the status bar and navigation bar to match the surface color.
 */
@Composable
fun DayCastTheme(
    darkTheme: Boolean    = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,   // set true to enable Material You wallpaper colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else           dynamicLightColorScheme(context)
        }
        darkTheme -> DayCastDarkColorScheme
        else      -> DayCastLightColorScheme
    }

    // Keep status bar and nav bar visually integrated
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}