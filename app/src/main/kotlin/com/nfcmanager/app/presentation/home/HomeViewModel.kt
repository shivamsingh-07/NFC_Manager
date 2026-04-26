package com.nfcmanager.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfcmanager.app.data.nfc.NfcReaderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val readerManager: NfcReaderManager,
) : ViewModel() {

    val state: StateFlow<HomeUiState> =
        readerManager.state
            .map { scanState ->
                HomeUiState(
                    scanState = scanState,
                    hardware = readerManager.hardwareStatus,
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HomeUiState(
                    scanState = NfcReaderManager.State.Idle,
                    hardware = readerManager.hardwareStatus,
                ),
            )
}

data class HomeUiState(
    val scanState: NfcReaderManager.State,
    val hardware: NfcReaderManager.HardwareStatus,
)
