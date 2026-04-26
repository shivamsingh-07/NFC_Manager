package com.nfcmanager.app.presentation.actions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Rocket
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nfcmanager.app.domain.model.ActionPayload
import com.nfcmanager.app.domain.model.NfcAction
import com.nfcmanager.app.presentation.components.TabScreenHeader
import com.nfcmanager.app.presentation.theme.ButtonShape
import com.nfcmanager.app.presentation.theme.LocalAppColors
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Spring

@Composable
fun ActionsScreen(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActionsViewModel = hiltViewModel(),
) {
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val C = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "fabScale"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.statusBarsPadding()) {
                Spacer(Modifier.height(16.dp))
                TabScreenHeader(
                    title = "Actions",
                    icon = Icons.Rounded.Bolt,
                    subtitle = "Map any tag to a custom action",
                )
                Spacer(Modifier.height(16.dp))
            }

            if (actions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = "No actions",
                            style = MaterialTheme.typography.headlineSmall,
                            color = C.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "Tap \"New action\" to map a tag.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = C.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 140.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = C.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, C.outlineVariant.copy(alpha = 0.55f)),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            actions.forEachIndexed { index, action ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = C.outlineVariant.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                ActionRowContent(
                                    action = action,
                                    onDelete = { viewModel.delete(action.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 104.dp),
        ) {
            Surface(
                onClick = onCreate,
                color = C.accent,
                contentColor = C.onAccent,
                shape = ButtonShape,
                interactionSource = interaction,
                modifier = Modifier
                    .scale(scale)
                    .shadow(
                        elevation = if (pressed) 4.dp else 12.dp,
                        shape = ButtonShape,
                        ambientColor = Color.Black.copy(alpha = 0.2f),
                        spotColor = C.accent.copy(alpha = 0.4f)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "New action",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRowContent(action: NfcAction, onDelete: () -> Unit) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(C.elevatedSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconFor(action.payload),
                contentDescription = null,
                tint = C.textPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.titleMedium,
                color = C.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitleFor(action.payload),
                style = MaterialTheme.typography.bodyMedium,
                color = C.textSecondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete",
                tint = C.error,
            )
        }
    }
}

private fun iconFor(payload: ActionPayload): ImageVector = when (payload) {
    is ActionPayload.OpenUrl -> Icons.Rounded.Link
    is ActionPayload.OpenApp -> Icons.Rounded.Rocket
    is ActionPayload.ConnectWifi -> Icons.Rounded.Wifi
    is ActionPayload.ConnectBluetooth -> Icons.Rounded.Bluetooth
    ActionPayload.ToggleFlashlight -> Icons.Rounded.FlashOn
}

private fun subtitleFor(payload: ActionPayload): String = when (payload) {
    is ActionPayload.OpenUrl -> "Web · ${payload.url.removePrefix("https://").removePrefix("http://")}"
    is ActionPayload.OpenApp -> {
        val name = payload.label?.takeIf { it.isNotBlank() } ?: payload.packageName
        "App · Launch $name"
    }
    is ActionPayload.ConnectWifi -> "Wi-Fi · Connect to ${payload.ssid}"
    is ActionPayload.ConnectBluetooth -> "Bluetooth · Connect to ${payload.deviceName}"
    ActionPayload.ToggleFlashlight -> "System · Toggle flashlight"
}
