package com.nfcmanager.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nfcmanager.app.data.nfc.NfcReaderManager
import com.nfcmanager.app.data.prefs.ThemeMode
import com.nfcmanager.app.presentation.home.components.HomeNfcHero
import com.nfcmanager.app.presentation.home.components.HomeTopBar
import com.nfcmanager.app.presentation.theme.NfcManagerTheme

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreenContent(
        readerState = state.scanState,
        hardware = state.hardware,
        modifier = modifier,
    )
}

@Composable
fun HomeScreenContent(
    readerState: NfcReaderManager.State,
    hardware: NfcReaderManager.HardwareStatus,
    modifier: Modifier = Modifier,
) {
    val C = com.nfcmanager.app.presentation.theme.LocalAppColors.current
    Box(modifier = modifier.fillMaxSize().background(C.background)) {
        Column(Modifier.fillMaxSize()) {
            HomeTopBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            )
            HomeNfcHero(
                readerState = readerState,
                hardware = hardware,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(name = "Home — dark", showBackground = true)
@Composable
private fun PreviewHomeDark() {
    NfcManagerTheme(themeMode = ThemeMode.DARK) {
        HomeScreenContent(
            readerState = NfcReaderManager.State.Scanning,
            hardware = NfcReaderManager.HardwareStatus.Enabled,
        )
    }
}
