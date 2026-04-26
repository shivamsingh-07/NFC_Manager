package com.nfcmanager.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.data.prefs.ThemeMode
import com.nfcmanager.app.presentation.theme.LocalAppColors
import com.nfcmanager.app.presentation.theme.NfcManagerTheme

private val SectionShape: Shape = RoundedCornerShape(24.dp)

/**
 * Grouped expressive surface using [LocalAppColors] (not Material dynamic colour).
 */
@Composable
fun ExpressiveSection(
    modifier: Modifier = Modifier,
    shape: Shape = SectionShape,
    containerColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val C = LocalAppColors.current
    val fill = containerColor ?: C.elevatedSurface
    val border = BorderStroke(1.dp, C.outlineVariant.copy(alpha = 0.55f))
    val spot = Color.Black.copy(alpha = 0.06f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = spot,
                spotColor = spot,
                clip = false,
            ),
        shape = shape,
        color = fill,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExpressiveSectionPreview() {
    NfcManagerTheme(themeMode = ThemeMode.DARK) {
        val C = LocalAppColors.current
        ExpressiveSection {
            Text(
                text = "Section title",
                style = MaterialTheme.typography.titleLarge,
                color = C.textPrimary,
            )
            Text(
                text = "Supporting copy uses hierarchy and spacing.",
                style = MaterialTheme.typography.bodyMedium,
                color = C.textSecondary,
            )
        }
    }
}
