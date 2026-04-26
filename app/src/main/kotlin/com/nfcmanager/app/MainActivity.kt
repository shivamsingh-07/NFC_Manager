package com.nfcmanager.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.nfcmanager.app.data.prefs.ThemeMode
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nfcmanager.app.data.nfc.NfcReaderManager
import com.nfcmanager.app.data.nfc.TagDebouncer
import com.nfcmanager.app.data.prefs.UserPreferences
import com.nfcmanager.app.data.prefs.UserPreferencesRepository
import com.nfcmanager.app.nfc.AppProcessForeground
import com.nfcmanager.app.nfc.NfcMainReaderTabGate
import com.nfcmanager.app.nfc.NfcTagEventHandler
import com.nfcmanager.app.presentation.components.GlassNavBar
import com.nfcmanager.app.presentation.navigation.NfcNavHost
import com.nfcmanager.app.presentation.navigation.TopDestination
import com.nfcmanager.app.presentation.navigation.ROUTE_CREATE_ACTION
import com.nfcmanager.app.presentation.nfc.NfcConfirmController
import com.nfcmanager.app.presentation.nfc.NfcConfirmController.Pending
import com.nfcmanager.app.presentation.nfc.NfcTagConfirmBottomSheetHost
import com.nfcmanager.app.presentation.scan.ScanBottomSheetHost
import com.nfcmanager.app.presentation.scan.ScanSheetController
import com.nfcmanager.app.presentation.theme.ImmersiveSystemBars
import com.nfcmanager.app.presentation.theme.LocalAppColors
import com.nfcmanager.app.presentation.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single-Activity host for the full app UI.
 *
 * ## NFC routing model
 *
 * The app deliberately uses **only two** NFC entry points:
 *
 *  - **Reader Mode** on the Home tab while this activity is resumed.
 *    Direct callback, no system chooser, lowest-latency read path.
 *  - **Manifest dispatch** to [NfcDispatchActivity] for everything else
 *    (other tabs, backgrounded, killed). The OS may show its "Open with…"
 *    chooser when our app is one of several candidates — that's accepted
 *    behaviour, not something to engineer around.
 *
 * Foreground Dispatch is intentionally **not** used. The earlier attempt
 * to silence the chooser via `NfcAdapter.enableForegroundDispatch` added
 * intent-routing complexity, multi-discovery races during an active hold,
 * and a parallel parseTag entry point that overwrote the confirm sheet
 * with "Empty Tag" payloads. The chooser is a small UX cost; the bug
 * surface from suppressing it was much larger.
 *
 * ## Reader Mode gating (single source of truth: [syncMainReaderWithUiState])
 *
 * Reader Mode is enabled **only** when ALL of the following are true:
 *  1. Activity is resumed (`Lifecycle.State.RESUMED`)
 *  2. Current tab is [TopDestination.Home] ([NfcMainReaderTabGate.shouldEnableScanning])
 *  3. No NFC confirmation sheet is open
 *  4. No tag-capture enrollment is in progress
 *
 * When ANY condition fails, Reader Mode is disabled **immediately** and the
 * OS takes over routing via the manifest.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var readerManager: NfcReaderManager
    @Inject lateinit var userPrefs: UserPreferencesRepository
    @Inject lateinit var tagDebouncer: TagDebouncer
    @Inject lateinit var tagEventHandler: NfcTagEventHandler
    @Inject lateinit var confirmController: NfcConfirmController
    @Inject lateinit var scanSheetController: ScanSheetController
    @Inject lateinit var nfcMainReaderTabGate: NfcMainReaderTabGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        bindReaderToLifecycle()
        observeTagPipeline()

        setContent {
            val prefsFlow = remember {
                userPrefs.preferences.stateIn(
                    scope = lifecycleScope,
                    started = SharingStarted.Eagerly,
                    initialValue = UserPreferences(),
                )
            }
            val prefs by prefsFlow.collectAsStateWithLifecycle()
            NfcManagerRoot(
                themeMode = prefs.themeMode,
                host = this@MainActivity,
            )
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  SINGLE SOURCE OF TRUTH for Reader Mode enable/disable
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Evaluates the gating conditions and enables or disables Reader Mode.
     *
     * Called from:
     *  - Lifecycle callbacks (onResume/onPause)
     *  - Tab navigation changes
     *  - Confirmation sheet show/dismiss
     *  - Scan sheet mode changes
     *
     * **NEVER** enable Reader Mode from anywhere else.
     */
    fun syncMainReaderWithUiState() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (readerManager.isTagCaptureInProgress()) return

        val onHome = nfcMainReaderTabGate.shouldEnableScanning()
        val confirmOpen = confirmController.pending.value is Pending.Visible
        val wantReader = onHome && !confirmOpen

        // NfcReaderManager.enable is idempotent (early-returns if already
        // bound to this activity), so this method is safe to invoke on
        // every Compose recomposition without churning the NFC stack.
        Log.v(TAG, "syncMainReaderWithUiState onHome=$onHome confirmOpen=$confirmOpen wantReader=$wantReader")
        if (wantReader) readerManager.enable(this) else readerManager.disable(this)
    }

    // ────────────────────────────────────────────────────────────────────────

    private fun bindReaderToLifecycle() {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppProcessForeground.setMainActivityAtLeastStarted(true)
                Log.d(TAG, "lifecycle onStart")
            }

            override fun onResume(owner: LifecycleOwner) {
                Log.d(TAG, "lifecycle onResume")
                AppProcessForeground.setMainActivityVisible(true)
                handleDeepLink(intent)
                syncMainReaderWithUiState()
            }

            override fun onPause(owner: LifecycleOwner) {
                Log.d(TAG, "lifecycle onPause")
                AppProcessForeground.setMainActivityVisible(false)
                if (!readerManager.isTagCaptureInProgress()) {
                    readerManager.disable(this@MainActivity)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                AppProcessForeground.setMainActivityAtLeastStarted(false)
                Log.d(TAG, "lifecycle onStop")
            }
        })
    }

    private fun observeTagPipeline() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                coroutineScope {
                    launch {
                        userPrefs.preferences.collect { prefs ->
                            tagDebouncer.setWindow(prefs.debounceMillis)
                        }
                    }
                    // Forward Reader Mode tag discoveries to the event handler.
                    launch {
                        readerManager.tags.collect { tag ->
                            tagEventHandler.onTagDetected(tag)
                        }
                    }
                }
            }
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme == "nfcmanager" && data.host == "tag") {
            // Reserved for future deep-link routing.
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

// ─── Compose root ─────────────────────────────────────────────────────────────

@Composable
private fun NfcManagerRoot(
    themeMode: ThemeMode,
    host: MainActivity,
) {
    AppTheme(themeMode = themeMode) {
        val canvas = LocalAppColors.current.background
        ImmersiveSystemBars()

        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()

        // fromNavRoute returns null for sub-routes → Reader Mode will NOT
        // be enabled because isHomeTab() returns false when tab is null.
        val current = remember(backStack) {
            TopDestination.fromNavRoute(backStack?.destination?.route)
        }

        val confirmPending by host.confirmController.pending.collectAsStateWithLifecycle()
        val scanSheetState by host.scanSheetController.state.collectAsStateWithLifecycle()
        val scanMode by host.scanSheetController.scanMode.collectAsStateWithLifecycle()

        // Re-evaluate Reader Mode on EVERY nav/sheet state change.
        LaunchedEffect(current, confirmPending, scanSheetState.visible, scanMode) {
            host.nfcMainReaderTabGate.setCurrentTab(current)
            host.syncMainReaderWithUiState()
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(canvas),
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        ) { scaffoldPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(canvas)
                    .padding(scaffoldPadding),
            ) {
                // Full-bleed nav host — each screen manages its own top insets
                NfcNavHost(
                    navController = navController,
                    padding = PaddingValues(),
                    modifier = Modifier.fillMaxSize(),
                )
                // Consolidate the entire navigation area (scrim + pill) into a single synced animation.
                val route = backStack?.destination?.route
                val showNavBar = route != null && route != ROUTE_CREATE_ACTION && TopDestination.fromRoute(route) != null

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(10f)
                ) {
                    AnimatedVisibility(
                        visible = showNavBar,
                        // Entry: lightly under-damped spring at high enough
                        // stiffness that the convergence tail isn't visible —
                        // gives the bar a subtle "settle" overshoot when it
                        // arrives without ever feeling stuck. (LowBouncy =
                        // 0.75; StiffnessMedium = 1500 — the previous "stuck"
                        // version used stiffness 600, which is what made the
                        // last few dp drag.)
                        // Exit: a deterministic accelerated tween; bouncing
                        // on the way out reads as the bar swinging back
                        // before disappearing, which looks broken.
                        enter = slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        ) { fullHeight -> fullHeight },
                        exit = slideOutVertically(
                            animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing),
                        ) { fullHeight -> fullHeight },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp), // Height covers the bar + padding + soft gradient
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Soft gradient behind the bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                canvas.copy(alpha = 0.94f),
                                            ),
                                        ),
                                    ),
                            )
                            
                            // The glass pill nav
                            Box(
                                modifier = Modifier
                                    .navigationBarsPadding()
                                    .padding(bottom = 16.dp),
                            ) {
                                GlassNavBar(
                                    current = current ?: TopDestination.Home,
                                    onSelect = { dest ->
                                        if (dest.route != current?.route) {
                                            navController.navigate(dest.route) {
                                                popUpTo(TopDestination.Home.route) {
                                                    inclusive = false
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                ScanBottomSheetHost()
                NfcTagConfirmBottomSheetHost()
            }
        }
    }
}
