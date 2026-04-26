package com.nfcmanager.app.presentation.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.R
import com.nfcmanager.app.data.prefs.ThemeMode
import com.nfcmanager.app.presentation.theme.LocalAppColors
import com.nfcmanager.app.presentation.theme.NfcManagerTheme

/**
 * Home header: brand mark + title.
 */
@Composable
fun HomeTopBar(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.app_name),
) {
    val C = LocalAppColors.current
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.headlineSmall
    val markSize = with(density) { titleStyle.fontSize.toDp() }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_app_mark),
                contentDescription = null,
                tint = C.accent,
                modifier = Modifier.size(markSize),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = titleStyle,
                color = C.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeTopBarPreview() {
    NfcManagerTheme(themeMode = ThemeMode.DARK) {
        HomeTopBar()
    }
}
