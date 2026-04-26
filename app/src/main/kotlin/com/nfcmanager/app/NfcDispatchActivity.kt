package com.nfcmanager.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nfcmanager.app.data.nfc.NfcReaderManager
import com.nfcmanager.app.data.nfc.NfcReaderManager.TagSource
import com.nfcmanager.app.data.prefs.UserPreferences
import com.nfcmanager.app.nfc.AppProcessForeground
import com.nfcmanager.app.data.prefs.UserPreferencesRepository
import com.nfcmanager.app.nfc.NfcScanConfirmationCoordinator
import com.nfcmanager.app.domain.model.NfcTag
import com.nfcmanager.app.presentation.components.ExpressiveButton
import com.nfcmanager.app.presentation.nfc.NfcConfirmController
import com.nfcmanager.app.presentation.nfc.NfcDispatchUiState
import com.nfcmanager.app.presentation.nfc.NfcDispatchViewModel
import com.nfcmanager.app.presentation.nfc.NfcTagDetectedSheetContent
import com.nfcmanager.app.presentation.theme.AppTheme
import com.nfcmanager.app.presentation.theme.ImmersiveSystemBars
import com.nfcmanager.app.presentation.theme.LocalAppColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Lightweight NFC entry point. Manifest NFC intent filters target this activity
 * so passive taps never pull the full app UI over other apps.
 *
 * This is the **only** dispatch path now that Foreground Dispatch has been
 * removed from [MainActivity]. When the app is in the foreground on the Home
 * tab, Reader Mode catches taps directly. In every other case (other tabs,
 * backgrounded, killed, or while the confirm sheet is already up), the OS
 * routes the tap here via the manifest intent filters — sometimes after
 * showing its own "Open with…" chooser when other NFC handlers also match.
 * That chooser is accepted UX, not a bug.
 */
@AndroidEntryPoint
class NfcDispatchActivity : ComponentActivity() {

    @Inject lateinit var readerManager: NfcReaderManager
    @Inject lateinit var userPrefs: UserPreferencesRepository
    @Inject lateinit var coordinator: NfcScanConfirmationCoordinator
    @Inject lateinit var confirmController: NfcConfirmController

    private val dispatchViewModel: NfcDispatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (readerManager.isTagCaptureInProgress()) {
            finish()
            return
        }

        // A confirm sheet is already up in MainActivity. The OS just routed a
        // re-discovery (or a fresh tap) to us — opening this activity on top
        // would obscure the in-flight sheet with a duplicate one. Bow out
        // silently and let the user finish with the existing sheet.
        if (confirmController.pending.value is NfcConfirmController.Pending.Visible) {
            finish()
            return
        }

        if (!AppProcessForeground.isMainActivityVisible()) {
            val allowBackground = runBlocking {
                userPrefs.preferences.first().backgroundScanningEnabled
            }
            if (!allowBackground) {
                finish()
                return
            }
        }

        // The actual NDEF read may need a tag.connect() — must happen off the
        // main thread (StrictMode disallows it on Android 14+). Show nothing
        // until the parse completes; if it fails / times out we just finish().
        val pendingIntent = intent
        neutralizeIntent()
        lifecycleScope.launch {
            val tag = readerManager.processDispatchedIntent(pendingIntent, TagSource.ManifestDispatch)
            if (tag == null) {
                finish()
                return@launch
            }
            dispatchViewModel.resolveFor(tag)
        }

        setContent {
            val prefsFlow = remember {
                userPrefs.preferences.stateIn(
                    scope = lifecycleScope,
                    started = SharingStarted.Eagerly,
                    initialValue = UserPreferences(),
                )
            }
            val currentPrefs by prefsFlow.collectAsStateWithLifecycle()
            val uiState by dispatchViewModel.ui.collectAsStateWithLifecycle()

            AppTheme(themeMode = currentPrefs.themeMode) {
                ImmersiveSystemBars()
                Box(Modifier.fillMaxSize()) {
                    when (val s = uiState) {
                        is NfcDispatchUiState.ActionExecuted -> LaunchedEffect(Unit) { finish() }
                        is NfcDispatchUiState.Dropped -> LaunchedEffect(Unit) { finish() }
                        is NfcDispatchUiState.ShowTagInfo -> NfcDispatchSheet(
                            tag = s.tag,
                            coordinator = coordinator,
                            activity = this@NfcDispatchActivity,
                        )
                        NfcDispatchUiState.Loading -> Unit
                    }
                }
            }
        }
    }

    private fun neutralizeIntent() {
        setIntent(
            Intent(this, NfcDispatchActivity::class.java).apply {
                action = Intent.ACTION_MAIN
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NfcDispatchSheet(
    tag: NfcTag,
    coordinator: NfcScanConfirmationCoordinator,
    activity: NfcDispatchActivity,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            coordinator.onConfirmationDismissed(activity, stopReader = true)
            activity.finish()
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = LocalAppColors.current.surface,
        // Use the live navigation bar inset so this dispatch sheet sits
        // correctly above gesture pill / 3-button nav on every device.
        contentWindowInsets = { WindowInsets.navigationBars },
        dragHandle = {
            Spacer(Modifier.height(22.dp))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val closeAction = {
                activity.lifecycleScope.launch {
                    coordinator.onPassiveReadAcknowledged(tag, activity, disableReader = true)
                    activity.finish()
                }
                Unit
            }

            NfcTagDetectedSheetContent(
                tag = tag,
                onClose = { closeAction() },
                onDismiss = {
                    coordinator.onConfirmationDismissed(activity, stopReader = true)
                    activity.finish()
                },
            )

            Spacer(Modifier.height(20.dp))

            ExpressiveButton(
                text = "Close",
                onClick = { closeAction() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
