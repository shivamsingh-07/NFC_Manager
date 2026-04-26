package com.nfcmanager.app.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.presentation.theme.ButtonShape
import com.nfcmanager.app.presentation.theme.LocalAppColors

enum class ExpressiveButtonStyle {
    Primary,
    Tonal,
    Outlined,
}

/**
 * Primary / tonal / outlined CTA with **scale-down → spring-back** press feedback
 * and soft elevation on filled variants.
 */
@Composable
fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ExpressiveButtonStyle = ExpressiveButtonStyle.Primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val C = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "expressiveBtnScale",
    )

    val shape = ButtonShape
    val mod = modifier
        .scale(scale)
        .then(
            when (style) {
                ExpressiveButtonStyle.Primary -> Modifier.shadow(
                    elevation = if (pressed) 2.dp else 6.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.18f),
                )
                else -> Modifier
            },
        )
        .heightIn(min = 48.dp)

    when (style) {
        ExpressiveButtonStyle.Primary -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = mod,
            shape = shape,
            interactionSource = interaction,
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = C.accent,
                contentColor = C.onAccent,
                disabledContainerColor = C.accent.copy(alpha = 0.38f),
                disabledContentColor = C.onAccent.copy(alpha = 0.38f),
            ),
            contentPadding = contentPadding,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.invoke()
                if (leadingIcon != null) Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }

        ExpressiveButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = mod,
            shape = shape,
            interactionSource = interaction,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = C.accentContainer,
                contentColor = C.onAccentContainer,
            ),
            contentPadding = contentPadding,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.invoke()
                if (leadingIcon != null) Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }

        ExpressiveButtonStyle.Outlined -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = mod,
            shape = shape,
            interactionSource = interaction,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = C.accent,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = C.accent.copy(alpha = if (enabled) 1f else 0.38f),
            ),
            contentPadding = contentPadding,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.invoke()
                if (leadingIcon != null) Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
