package com.nfcmanager.app.presentation.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ContactMail
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nfcmanager.app.R
import com.nfcmanager.app.data.nfc.NfcTagCaptureManager
import com.nfcmanager.app.domain.model.NfcTag
import com.nfcmanager.app.domain.model.TagPayload
import com.nfcmanager.app.presentation.components.ExpressiveButton
import com.nfcmanager.app.presentation.home.components.NfcPulseAnimation
import com.nfcmanager.app.presentation.theme.LocalAppColors

@Composable
fun ScanBottomSheetHost(viewModel: ScanSheetViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.visible) {
        ScanBottomSheet(onDismiss = viewModel::dismiss, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBottomSheet(
    onDismiss: () -> Unit,
    viewModel: ScanSheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LocalAppColors.current.surface,
        dragHandle = { Spacer(Modifier.height(22.dp)) },
        // Scope insets to the navigation bar only so the sheet's container
        // can paint edge-to-edge while the content area is automatically
        // padded by the real nav bar height — whether that's the gesture
        // pill, 3-button bar, or absent (foldables/tablets without one).
        contentWindowInsets = { WindowInsets.navigationBars },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val error = state.error
                if (error != null) {
                    ErrorContent(error)
                } else if (state.capturedIdentity != null || state.detected != null) {
                    ResultContent(state)
                } else {
                    ScanningContent()
                }

                Spacer(Modifier.height(32.dp))

                ExpressiveButton(
                    text = if (state.capturedIdentity != null || state.detected != null) "Done" else "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ScanningContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Ready to Scan",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Hold your device near the NFC tag",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAppColors.current.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        NfcScanIllustration()
    }
}

@Composable
private fun ResultContent(state: ScanSheetController.State) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(LocalAppColors.current.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.TaskAlt,
                contentDescription = null,
                tint = LocalAppColors.current.onAccentContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (state.capturedIdentity != null) "Tag Captured" else "Tag Detected",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tag read successfully",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAppColors.current.textSecondary,
        )
        Spacer(Modifier.height(24.dp))
        
        if (state.capturedIdentity != null) {
            CaptureSummary(state.capturedIdentity)
        } else if (state.detected != null) {
            TagSummary(state.detected)
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(LocalAppColors.current.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = LocalAppColors.current.onErrorContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Scan Failed",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAppColors.current.error,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TagSummary(tag: NfcTag) {
    val payload = tag.primaryPayload
    val (icon, label) = payloadIconAndLabel(payload)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = LocalAppColors.current.elevatedSurface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(LocalAppColors.current.accentContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = LocalAppColors.current.onAccentContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalAppColors.current.accent,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = summaryFor(payload),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tag.uid,
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textSecondary,
            )
        }
    }
}

@Composable
private fun CaptureSummary(identity: NfcTagCaptureManager.TagIdentity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = LocalAppColors.current.elevatedSurface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tag identity captured",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = identity.techSignature.ifBlank { "No tech signature" },
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.textSecondary,
            )
        }
    }
}

@Composable
fun NfcScanIllustration() {
    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        NfcPulseAnimation(
            isActive = true,
            modifier = Modifier.matchParentSize(),
            minRadiusDp = 42.dp
        )

        Surface(
            modifier = Modifier.size(84.dp),
            shape = CircleShape,
            color = LocalAppColors.current.accentContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_nfc_phone_mark),
                    contentDescription = null,
                    tint = LocalAppColors.current.onAccentContainer,
                    modifier = Modifier.size(42.dp),
                )
            }
        }
    }
}

private fun payloadIconAndLabel(payload: TagPayload?): Pair<ImageVector, String> = when (payload) {
    is TagPayload.Text -> Icons.Rounded.Description to "Text"
    is TagPayload.Uri -> Icons.Rounded.Link to "Link"
    is TagPayload.Contact -> Icons.Rounded.ContactMail to "Contact"
    is TagPayload.WiFi -> Icons.Rounded.Wifi to "Wi-Fi"
    is TagPayload.Bluetooth -> Icons.Rounded.Bluetooth to "Bluetooth"
    is TagPayload.Raw -> Icons.Rounded.DataObject to "Raw"
    null -> Icons.Rounded.DataObject to "Empty"
}

private fun summaryFor(payload: TagPayload?): String = when (payload) {
    is TagPayload.Text -> payload.text
    is TagPayload.Uri -> if (payload.isSafeToOpen) payload.sanitizedUrl!! else payload.raw
    is TagPayload.Contact -> payload.displayName ?: "Unnamed contact"
    is TagPayload.WiFi -> "Network: ${payload.ssid}"
    is TagPayload.Bluetooth -> "Bluetooth Device: ${payload.deviceName}"
    is TagPayload.Raw -> payload.mimeType ?: "${payload.bytes.size} bytes"
    null -> "Tag contains no NDEF data"
}
