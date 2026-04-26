package com.nfcmanager.app.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.presentation.theme.AppColors
import com.nfcmanager.app.presentation.theme.LocalAppColors

/**
 * Branded toggle: thumb carries **✔** when ON and **✖** when OFF, with a short
 * scale + cross-fade morph (not a hard icon swap).
 *
 * When **off**: light UI uses dark thumb + white ✕; dark UI uses light thumb + dark ✕
 * (same palette as [AppColors.Dark] / [AppColors.Light]).
 */
@Composable
fun AnimatedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** When non-null, used for the OFF track (e.g. to match a description callout surface). */
    uncheckedTrackColor: Color? = null,
) {
    val C = LocalAppColors.current
    val trackOff = uncheckedTrackColor ?: C.elevatedSurface
    val isLightTheme = C.background.luminance() > 0.45f
    val offThumbBg = if (isLightTheme) AppColors.Dark.background else AppColors.Light.background
    val offIconTint = if (isLightTheme) Color.White else AppColors.Light.accent

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = C.onAccent,
            checkedTrackColor = C.accent,
            checkedBorderColor = C.accent,
            uncheckedThumbColor = offThumbBg,
            uncheckedTrackColor = trackOff,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = C.onAccent.copy(alpha = 0.5f),
            disabledCheckedTrackColor = C.accent.copy(alpha = 0.35f),
            disabledUncheckedThumbColor = offThumbBg.copy(alpha = 0.5f),
            disabledUncheckedTrackColor = trackOff.copy(alpha = 0.5f),
        ),
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(
                            initialScale = 0.65f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )) togetherWith (
                        fadeOut(spring(stiffness = Spring.StiffnessHigh)) +
                            scaleOut(targetScale = 0.65f)
                        )
                },
                label = "switchThumbMorph",
            ) { on ->
                Icon(
                    imageVector = if (on) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    tint = if (on) C.accent else offIconTint,
                )
            }
        },
    )
}
