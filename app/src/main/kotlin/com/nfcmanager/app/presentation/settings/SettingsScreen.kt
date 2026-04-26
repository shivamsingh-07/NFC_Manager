package com.nfcmanager.app.presentation.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nfcmanager.app.R
import com.nfcmanager.app.data.prefs.ThemeMode
import com.nfcmanager.app.presentation.components.AnimatedSwitch
import com.nfcmanager.app.presentation.components.ExpressiveButton
import com.nfcmanager.app.presentation.components.ExpressiveSection
import com.nfcmanager.app.presentation.components.TabScreenHeader
import com.nfcmanager.app.presentation.theme.LocalAppColors

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state = viewModel.preferences.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val C = LocalAppColors.current
    val isLightTheme = C.background.luminance() > 0.45f

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.statusBarsPadding()) {
            Spacer(Modifier.height(16.dp))

            TabScreenHeader(
                title = "Settings",
                icon = Icons.Rounded.Settings,
                subtitle = "Preferences and NFC behaviour",
            )

            Spacer(Modifier.height(16.dp))
        }

        ExpressiveSection(
            shape = RoundedCornerShape(24.dp),
            containerColor = C.surface,
            contentPadding = PaddingValues(16.dp),
        ) {
            SettingsSubsectionHeader(title = "Detection", icon = Icons.Rounded.Radar)
            Spacer(Modifier.height(8.dp))
            SettingsCallout(
                text = "When off, NFC taps while the app is closed are ignored.",
            )
            Spacer(Modifier.height(12.dp))
            SettingsSwitchRow(
                icon = null,
                title = "Background scanning",
                subtitle = null,
                checked = state.backgroundScanningEnabled,
                onChange = viewModel::toggleBackgroundScanning,
            )

            Spacer(Modifier.height(16.dp))
            SettingsSectionDivider(isLightTheme = isLightTheme)
            Spacer(Modifier.height(16.dp))

            SettingsSubsectionHeader(title = "Debounce Window", icon = Icons.Rounded.Speed)
            Spacer(Modifier.height(8.dp))
            SettingsCallout(
                text = "Minimum time after reading a tag before the same tag can be read again.",
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${state.debounceMillis} ms",
                    style = MaterialTheme.typography.titleMedium,
                    color = C.accent,
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = state.debounceMillis.toFloat(),
                valueRange = 500f..5000f,
                steps = 17,
                onValueChange = { viewModel.setDebounce(it.toLong()) },
                colors = SliderDefaults.colors(
                    thumbColor = C.accent,
                    activeTrackColor = C.accent,
                    inactiveTrackColor = C.outlineVariant,
                ),
            )

            Spacer(Modifier.height(16.dp))
            SettingsSectionDivider(isLightTheme = isLightTheme)
            Spacer(Modifier.height(16.dp))

            SettingsSubsectionHeader(title = "Appearance", icon = Icons.Rounded.Palette)
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val selected = state.themeMode == mode
                    val chipBg: Color
                    val chipIcon: Color
                    val chipLabel: Color
                    val chipBorder: BorderStroke?
                    if (selected) {
                        chipBg = C.accent
                        chipIcon = C.onAccent
                        chipLabel = C.onAccent.copy(alpha = 0.92f)
                        chipBorder = null
                    } else {
                        chipBg = settingsSupportingInsetColor()
                        chipIcon = C.textPrimary
                        chipLabel = C.textSecondary
                        chipBorder = BorderStroke(1.dp, C.outline.copy(alpha = 0.4f))
                    }
                    Surface(
                        onClick = { viewModel.setThemeMode(mode) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = chipBg,
                        border = chipBorder,
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                                    ThemeMode.SYSTEM -> Icons.Rounded.AutoMode
                                },
                                contentDescription = null,
                                tint = chipIcon,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = mode.displayLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = chipLabel,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SettingsSectionDivider(isLightTheme = isLightTheme)
            Spacer(Modifier.height(16.dp))

            SettingsSubsectionHeader(
                title = "NFC settings",
                painter = painterResource(R.drawable.ic_app_mark),
            )
            Spacer(Modifier.height(12.dp))
            ExpressiveButton(
                text = "Open NFC system settings",
                onClick = {
                    val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun SettingsSubsectionHeader(
    title: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
) {
    val C = LocalAppColors.current
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.titleMedium
    val insetGraphicSize = with(density) { titleStyle.fontSize.toDp() }
    check(icon != null || painter != null) { "SettingsSubsectionHeader requires icon or painter" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(settingsSupportingInsetColor()),
            contentAlignment = Alignment.Center,
        ) {
            if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = C.accent,
                    modifier = Modifier.size(insetGraphicSize),
                )
            } else {
                Icon(
                    imageVector = icon!!,
                    contentDescription = null,
                    tint = C.textPrimary,
                    modifier = Modifier.size(insetGraphicSize),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = titleStyle, color = C.textPrimary)
    }
}

@Composable
private fun SettingsSectionDivider(isLightTheme: Boolean) {
    val C = LocalAppColors.current
    val line = if (isLightTheme) {
        C.textPrimary.copy(alpha = 0.16f)
    } else {
        C.onAccentContainer.copy(alpha = 0.14f)
    }
    HorizontalDivider(thickness = 1.dp, color = line)
}

@Composable
private fun settingsSupportingInsetColor(): Color {
    val C = LocalAppColors.current
    val isLight = C.background.luminance() > 0.45f
    return if (isLight) {
        C.elevatedSurface.copy(alpha = 0.65f)
    } else {
        lerp(C.elevatedSurface, Color.White, 0.16f).copy(alpha = 0.88f)
    }
}

@Composable
private fun SettingsCallout(text: String) {
    val C = LocalAppColors.current
    val isLight = C.background.luminance() > 0.45f
    val calloutBg = settingsSupportingInsetColor()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = calloutBg,
        border = BorderStroke(1.dp, C.outlineVariant.copy(alpha = if (isLight) 0.75f else 0.5f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = C.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val C = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(settingsSupportingInsetColor()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = C.textPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = C.textPrimary)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = C.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        AnimatedSwitch(
            checked = checked,
            onCheckedChange = onChange,
            uncheckedTrackColor = settingsSupportingInsetColor(),
        )
    }
}

private fun ThemeMode.displayLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
