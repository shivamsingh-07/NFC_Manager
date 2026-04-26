package com.nfcmanager.app.presentation.nfc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfcmanager.app.domain.action.ActionExecutor
import com.nfcmanager.app.domain.model.NfcAction
import com.nfcmanager.app.domain.model.NfcTag
import com.nfcmanager.app.domain.repository.NfcActionRepository
import com.nfcmanager.app.domain.util.UidHasher
import com.nfcmanager.app.data.prefs.UserPreferencesRepository
import com.nfcmanager.app.nfc.AppProcessForeground
import com.nfcmanager.app.nfc.NfcMainReaderTabGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI state for [com.nfcmanager.app.NfcDispatchActivity].
 *
 * | State             | Meaning                                             |
 * |-------------------|-----------------------------------------------------|
 * | [Loading]         | Lookup in progress                                  |
 * | [ActionExecuted]  | Mapped action was run silently → finish immediately |
 * | [ShowTagInfo]     | No action → show tag sheet (foreground Home or background scan on) |
 * | [Dropped]         | Tag silently ignored (background off, or non-Home while main visible) |
 */
sealed interface NfcDispatchUiState {
    data object Loading : NfcDispatchUiState
    data object ActionExecuted : NfcDispatchUiState
    data class ShowTagInfo(val tag: NfcTag) : NfcDispatchUiState
    data object Dropped : NfcDispatchUiState
}

@HiltViewModel
class NfcDispatchViewModel @Inject constructor(
    private val actionRepository: NfcActionRepository,
    private val uidHasher: UidHasher,
    private val executor: ActionExecutor,
    private val prefs: UserPreferencesRepository,
    private val mainReaderTabGate: NfcMainReaderTabGate,
) : ViewModel() {

    private val _ui = MutableStateFlow<NfcDispatchUiState>(NfcDispatchUiState.Loading)
    val ui: StateFlow<NfcDispatchUiState> = _ui.asStateFlow()

    /**
     * ## Dispatch gating logic
     *
     * The system delivers NFC intents to [com.nfcmanager.app.NfcDispatchActivity]
     * regardless of whether our app is open. Two cases require special handling:
     *
     * ### MainActivity shell alive but user is not on Home
     * Strict Home-only: drop everything (no actions, no popup). Uses
     * [AppProcessForeground.isMainActivityAtLeastStarted] so this still applies
     * when Main was paused before this coroutine ran (e.g. dispatch activity
     * stole focus).
     *
     * ### MainActivity NOT visible (another app, launcher, or cold start dispatch)
     * - If `backgroundScanningEnabled == false` → drop everything silently.
     * - If `backgroundScanningEnabled == true` → allow mapped actions; show the
     *   tag info sheet for tags with no mapped action (user opted in).
     *
     * ### MainActivity visible on Home
     * Execute mapped action OR show popup for informational tags.
     */
    fun resolveFor(tag: NfcTag) {
        viewModelScope.launch {
            val settings = prefs.preferences.first()
            val appWasVisible = AppProcessForeground.isMainActivityVisible()
            val mainShellAlive = AppProcessForeground.isMainActivityAtLeastStarted()

            if (mainShellAlive && !mainReaderTabGate.isHomeTab()) {
                _ui.value = NfcDispatchUiState.Dropped
                return@launch
            }

            if (!appWasVisible && !settings.backgroundScanningEnabled) {
                _ui.value = NfcDispatchUiState.Dropped
                return@launch
            }

            val action: NfcAction? = runCatching {
                val hash = uidHasher.hashHex(tag.uid)
                actionRepository.findByUidHash(hash)
            }.getOrNull()

            when {
                action != null -> {
                    executor.execute(action)
                    _ui.value = NfcDispatchUiState.ActionExecuted
                }
                else -> {
                    _ui.value = NfcDispatchUiState.ShowTagInfo(tag)
                }
            }
        }
    }
}
