package com.nfcmanager.app.presentation.write

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContactMail
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nfcmanager.app.domain.model.TagPayload
import com.nfcmanager.app.domain.model.WriteResult
import com.nfcmanager.app.presentation.components.AnimatedSwitch
import com.nfcmanager.app.presentation.components.ExpressiveButton
import com.nfcmanager.app.presentation.components.TabContentSection
import com.nfcmanager.app.presentation.components.TabScreenHeader
import com.nfcmanager.app.presentation.components.SectionHeader
import com.nfcmanager.app.data.local.SavedMessageEntity
import com.nfcmanager.app.presentation.theme.LocalAppColors
import com.nfcmanager.app.util.findActivity
import kotlinx.coroutines.delay

@Composable
fun WriteScreen(
    modifier: Modifier = Modifier,
    viewModel: WriteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()

    val savedMessages by viewModel.savedMessages.collectAsStateWithLifecycle()
    var expandedMessageId by remember { mutableStateOf<Long?>(null) }

    val showSheet = state.status !is WriteUiState.Status.Idle || state.isEmulating
    BackHandler(enabled = showSheet) {
        viewModel.cancel()
    }

    // Android 14+ stopped implicitly routing `category="other"` AIDs to the
    // owning HCE service while the app is foreground. Without this, the
    // sender's MessageEmulationService never sees a SELECT AID on Android 16,
    // so the receiver shows "Empty Tag". We pin the service for the lifetime
    // of the emulation sheet and unpin on dismissal / activity exit.
    DisposableEffect(activity, state.isEmulating) {
        val act = activity
        if (act != null && state.isEmulating) {
            viewModel.pinHceForeground(act)
        }
        onDispose {
            if (act != null) viewModel.releaseHceForeground(act)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.statusBarsPadding()) {
            Spacer(Modifier.height(16.dp))

            TabScreenHeader(
                title = "Message",
                icon = Icons.AutoMirrored.Rounded.Message,
                subtitle = "Create, save and manage NFC messages",
            )

            Spacer(Modifier.height(16.dp))
        }

        if (!state.isCreatingMessage) {
            ExpressiveButton(
                text = "Add new message",
                onClick = {
                    expandedMessageId = null
                    viewModel.update { it.copy(isCreatingMessage = true) }
                },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            Spacer(Modifier.height(24.dp))
        }

        val ExpansionSpring = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 600f // Snappier
        )
        val ExpansionSizeSpring = spring<androidx.compose.ui.unit.IntSize>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 600f // Snappier
        )

        AnimatedVisibility(
            visible = state.isCreatingMessage,
            enter = fadeIn(ExpansionSpring) + expandVertically(ExpansionSizeSpring),
            exit = fadeOut(ExpansionSpring) + shrinkVertically(ExpansionSizeSpring),
        ) {
            Column {
                SectionHeader(title = "Create Message", modifier = Modifier.padding(bottom = 12.dp))

                TabContentSection {
                    KindSelector(
                        selected   = state.kind,
                        onSelected = { kind -> viewModel.update { it.copy(kind = kind, validationError = null) } },
                    )

                    Spacer(Modifier.height(20.dp))

                    AnimatedContent(
                        targetState = state.kind,
                        transitionSpec = {
                            (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                expandVertically(spring(stiffness = Spring.StiffnessMediumLow))
                            ) togetherWith (
                                fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                                shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow))
                            )
                        },
                        label = "writeFields",
                    ) { kind ->
                        Surface(
                            color = LocalAppColors.current.surface,
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                when (kind) {
                                    WriteKind.TEXT      -> TextFields(state, viewModel)
                                    WriteKind.URL       -> UrlFields(state, viewModel)
                                    WriteKind.CONTACT   -> ContactFields(state, viewModel)
                                    WriteKind.WIFI      -> WifiFields(state, viewModel)
                                    WriteKind.BLUETOOTH -> BluetoothFields(state, viewModel)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = state.validationError != null,
                        enter   = fadeIn() + expandVertically(),
                        exit    = fadeOut() + shrinkVertically(),
                    ) {
                        state.validationError?.let { error ->
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = LocalAppColors.current.error,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = error,
                                    color = LocalAppColors.current.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExpressiveButton(
                            text = "Save",
                            onClick = viewModel::saveMessage,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        ExpressiveButton(
                            text = "Cancel",
                            onClick = { viewModel.update { it.copy(isCreatingMessage = false) } },
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        
        SectionHeader(title = "Saved Messages", modifier = Modifier.padding(bottom = 12.dp))
        
        savedMessages.forEach { message ->
            SavedMessageCard(
                message = message,
                isExpanded = expandedMessageId == message.id,
                onToggleExpand = {
                    expandedMessageId = if (expandedMessageId == message.id) null else message.id
                    if (expandedMessageId != null) {
                        viewModel.update { it.copy(isCreatingMessage = false) }
                    }
                },
                onEmulate = {
                    if (viewModel.isNfcReady()) {
                        viewModel.emulateMessage(message)
                    } else {
                        viewModel.update { it.copy(status = WriteUiState.Status.NfcDisabled) }
                    }
                },
                onDelete = { viewModel.deleteMessage(message) },
                onWrite = {
                    if (viewModel.isNfcReady()) {
                        viewModel.loadMessageForWriting(message)
                        activity?.let(viewModel::arm)
                    } else {
                        viewModel.update { it.copy(status = WriteUiState.Status.NfcDisabled) }
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        if (savedMessages.isEmpty()) {
            Text(
                "No saved messages.",
                color = LocalAppColors.current.textSecondary,
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ─── Status banner ────────────────────────────────────────────────
        if (showSheet) {
            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            LaunchedEffect(state.status, state.isEmulating) {
                if (state.status is WriteUiState.Status.WaitingForTag || state.isEmulating) {
                    // Increased timeout for better UX during device alignment
                    delay(45000L) 
                    viewModel.cancel()
                }
            }

            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.cancel()
                },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                containerColor = LocalAppColors.current.surface,
                // Let the navigation bar inset drive the bottom padding so
                // gesture nav, 3-button nav, and devices with no nav bar all
                // get the right spacing without hardcoded heights.
                contentWindowInsets = { WindowInsets.navigationBars },
                dragHandle = {
                    Spacer(Modifier.height(22.dp))
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedContent(
                        targetState = state.status is WriteUiState.Status.Done,
                        transitionSpec = {
                            (scaleIn(tween(260), initialScale = 0.92f) + fadeIn(tween(220))) togetherWith
                                fadeOut(tween(160))
                        },
                        label = "writeSheetContent",
                    ) { isDone ->
                        if (isDone) {
                            val result = (state.status as WriteUiState.Status.Done).result
                            val success = result is WriteResult.Success
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(if (success) LocalAppColors.current.accentContainer else LocalAppColors.current.errorContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (success) Icons.Rounded.TaskAlt else Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (success) LocalAppColors.current.onAccentContainer else LocalAppColors.current.onErrorContainer,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = if (success) "Tag Written Successfully" else "Write Failed",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                if (!success) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = writeFailureReasonLabel((result as WriteResult.Failure).reason),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LocalAppColors.current.error,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        } else {
                            // Non-Done sheet states. Layout intentionally splits along the
                            // "is there a live NFC operation?" axis:
                            //   - Ripple path (Emulate / Write / Peer Connected): the
                            //     animation is the focal point, so the title + subtitle
                            //     sit on top and the pulse is centered below — mirrors
                            //     the Reading sheet (ScanBottomSheet.ScanningContent).
                            //   - NfcDisabled (an error, not a ripple): icon-led, text
                            //     below — matches the Done / Detected / Error sheets so
                            //     the user reads the layout as "something to fix".
                            val isNfcDisabled = state.status is WriteUiState.Status.NfcDisabled

                            val titleText = when {
                                isNfcDisabled -> "NFC is Disabled"
                                state.isPeerConnected -> "Peer Connected"
                                state.isEmulating -> "Ready to Share"
                                else -> "Ready to Write"
                            }
                            val subtitleText = when {
                                isNfcDisabled -> "Please enable NFC in your device settings to continue."
                                state.isPeerConnected -> "Transferring data..."
                                state.isEmulating -> "Tap another device to share"
                                else -> "Hold your device near an NFC tag"
                            }
                            val subtitleColor = if (isNfcDisabled) {
                                LocalAppColors.current.error
                            } else {
                                LocalAppColors.current.textSecondary
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isNfcDisabled) {
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
                                    Spacer(Modifier.height(24.dp))
                                    Text(
                                        text = titleText,
                                        style = MaterialTheme.typography.headlineSmall,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = subtitleColor,
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    Text(
                                        text = titleText,
                                        style = MaterialTheme.typography.headlineSmall,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = subtitleColor,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    com.nfcmanager.app.presentation.scan.NfcScanIllustration()
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(20.dp))

                    ExpressiveButton(
                        text = if (state.status is WriteUiState.Status.Done || state.status is WriteUiState.Status.NfcDisabled) "Close" else "Cancel",
                        onClick = {
                            viewModel.cancel()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(140.dp))
    }
}

// ─── Kind selector ────────────────────────────────────────────────────────────

@Composable
private fun KindSelector(selected: WriteKind, onSelected: (WriteKind) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        WriteKind.entries.forEach { kind ->
            val isSelected = kind == selected
            FilterChip(
                selected = isSelected,
                onClick  = { onSelected(kind) },
                label    = { Text(writeKindLabel(kind)) },
                leadingIcon = {
                    Icon(
                        imageVector = writeKindIcon(kind),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                shape  = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LocalAppColors.current.accentContainer,
                    selectedLabelColor     = LocalAppColors.current.onAccentContainer,
                    selectedLeadingIconColor = LocalAppColors.current.onAccentContainer,
                ),
            )
        }
    }
}

// ─── Field groups ─────────────────────────────────────────────────────────────

@Composable
private fun TextFields(state: WriteUiState, vm: WriteViewModel) {
    OutlinedTextField(
        value       = state.text,
        onValueChange = { v -> vm.update { it.copy(text = v) } },
        label       = { Text("Text content") },
        modifier    = Modifier.fillMaxWidth(),
        shape       = RoundedCornerShape(14.dp),
        minLines    = 3,
        maxLines    = 8,
    )
}

@Composable
private fun UrlFields(state: WriteUiState, vm: WriteViewModel) {
    val trimmed    = state.url.trim()
    val validator  = remember { com.nfcmanager.app.domain.util.UrlValidator() }
    val normalized = remember(trimmed) {
        if (trimmed.isEmpty()) null else validator.normalizeForUserInput(trimmed)
    }
    val isInvalid  = trimmed.isNotEmpty() && normalized == null
    val schemeless = trimmed.isNotEmpty() && !trimmed.contains("://") && normalized != null

    OutlinedTextField(
        value         = state.url,
        onValueChange = { v -> vm.update { it.copy(url = v) } },
        label         = { Text("URL") },
        placeholder   = { Text("https://example.com") },
        isError       = isInvalid,
        supportingText = {
            when {
                isInvalid  -> Text("Not a valid or safe URL")
                schemeless -> Text("Will be saved as $normalized")
                else       -> Unit
            }
        },
        modifier    = Modifier.fillMaxWidth(),
        shape       = RoundedCornerShape(14.dp),
        singleLine  = true,
    )
}

@Composable
private fun ContactFields(state: WriteUiState, vm: WriteViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ContactField("Full name", state.contactName) { v -> vm.update { it.copy(contactName = v) } }
        ContactField("Phone",     state.contactPhone) { v -> vm.update { it.copy(contactPhone = v) } }
        ContactField("Email",     state.contactEmail) { v -> vm.update { it.copy(contactEmail = v) } }
        ContactField("Organization", state.contactOrg) { v -> vm.update { it.copy(contactOrg = v) } }
    }
}

@Composable
private fun ContactField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = true,
        shape         = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun WifiFields(state: WriteUiState, vm: WriteViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value         = state.wifiSsid,
            onValueChange = { v -> vm.update { it.copy(wifiSsid = v) } },
            label         = { Text("Network name (SSID)") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(14.dp),
        )
        SecurityPicker(
            selected   = state.wifiSecurity,
            onSelected = { sec -> vm.update { it.copy(wifiSecurity = sec) } },
        )
        OutlinedTextField(
            value         = state.wifiPassword,
            onValueChange = { v -> vm.update { it.copy(wifiPassword = v) } },
            label         = { Text("Password") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            enabled       = state.wifiSecurity != TagPayload.WiFi.Security.OPEN,
            shape         = RoundedCornerShape(14.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val isLight = LocalAppColors.current.background.luminance() > 0.45f
            val supportingColor = if (isLight) {
                LocalAppColors.current.elevatedSurface.copy(alpha = 0.65f)
            } else {
                androidx.compose.ui.graphics.lerp(LocalAppColors.current.elevatedSurface, androidx.compose.ui.graphics.Color.White, 0.16f).copy(alpha = 0.88f)
            }
            AnimatedSwitch(
                checked = state.wifiHidden,
                onCheckedChange = { v -> vm.update { it.copy(wifiHidden = v) } },
                uncheckedTrackColor = supportingColor,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Hidden network", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Network SSID is not broadcast",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun SecurityPicker(
    selected: TagPayload.WiFi.Security,
    onSelected: (TagPayload.WiFi.Security) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Security type", style = MaterialTheme.typography.labelLarge, color = LocalAppColors.current.textSecondary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagPayload.WiFi.Security.entries.forEach { sec ->
                FilterChip(
                    selected = sec == selected,
                    onClick  = { onSelected(sec) },
                    label    = { Text(securityChipLabel(sec)) },
                    shape    = RoundedCornerShape(12.dp),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LocalAppColors.current.accentContainer,
                        selectedLabelColor     = LocalAppColors.current.onAccentContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BluetoothFields(state: WriteUiState, vm: WriteViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value         = state.bluetoothName,
            onValueChange = { v -> vm.update { it.copy(bluetoothName = v) } },
            label         = { Text("Device name") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(14.dp),
        )
        OutlinedTextField(
            value         = state.bluetoothMac,
            onValueChange = { v -> vm.update { it.copy(bluetoothMac = v) } },
            label         = { Text("MAC Address (e.g. 00:11:22:33:44:55)") },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(14.dp),
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SavedMessageCard(
    message: SavedMessageEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEmulate: () -> Unit,
    onDelete: () -> Unit,
    onWrite: () -> Unit,
) {
    val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }
    )
    val isLight = LocalAppColors.current.background.luminance() > 0.45f
    val supportingColor = if (isLight) {
        LocalAppColors.current.elevatedSurface
    } else {
        androidx.compose.ui.graphics.lerp(LocalAppColors.current.elevatedSurface, androidx.compose.ui.graphics.Color.White, 0.16f)
    }

    androidx.compose.material3.SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> supportingColor
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = LocalAppColors.current.error
                )
            }
        }
    ) {
        var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, LocalAppColors.current.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .clickable { onToggleExpand() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .background(LocalAppColors.current.surface, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val kind = try { WriteKind.valueOf(message.type) } catch (e: Exception) { WriteKind.TEXT }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LocalAppColors.current.accentContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = writeKindIcon(kind),
                            contentDescription = null,
                            tint = LocalAppColors.current.onAccentContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = writeKindLabel(kind) + " Message",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalAppColors.current.textPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        val summary = try {
                            val json = org.json.JSONObject(message.payload)
                            when (kind) {
                                WriteKind.TEXT -> json.optString("text", "")
                                WriteKind.URL -> json.optString("url", "")
                                WriteKind.CONTACT -> json.optString("name", "Contact")
                                WriteKind.WIFI -> "Network: ${json.optString("ssid", "")}"
                                WriteKind.BLUETOOTH -> "Device: ${json.optString("name", "")}"
                            }
                        } catch (e: Exception) { "" }
                        if (summary.isNotBlank()) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAppColors.current.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            val ExpansionSizeSpring = spring<androidx.compose.ui.unit.IntSize>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = 450f // Balanced speed for card expansion
            )
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(ExpansionSizeSpring),
                exit = shrinkVertically(ExpansionSizeSpring),
                modifier = Modifier.layout { measurable, constraints ->
                    val shift = 16.dp.roundToPx()
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, kotlin.math.max(0, placeable.height - shift)) {
                        placeable.placeRelative(0, -shift)
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LocalAppColors.current.background)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Always block taps from reaching parent Column while visible
                        )
                        .padding(top = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(enabled = isExpanded) { onEmulate() }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val blueColor = androidx.compose.ui.graphics.Color(0xFF2196F3)
                            Icon(Icons.Rounded.Sensors, null, Modifier.size(18.dp), tint = blueColor)
                            Spacer(Modifier.width(8.dp))
                            Text("Emulate Tag", color = blueColor, style = MaterialTheme.typography.labelLarge)
                        }
                        androidx.compose.material3.VerticalDivider(
                            modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp),
                            color = LocalAppColors.current.outlineVariant.copy(alpha = 0.55f)
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(enabled = isExpanded) { onWrite() }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val successColor = LocalAppColors.current.success
                            Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp), tint = successColor)
                            Spacer(Modifier.width(8.dp))
                            Text("Write to Tag", color = successColor, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = d

private fun writeKindLabel(kind: WriteKind): String = when (kind) {
    WriteKind.TEXT      -> "Text"
    WriteKind.URL       -> "URL"
    WriteKind.CONTACT   -> "Contact"
    WriteKind.WIFI      -> "Wi-Fi"
    WriteKind.BLUETOOTH -> "Bluetooth"
}

private fun writeKindIcon(kind: WriteKind): ImageVector = when (kind) {
    WriteKind.TEXT      -> Icons.Rounded.Description
    WriteKind.URL       -> Icons.Rounded.Link
    WriteKind.CONTACT   -> Icons.Rounded.ContactMail
    WriteKind.WIFI      -> Icons.Rounded.Wifi
    WriteKind.BLUETOOTH -> Icons.Rounded.Bluetooth
}

private fun securityChipLabel(sec: TagPayload.WiFi.Security): String = when (sec) {
    TagPayload.WiFi.Security.OPEN         -> "Open"
    TagPayload.WiFi.Security.WEP          -> "WEP"
    TagPayload.WiFi.Security.WPA_WPA2_PSK -> "WPA2"
    TagPayload.WiFi.Security.WPA3_SAE     -> "WPA3"
    TagPayload.WiFi.Security.UNKNOWN      -> "Other"
}

private fun writeFailureReasonLabel(reason: WriteResult.Reason): String = when (reason) {
    WriteResult.Reason.TAG_LOST               -> "Tag moved away — try again."
    WriteResult.Reason.READ_ONLY              -> "Tag is read-only."
    WriteResult.Reason.INSUFFICIENT_CAPACITY -> "Tag is too small for this payload."
    WriteResult.Reason.UNSUPPORTED_TAG        -> "This tag type is not supported."
    WriteResult.Reason.MALFORMED_PAYLOAD      -> "Payload is malformed."
    WriteResult.Reason.IO_ERROR               -> "I/O error — please try again."
    WriteResult.Reason.UNKNOWN                -> "Write failed."
}
