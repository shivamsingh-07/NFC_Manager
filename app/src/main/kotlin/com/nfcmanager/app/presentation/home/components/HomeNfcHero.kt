package com.nfcmanager.app.presentation.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfcmanager.app.R
import com.nfcmanager.app.data.nfc.NfcReaderManager
import com.nfcmanager.app.presentation.theme.LocalAppColors

private object HomeHeroConstants {
    val ICON_SIZE = 88.dp
    val ICON_RADIUS = 44.dp
    val CONTAINER_SIZE_ACTIVE = 180.dp
    val CONTAINER_SIZE_IDLE = 120.dp
    const val PULSE_SCALE_MIN = 0.95f
    const val PULSE_SCALE_MAX = 1.05f
    const val PULSE_DURATION = 900
}

@Composable
fun HomeNfcHero(
    readerState: NfcReaderManager.State,
    hardware: NfcReaderManager.HardwareStatus,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp
    
    val unsupported = hardware == NfcReaderManager.HardwareStatus.Unsupported
    val disabled = hardware == NfcReaderManager.HardwareStatus.Disabled
    val nfcRippleActive = hardware == NfcReaderManager.HardwareStatus.Enabled && 
                          readerState == NfcReaderManager.State.Scanning

    val title = when {
        unsupported -> "NFC Not Supported"
        disabled -> "NFC is turned off"
        else -> "Ready to Scan"
    }

    val subtitle = when {
        unsupported -> "Your device doesn't support NFC"
        disabled -> "Enable NFC in settings"
        else -> "Hold your device near the tag"
    }

    val iconTint = when {
        unsupported -> C.error
        disabled -> C.textSecondary
        readerState == NfcReaderManager.State.Scanning -> C.onAccentContainer
        else -> C.accent.copy(alpha = 0.9f)
    }

    val iconContainerSize by animateDpAsState(
        targetValue = if (nfcRippleActive) HomeHeroConstants.CONTAINER_SIZE_ACTIVE else HomeHeroConstants.CONTAINER_SIZE_IDLE,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "containerSize"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(0.85f))

        Box(
            modifier = Modifier
                .size(iconContainerSize)
                .offset(y = (-8).dp)
                .then(
                    if (nfcRippleActive) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            NfcPulseAnimation(
                isActive = nfcRippleActive,
                modifier = Modifier.matchParentSize(),
                color = C.accent,
                minRadiusDp = HomeHeroConstants.ICON_RADIUS
            )

            val iconScale by animateFloatAsState(
                targetValue = if (nfcRippleActive) HomeHeroConstants.PULSE_SCALE_MAX else 1f,
                animationSpec = if (nfcRippleActive) {
                    infiniteRepeatable(
                        animation = tween(HomeHeroConstants.PULSE_DURATION, easing = EaseInOutCubic),
                        repeatMode = RepeatMode.Reverse
                    )
                } else {
                    spring()
                },
                label = "iconScale"
            )

            Surface(
                modifier = Modifier
                    .size(HomeHeroConstants.ICON_SIZE)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                shape = CircleShape,
                color = if (nfcRippleActive) C.accentContainer else C.surface,
                tonalElevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (unsupported) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                if (disabled) R.drawable.ic_nfc_off else R.drawable.ic_nfc_phone_mark
                            ),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height((screenHeight * 0.05f).coerceAtLeast(24.dp)))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = if (unsupported) C.error else C.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = C.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.weight(1.15f))
    }
}
