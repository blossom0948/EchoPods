package com.galaxyairpods.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AirPodsColors = darkColorScheme(
	primary = Color(0xFF91B2FF),
	onPrimary = Color(0xFF10264B),
	secondary = Color(0xFFBAC8E8),
	onSecondary = Color(0xFF20283B),
	tertiary = Color(0xFF27C45A),
	background = Color(0xFF27272B),
	onBackground = Color(0xFFF2F1F5),
	surface = Color(0xFF1C1C20),
	onSurface = Color(0xFFF2F1F5),
	surfaceVariant = Color(0xFF303035),
	onSurfaceVariant = Color(0xFFB4B3BC),
	outline = Color(0xFF48484F),
    error = Color(0xFFFFB4AB),
)

@Composable
fun AirPodsGalaxyTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AirPodsColors,
        typography = Typography(),
        content = content,
    )
}
