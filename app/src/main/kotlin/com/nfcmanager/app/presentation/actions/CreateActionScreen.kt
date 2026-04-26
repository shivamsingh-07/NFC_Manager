package com.nfcmanager.app.presentation.actions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nfcmanager.app.domain.model.ActionType
import com.nfcmanager.app.domain.model.TagPayload
import com.nfcmanager.app.domain.util.BluetoothDeviceProvider
import com.nfcmanager.app.domain.util.InstalledAppsProvider
import com.nfcmanager.app.presentation.components.ExpressiveButton
import com.nfcmanager.app.presentation.components.TabScreenHeader
import com.nfcmanager.app.presentation.theme.LocalAppColors
import com.nfcmanager.app.util.findActivity
import androidx.compose.foundation.BorderStroke
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateActionScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateActionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.finished) {
        if (state.finished) {
            keyboard?.hide()
            onDone()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                })
            }
            .padding(horizontal = 16.dp)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(16.dp))
        TabScreenHeader(
            title = "Create Action",
            subtitle = "Choose what happens when tag is scanned",
            icon = Icons.Rounded.Bolt,
        )
        Spacer(Modifier.height(16.dp))

        // Only intercept back while the user is mid-wizard (Configure /
        // Confirm). On the first step (ChooseType) the back gesture must
        // bubble up so Navigation Compose can render its predictive-back
        // transition to ActionsScreen — registering an enabled BackHandler
        // here would consume the gesture before NavHost's
        // popExitTransition can drive it along the swipe.
        BackHandler(enabled = state.step != CreateActionViewModel.Step.ChooseType) {
            viewModel.back()
        }

        AnimatedContent(
            targetState = state.step,
            // Material 3 "Shared Axis Z" — forward steps zoom in from
            // behind (start at 92%, fade up to full), back steps zoom out
            // from in front (start at 108%, shrink into place). The
            // outgoing content does the opposite, so the two layers
            // appear to slide along the depth axis instead of horizontally.
            // This deliberately picks a different motion axis from the
            // tab-level NavHost (which uses horizontal slide) so the user
            // doesn't conflate "next wizard step" with "next tab".
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val incomingScaleStart = if (forward) 0.92f else 1.08f
                val outgoingScaleEnd = if (forward) 1.08f else 0.92f

                val enter = scaleIn(
                    initialScale = incomingScaleStart,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 240, delayMillis = 80),
                )

                val exit = scaleOut(
                    targetScale = outgoingScaleEnd,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 160),
                )

                enter togetherWith exit using SizeTransform(
                    clip = false,
                    sizeAnimationSpec = { _, _ ->
                        tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    },
                )
            },
            label = "StepTransition"
        ) { step ->
            when (step) {
                CreateActionViewModel.Step.ChooseType -> ActionPicker(onSelect = viewModel::selectType)
                CreateActionViewModel.Step.Configure -> ConfigStep(
                    state = state,
                    viewModel = viewModel,
                    onNext = { context.findActivity()?.let { viewModel.beginScan(it) } }
                )
                CreateActionViewModel.Step.Confirm -> FinalizeStep(
                    state = state,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun ActionPicker(onSelect: (ActionType) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        items(ActionType.entries) { type ->
            ActionCard(
                type = type,
                onClick = { onSelect(type) }
            )
        }
    }
}

@Composable
private fun ActionCard(type: ActionType, onClick: () -> Unit) {
    val (icon, title, desc) = remember(type) {
        when (type) {
            ActionType.OPEN_URL -> Triple(Icons.Rounded.Language, "Open Website", "Launch any URL in browser")
            ActionType.OPEN_APP -> Triple(Icons.Rounded.Apps, "Open App", "Launch any installed app")
            ActionType.CONNECT_WIFI -> Triple(Icons.Rounded.Wifi, "Connect Wi-Fi", "Automate Wi-Fi connection")
            ActionType.CONNECT_BLUETOOTH -> Triple(Icons.Rounded.Bluetooth, "Bluetooth", "Connect to paired device")
            ActionType.TOGGLE_FLASHLIGHT -> Triple(Icons.Rounded.FlashlightOn, "Flashlight", "Toggle system flashlight")
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = LocalAppColors.current.elevatedSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp) // Fixed height for equal dimensions
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(LocalAppColors.current.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = LocalAppColors.current.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LocalAppColors.current.textPrimary
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.labelSmall.lineHeight * 1.1f
            )
        }
    }
}

@Composable
private fun ConfigStep(
    state: CreateActionViewModel.UiState,
    viewModel: CreateActionViewModel,
    onNext: () -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (state.selectedType) {
                ActionType.OPEN_URL -> WebsiteConfig(state, viewModel::updateUrl)
                ActionType.OPEN_APP -> AppPicker(state.apps, state.selectedApp, viewModel::selectApp)
                ActionType.CONNECT_WIFI -> WifiConfig(state, viewModel)
                ActionType.CONNECT_BLUETOOTH -> BluetoothPicker(state.pairedDevices, state.selectedBluetoothDevice, viewModel::selectBluetoothDevice)
                ActionType.TOGGLE_FLASHLIGHT -> FlashlightInfo()
                null -> Unit
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        ExpressiveButton(
            text = "Scan Tag",
            onClick = {
                focusManager.clearFocus()
                keyboard?.hide()
                onNext()
            },
            enabled = state.canAdvanceFromConfigure,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun WebsiteConfig(state: CreateActionViewModel.UiState, onUrlChange: (String) -> Unit) {
    val C = LocalAppColors.current
    Column {
        Text("Enter Website URL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = C.textPrimary)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.urlInput,
            onValueChange = onUrlChange,
            placeholder = { Text("https://example.com") },
            isError = state.urlError != null,
            supportingText = { state.urlError?.let { Text(it) } },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
    }
}

@Composable
private fun AppPicker(
    apps: List<InstalledAppsProvider.AppEntry>,
    selected: InstalledAppsProvider.AppEntry?,
    onSelect: (InstalledAppsProvider.AppEntry) -> Unit,
) {
    val C = LocalAppColors.current
    Column {
        Text(
            "Select App",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = C.textPrimary,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            color = C.elevatedSurface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LazyColumn {
                itemsIndexed(apps) { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = C.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    AppItem(entry, isSelected = entry == selected) { onSelect(entry) }
                }
            }
        }
    }
}

@Composable
private fun WifiConfig(state: CreateActionViewModel.UiState, viewModel: CreateActionViewModel) {
    val C = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wi-Fi Connection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = C.textPrimary)
        
        OutlinedTextField(
            value = state.wifiSsid,
            onValueChange = viewModel::updateWifiSsid,
            label = { Text("SSID") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        
        OutlinedTextField(
            value = state.wifiPassword,
            onValueChange = viewModel::updateWifiPassword,
            label = { Text("Password") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        
        var expanded by remember { mutableStateOf(false) }
        
        Box {
            OutlinedTextField(
                value = state.wifiSecurity.name.replace("_", "/"),
                onValueChange = {},
                readOnly = true,
                label = { Text("Security Type") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Rounded.ArrowDropDown, null)
                    }
                }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.8f).background(C.elevatedSurface)
            ) {
                TagPayload.WiFi.Security.entries.filter { it != TagPayload.WiFi.Security.UNKNOWN }.forEach { sec ->
                    DropdownMenuItem(
                        text = { Text(sec.name.replace("_", "/"), color = C.textPrimary) },
                        onClick = {
                            viewModel.updateWifiSecurity(sec)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothPicker(
    devices: List<BluetoothDeviceProvider.DeviceEntry>,
    selected: BluetoothDeviceProvider.DeviceEntry?,
    onSelect: (BluetoothDeviceProvider.DeviceEntry) -> Unit
) {
    val C = LocalAppColors.current
    Column {
        Text("Select Bluetooth Device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = C.textPrimary)
        Spacer(Modifier.height(16.dp))
        if (devices.isEmpty()) {
            Text("No paired devices found.", color = LocalAppColors.current.textSecondary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    DeviceItem(device, device == selected) { onSelect(device) }
                }
            }
        }
    }
}

@Composable
private fun FlashlightInfo() {
    val C = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(bottom = 60.dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(C.accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.FlashlightOn, null, Modifier.size(48.dp), tint = C.accent)
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Flashlight Toggle",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = C.textPrimary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "The rear flashlight will toggle instantly when scanned.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = C.textSecondary,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun AppItem(app: InstalledAppsProvider.AppEntry, isSelected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName)
        } catch (e: Exception) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) LocalAppColors.current.accent.copy(alpha = 0.1f) else Color.Transparent)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(Icons.Rounded.Android, null, Modifier.size(24.dp), tint = LocalAppColors.current.textSecondary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.textPrimary)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textSecondary)
        }
        if (isSelected) {
            Icon(Icons.Rounded.CheckCircle, null, tint = LocalAppColors.current.accent)
        }
    }
}


@Composable
private fun DeviceItem(device: BluetoothDeviceProvider.DeviceEntry, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) LocalAppColors.current.accentContainer else LocalAppColors.current.elevatedSurface,
        border = if (isSelected) BorderStroke(1.dp, LocalAppColors.current.accent) else null
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bluetooth, null, Modifier.size(24.dp), tint = if (isSelected) LocalAppColors.current.accent else LocalAppColors.current.textSecondary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(device.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.textPrimary)
                Text(device.address, style = MaterialTheme.typography.labelSmall, color = LocalAppColors.current.textSecondary)
            }
        }
    }
}

@Composable
private fun FinalizeStep(state: CreateActionViewModel.UiState, viewModel: CreateActionViewModel) {
    val C = LocalAppColors.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Finalize Mapping", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = C.textPrimary)
        
        Surface(color = LocalAppColors.current.elevatedSurface, shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TaskAlt, null, tint = LocalAppColors.current.accent)
                Spacer(Modifier.width(12.dp))
                Text("Tag linked successfully", style = MaterialTheme.typography.bodyLarge, color = LocalAppColors.current.textPrimary)
            }
        }

        OutlinedTextField(
            value = state.label,
            onValueChange = viewModel::updateLabel,
            label = { Text("Give it a name") },
            placeholder = { Text("e.g. Bedside Trigger") },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.save() })
        )
        
        if (state.duplicateWarning) {
            Text("Note: This tag already has an action. Saving will overwrite it.", color = LocalAppColors.current.accent, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.weight(1f))
        
        ExpressiveButton(
            text = "Save Action",
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
    }
}
