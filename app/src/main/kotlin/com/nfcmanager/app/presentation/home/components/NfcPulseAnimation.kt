package com.nfcmanager.app.presentation.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.presentation.theme.LocalAppColors

private object PulseConstants {
    const val CYCLE_DURATION = 2000
    const val GLOW_DURATION = 900
    const val MIN_GLOW_SCALE = 0.92f
    const val MAX_GLOW_SCALE = 1.08f
    const val RING_COUNT = 3
    const val RING_STROKE_WIDTH = 3.5f
    const val MAX_ALPHA = 0.35f
    val GLOW_BASE_SIZE = 96.dp
}

/**
 * Premium NFC pulse animation. 
 * Consists of concentric rings starting from [minRadiusDp] and a pulsing radial glow.
 */
@Composable
fun NfcPulseAnimation(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    color: Color? = null,
    minRadiusDp: Dp = 0.dp,
) {
    if (!isActive) return

    val colors = LocalAppColors.current
    val waveColor = color ?: colors.accent
    val density = LocalDensity.current
    val minRPx = with(density) { minRadiusDp.toPx() }

    val transition = rememberInfiniteTransition(label = "nfcPulse")
    
    val ringProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PulseConstants.CYCLE_DURATION, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringProgress",
    )
    
    val glowScale by transition.animateFloat(
        initialValue = PulseConstants.MIN_GLOW_SCALE,
        targetValue = PulseConstants.MAX_GLOW_SCALE,
        animationSpec = infiniteRepeatable(
            animation = tween(PulseConstants.GLOW_DURATION, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowScale",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // 1. Expanding rings (Canvas)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPulseRings(
                progress = ringProgress,
                minRadiusPx = minRPx,
                color = waveColor
            )
        }

        // 2. Inner radial glow (Optimized with graphicsLayer)
        Box(
            modifier = Modifier
                .size(PulseConstants.GLOW_BASE_SIZE)
                .graphicsLayer {
                    scaleX = glowScale
                    scaleY = glowScale
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.gradientStart.copy(alpha = 0.25f),
                            colors.gradientEnd.copy(alpha = 0.10f),
                        )
                    )
                ),
        )
    }
}

private fun DrawScope.drawPulseRings(
    progress: Float,
    minRadiusPx: Float,
    color: Color
) {
    val maxRadiusPx = size.minDimension / 2f
    val effectiveMinR = minRadiusPx.coerceAtMost(maxRadiusPx)
    
    repeat(PulseConstants.RING_COUNT) { i ->
        val offset = (progress + i.toFloat() / PulseConstants.RING_COUNT) % 1f
        val radius = effectiveMinR + offset * (maxRadiusPx - effectiveMinR)
        val alpha = (1f - offset).coerceIn(0f, 1f) * PulseConstants.MAX_ALPHA
        
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius,
            center = center,
            style = Stroke(
                width = PulseConstants.RING_STROKE_WIDTH, 
                cap = StrokeCap.Round
            ),
        )
    }
}
