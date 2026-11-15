package com.galaxyairpods.health

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val EchoLight = lightColorScheme(
    primary = Color(0xFF007D70),
    onPrimary = Color.White,
    secondary = Color(0xFF3E756E),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF171A1C),
    surface = Color.White,
    onSurface = Color(0xFF171A1C),
    surfaceVariant = Color(0xFFE8E8ED),
    onSurfaceVariant = Color(0xFF62666B),
    outline = Color(0xFFD5D6DA),
)

private val EchoDark = darkColorScheme(
    primary = Color(0xFF8FE2D2),
    onPrimary = Color(0xFF003730),
    secondary = Color(0xFF9ACFC5),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF4F4F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF4F4F7),
    surfaceVariant = Color(0xFF2B2B30),
    onSurfaceVariant = Color(0xFFB6B8BE),
    outline = Color(0xFF45464A),
)

@Composable
fun HealthTheme(useMaterialAppearance: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = if (useMaterialAppearance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) EchoDark else EchoLight
    } else if (dark) {
        EchoDark
    } else {
        EchoLight
    }
    MaterialTheme(colorScheme = colors, content = content)
}
