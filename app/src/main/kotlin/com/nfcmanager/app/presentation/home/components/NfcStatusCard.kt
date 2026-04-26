package com.nfcmanager.app.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.R
import com.nfcmanager.app.data.nfc.NfcReaderManager
import com.nfcmanager.app.data.prefs.ThemeMode
import com.nfcmanager.app.presentation.home.components.NfcPulseAnimation
import com.nfcmanager.app.presentation.theme.LocalAppColors
import com.nfcmanager.app.presentation.theme.NfcManagerTheme

/**
 * Primary home status surface: NFC hardware + reader state.
 */
@Composable
fun NfcStatusCard(
    readerState: NfcReaderManager.State,
    hardware: NfcReaderManager.HardwareStatus,
    modifier: Modifier = Modifier,
) {
    val C = LocalAppColors.current
    val unsupported = hardware == NfcReaderManager.HardwareStatus.Unsupported
    val disabled = hardware == NfcReaderManager.HardwareStatus.Disabled
    val errorLike = disabled || unsupported

    val containerColor = when {
        errorLike -> C.errorContainer
        readerState == NfcReaderManager.State.Scanning -> C.accentContainer
        else -> C.elevatedSurface
    }
    val onContainer = when {
        errorLike -> C.onErrorContainer
        readerState == NfcReaderManager.State.Scanning -> C.onAccentContainer
        else -> C.textPrimary
    }
    val subtitleColor = when {
        errorLike -> C.onErrorContainer.copy(alpha = 0.85f)
        else -> C.textSecondary
    }

    val (title, subtitle) = statusCopy(readerState, unsupported, disabled)

    val nfcRippleActive =
        !errorLike &&
            hardware == NfcReaderManager.HardwareStatus.Enabled &&
            readerState == NfcReaderManager.State.Scanning

    val shape = MaterialTheme.shapes.extraLarge
    val rim = BorderStroke(1.dp, C.outlineVariant.copy(alpha = 0.6f))
    val rippleColor = C.accent.copy(alpha = 0.28f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(rim, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 32.dp)
                    .then(
                        if (nfcRippleActive) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, color = rippleColor),
                                onClick = {},
                            )
                        } else {
                            Modifier
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = readerState to unsupported,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.92f) + fadeIn()) togetherWith fadeOut()
                    },
                    label = "nfcStatusIcon",
                ) { (_, uns) ->
                    val icon = when {
                        uns -> Icons.Rounded.ErrorOutline
                        disabled -> Icons.Rounded.WifiOff
                        else -> null
                    }
                    Box(
                        modifier = Modifier.size(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (nfcRippleActive) {
                        NfcPulseAnimation(
                            isActive = nfcRippleActive,
                            modifier = Modifier.matchParentSize(),
                            minRadiusDp = 40.dp
                        )
                        }
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = onContainer.copy(alpha = 0.12f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val tint = if (errorLike) C.error else C.accent
                                if (icon != null) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = tint,
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_nfc_phone_mark),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = tint,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = onContainer,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = subtitleColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun statusCopy(
    readerState: NfcReaderManager.State,
    unsupported: Boolean,
    disabled: Boolean,
): Pair<String, String> = when {
    unsupported -> stringResource(R.string.home_nfc_unavailable_title) to
        stringResource(R.string.home_nfc_unavailable_subtitle)
    disabled -> stringResource(R.string.home_nfc_off_title) to
        stringResource(R.string.home_nfc_off_subtitle)
    readerState == NfcReaderManager.State.Scanning ->
        stringResource(R.string.scan_ready_title) to stringResource(R.string.home_nfc_scanning_hint)
    else ->
        stringResource(R.string.home_nfc_idle_title) to stringResource(R.string.home_nfc_idle_subtitle)
}

@Preview(showBackground = true)
@Composable
private fun NfcStatusCardOffPreview() {
    NfcManagerTheme(themeMode = ThemeMode.DARK) {
        NfcStatusCard(
            readerState = NfcReaderManager.State.Disabled,
            hardware = NfcReaderManager.HardwareStatus.Disabled,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NfcStatusCardScanningPreview() {
    NfcManagerTheme(themeMode = ThemeMode.DARK) {
        NfcStatusCard(
            readerState = NfcReaderManager.State.Scanning,
            hardware = NfcReaderManager.HardwareStatus.Enabled,
            modifier = Modifier.padding(16.dp),
        )
    }
}
