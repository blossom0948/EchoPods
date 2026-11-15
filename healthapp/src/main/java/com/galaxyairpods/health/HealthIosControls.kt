package com.galaxyairpods.health

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/**
 * Echo Health's iOS appearance uses the same optical press language as EchoPods' StyledButton:
 * backdrop refraction, restrained lensing, spring response, and a contextual haptic.
 */
@Composable
internal fun HealthIosGlassControl(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = 28.dp,
    pressedScale: Float = 0.96f,
    surfaceOpacity: Float? = null,
    contentAlignment: Alignment = Alignment.Center,
    role: Role = Role.Button,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motionEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val progress by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = if (motionEnabled) spring(dampingRatio = 0.86f, stiffness = 700f) else tween(0),
        label = "echo_glass_press",
    )
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(cornerRadius)
    val surfaceBase = MaterialTheme.colorScheme.surface
    val surfaceAlpha = surfaceOpacity ?: if (selected) 0.48f else 0.24f
    val tintOpacity = when {
        !enabled -> 0.06f
        selected -> if (dark) 0.22f else 0.18f
        pressed -> if (dark) 0.21f else 0.14f
        else -> if (dark) 0.12f else 0.08f
    }

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                highlight = { Highlight.Ambient.copy(alpha = if (dark) 0.8f else 0.18f) },
                effects = {
                    vibrancy()
                    blur(1.5.dp.toPx())
                    lens(
                        refractionHeight = 5.dp.toPx(),
                        refractionAmount = 7.dp.toPx(),
                        depthEffect = false,
                        chromaticAberration = false,
                    )
                },
                layerBlock = {
                    val scale = lerp(1f, pressedScale.coerceIn(0.94f, 1f), progress)
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    drawRect(surfaceBase.copy(alpha = surfaceAlpha))
                    drawRect(tint.copy(alpha = tintOpacity))
                    if (selected || pressed) {
                        drawRect(Color.White.copy(alpha = if (dark) 0.10f else 0.22f))
                    }
                },
                onDrawFront = {
                    if (progress > 0f) {
                        drawRect(Color.White.copy(alpha = 0.18f * progress))
                    }
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = role,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}
