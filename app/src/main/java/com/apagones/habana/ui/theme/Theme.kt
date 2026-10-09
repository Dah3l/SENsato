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

// Paleta inspirada en la electricidad: dorado eléctrico (rayo) sobre fondo oscuro profundo.
private val LightColors = lightColorScheme(
    primary = Color(0xFF6C4A00),
    onPrimary = Color.White,
    secondary = Color(0xFF263238),
    tertiary = Color(0xFF00639A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC107),           // Dorado eléctrico brillante (rayo)
    onPrimary = Color(0xFF1E1500),
    primaryContainer = Color(0xFF3B2E00),
    onPrimaryContainer = Color(0xFFFFECB3),
    secondary = Color(0xFF90A4AE),
    onSecondary = Color(0xFF1C252A),
    secondaryContainer = Color(0xFF263238),
    onSecondaryContainer = Color(0xFFECEFF1),
    background = Color(0xFF0F1216),        // Fondo oscuro profundo comercial
    onBackground = Color(0xFFE1E2E5),
    surface = Color(0xFF161A21),           // Superficie oscura pulida
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF222731),    // Tarjetas oscuras elegantes
    onSurfaceVariant = Color(0xFFC4C7D0),
    error = Color(0xFFFF5252),             // Rojo de afectación
    onError = Color(0xFF380000),
    errorContainer = Color(0xFF3E1414),
    onErrorContainer = Color(0xFFFFB4AB)
)

/**
 * Tema Material 3 de la aplicación con identidad eléctrica dorada.
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
