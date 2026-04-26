package com.nfcmanager.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.presentation.theme.LocalAppColors

private val DefaultCardShape = RoundedCornerShape(24.dp)

/**
 * Layered surface card: soft rim, subtle lift, tuned to [LocalAppColors].
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = DefaultCardShape,
    containerColor: Color? = null,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val bg = containerColor ?: C.surface
    val border = BorderStroke(1.dp, C.outlineVariant.copy(alpha = 0.55f))
    val spot = Color.Black.copy(alpha = 0.08f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = spot,
                spotColor = spot,
                clip = false,
            ),
        shape = shape,
        color = bg,
        tonalElevation = tonalElevation,
        shadowElevation = 0.dp,
        border = border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            content = content,
        )
    }
}
