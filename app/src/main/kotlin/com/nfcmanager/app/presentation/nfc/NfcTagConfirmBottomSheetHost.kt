package com.nfcmanager.app.presentation.nfc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nfcmanager.app.presentation.components.ExpressiveButton
import com.nfcmanager.app.presentation.nfc.NfcConfirmController.Pending
import com.nfcmanager.app.presentation.theme.LocalAppColors
import com.nfcmanager.app.util.findActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcTagConfirmBottomSheetHost(
    viewModel: NfcConfirmViewModel = hiltViewModel(),
) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val visible = when (val p = pending) {
        Pending.Hidden -> return
        is Pending.Visible -> p
    }

    val activity = LocalContext.current.findActivity() ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { viewModel.onDismiss(activity) },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = LocalAppColors.current.surface,
        // Drive bottom padding from the live navigation bar inset; the
        // sheet's container still paints edge-to-edge.
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
            NfcTagDetectedSheetContent(
                tag = visible.tag,
                onClose = { viewModel.onClose(activity) },
                onDismiss = { viewModel.onDismiss(activity) },
            )
            
            Spacer(Modifier.height(20.dp))

            ExpressiveButton(
                text = "Close",
                onClick = { viewModel.onClose(activity) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
