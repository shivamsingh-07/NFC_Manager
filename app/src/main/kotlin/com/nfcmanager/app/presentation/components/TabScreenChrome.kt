package com.nfcmanager.app.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.presentation.theme.LocalAppColors

/**
 * Top-left tab title row on the layered background canvas.
 */
@Composable
fun TabScreenHeader(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val C = LocalAppColors.current
    val hasSubtitle = subtitle != null
    // Match combined visual height of headline + subtitle (type scale–aware).
    val iconBoxSize = if (hasSubtitle) 48.dp else 40.dp
    val iconGraphicSize = if (hasSubtitle) 26.dp else 22.dp
    val iconCorner = if (hasSubtitle) 16.dp else 12.dp
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon?.invoke(this)
        if (navigationIcon != null) {
            Spacer(Modifier.width(8.dp))
        }
        if (icon != null) {
            Surface(
                modifier = Modifier.size(iconBoxSize),
                shape = RoundedCornerShape(iconCorner),
                color = C.accentContainer,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = C.onAccentContainer,
                        modifier = Modifier.size(iconGraphicSize),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = C.textPrimary,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = C.textSecondary,
                )
            }
        }
        Row(content = actions)
    }
}

/**
 * Grouped tab body: expressive surface, border, and soft lift.
 */
@Composable
fun TabContentSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    ExpressiveSection(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = C.surface,
        content = content,
    )
}
