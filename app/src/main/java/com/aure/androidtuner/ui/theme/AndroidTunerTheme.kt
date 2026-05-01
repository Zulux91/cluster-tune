package com.aure.androidtuner.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aure.androidtuner.model.AppColorSource
import com.aure.androidtuner.model.AppSettings
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun AndroidTunerTheme(
    settings: AppSettings = AppSettings(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current

    val colorScheme = when {
        settings.colorSource == AppColorSource.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        settings.colorSource == AppColorSource.CUSTOM_ACCENT -> {
            seededColorScheme(
                seedColor = settings.accentColor,
                darkTheme = darkTheme,
            )
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private fun seededColorScheme(
    seedColor: Int,
    darkTheme: Boolean,
): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seedColor), darkTheme, 0.0)
    val colors = MaterialDynamicColors()

    return if (darkTheme) {
        darkColorScheme(
            primary = Color(colors.primary().getArgb(scheme)),
            onPrimary = Color(colors.onPrimary().getArgb(scheme)),
            primaryContainer = Color(colors.primaryContainer().getArgb(scheme)),
            onPrimaryContainer = Color(colors.onPrimaryContainer().getArgb(scheme)),
            inversePrimary = Color(colors.inversePrimary().getArgb(scheme)),
            secondary = Color(colors.secondary().getArgb(scheme)),
            onSecondary = Color(colors.onSecondary().getArgb(scheme)),
            secondaryContainer = Color(colors.secondaryContainer().getArgb(scheme)),
            onSecondaryContainer = Color(colors.onSecondaryContainer().getArgb(scheme)),
            tertiary = Color(colors.tertiary().getArgb(scheme)),
            onTertiary = Color(colors.onTertiary().getArgb(scheme)),
            tertiaryContainer = Color(colors.tertiaryContainer().getArgb(scheme)),
            onTertiaryContainer = Color(colors.onTertiaryContainer().getArgb(scheme)),
            background = Color(colors.background().getArgb(scheme)),
            onBackground = Color(colors.onBackground().getArgb(scheme)),
            surface = Color(colors.surface().getArgb(scheme)),
            onSurface = Color(colors.onSurface().getArgb(scheme)),
            surfaceVariant = Color(colors.surfaceVariant().getArgb(scheme)),
            onSurfaceVariant = Color(colors.onSurfaceVariant().getArgb(scheme)),
            surfaceTint = Color(colors.primary().getArgb(scheme)),
            inverseSurface = Color(colors.inverseSurface().getArgb(scheme)),
            inverseOnSurface = Color(colors.inverseOnSurface().getArgb(scheme)),
            error = Color(colors.error().getArgb(scheme)),
            onError = Color(colors.onError().getArgb(scheme)),
            errorContainer = Color(colors.errorContainer().getArgb(scheme)),
            onErrorContainer = Color(colors.onErrorContainer().getArgb(scheme)),
            outline = Color(colors.outline().getArgb(scheme)),
            outlineVariant = Color(colors.outlineVariant().getArgb(scheme)),
            scrim = Color(colors.scrim().getArgb(scheme)),
            surfaceBright = Color(colors.surfaceBright().getArgb(scheme)),
            surfaceDim = Color(colors.surfaceDim().getArgb(scheme)),
            surfaceContainer = Color(colors.surfaceContainer().getArgb(scheme)),
            surfaceContainerHigh = Color(colors.surfaceContainerHigh().getArgb(scheme)),
            surfaceContainerHighest = Color(colors.surfaceContainerHighest().getArgb(scheme)),
            surfaceContainerLow = Color(colors.surfaceContainerLow().getArgb(scheme)),
            surfaceContainerLowest = Color(colors.surfaceContainerLowest().getArgb(scheme)),
        )
    } else {
        lightColorScheme(
            primary = Color(colors.primary().getArgb(scheme)),
            onPrimary = Color(colors.onPrimary().getArgb(scheme)),
            primaryContainer = Color(colors.primaryContainer().getArgb(scheme)),
            onPrimaryContainer = Color(colors.onPrimaryContainer().getArgb(scheme)),
            inversePrimary = Color(colors.inversePrimary().getArgb(scheme)),
            secondary = Color(colors.secondary().getArgb(scheme)),
            onSecondary = Color(colors.onSecondary().getArgb(scheme)),
            secondaryContainer = Color(colors.secondaryContainer().getArgb(scheme)),
            onSecondaryContainer = Color(colors.onSecondaryContainer().getArgb(scheme)),
            tertiary = Color(colors.tertiary().getArgb(scheme)),
            onTertiary = Color(colors.onTertiary().getArgb(scheme)),
            tertiaryContainer = Color(colors.tertiaryContainer().getArgb(scheme)),
            onTertiaryContainer = Color(colors.onTertiaryContainer().getArgb(scheme)),
            background = Color(colors.background().getArgb(scheme)),
            onBackground = Color(colors.onBackground().getArgb(scheme)),
            surface = Color(colors.surface().getArgb(scheme)),
            onSurface = Color(colors.onSurface().getArgb(scheme)),
            surfaceVariant = Color(colors.surfaceVariant().getArgb(scheme)),
            onSurfaceVariant = Color(colors.onSurfaceVariant().getArgb(scheme)),
            surfaceTint = Color(colors.primary().getArgb(scheme)),
            inverseSurface = Color(colors.inverseSurface().getArgb(scheme)),
            inverseOnSurface = Color(colors.inverseOnSurface().getArgb(scheme)),
            error = Color(colors.error().getArgb(scheme)),
            onError = Color(colors.onError().getArgb(scheme)),
            errorContainer = Color(colors.errorContainer().getArgb(scheme)),
            onErrorContainer = Color(colors.onErrorContainer().getArgb(scheme)),
            outline = Color(colors.outline().getArgb(scheme)),
            outlineVariant = Color(colors.outlineVariant().getArgb(scheme)),
            scrim = Color(colors.scrim().getArgb(scheme)),
            surfaceBright = Color(colors.surfaceBright().getArgb(scheme)),
            surfaceDim = Color(colors.surfaceDim().getArgb(scheme)),
            surfaceContainer = Color(colors.surfaceContainer().getArgb(scheme)),
            surfaceContainerHigh = Color(colors.surfaceContainerHigh().getArgb(scheme)),
            surfaceContainerHighest = Color(colors.surfaceContainerHighest().getArgb(scheme)),
            surfaceContainerLow = Color(colors.surfaceContainerLow().getArgb(scheme)),
            surfaceContainerLowest = Color(colors.surfaceContainerLowest().getArgb(scheme)),
        )
    }
}
