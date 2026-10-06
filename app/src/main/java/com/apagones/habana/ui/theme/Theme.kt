package com.apagones.habana.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Paleta inspirada en la electricidad: ámbar (rayo) sobre azul profundo.
private val LightColors = lightColorScheme(
    primary = Color(0xFF6C4A00),
    onPrimary = Color.White,
    secondary = Color(0xFF263238),
    tertiary = Color(0xFF00639A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB300),
    onPrimary = Color(0xFF3E2B00),
    secondary = Color(0xFFB0BEC5),
    tertiary = Color(0xFF7FC4FF)
)

/**
 * Tema Material 3 de la aplicación (soporta claro/oscuro automático).
 */
@Composable
fun ApagonesHabanaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
