package com.nfcmanager.app.domain.action

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.getSystemService
import com.nfcmanager.app.domain.model.ActionPayload
import com.nfcmanager.app.domain.model.NfcAction
import com.nfcmanager.app.domain.util.PackageValidator
import com.nfcmanager.app.domain.util.UrlValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Result of attempting to execute a user-defined [NfcAction]. */
sealed interface ActionResult {
    data class Success(val message: String) : ActionResult
    data class Failure(val reason: String) : ActionResult
}

/**
 * Executes user-defined NFC actions with strict validation.
 *
 * Design guarantees:
 *  - URL targets are revalidated by [UrlValidator] even though they were
 *    validated on save — a storage compromise shouldn't yield an injection.
 *  - App launch targets are revalidated by [PackageValidator] so an
 *    uninstall between save and trigger fails cleanly.
 *  - Wi-Fi and Bluetooth "toggles" open the system-provided settings panel
 *    instead of silently flipping state — required for Android 10+, and it
 *    also matches our rule "no silent changes without user awareness".
 *  - Flashlight uses `CameraManager.setTorchMode`; no CAMERA permission is
 *    required, but we treat any [CameraAccessException] as a soft failure.
 */
@Singleton
class ActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val urlValidator: UrlValidator,
    private val packageValidator: PackageValidator,
) {

    @Volatile private var torchState: Boolean = false

    fun execute(action: NfcAction): ActionResult = when (val p = action.payload) {
        is ActionPayload.OpenUrl -> openUrl(p.url)
        is ActionPayload.OpenApp -> openApp(p.packageName)
        is ActionPayload.ConnectWifi -> connectWifi(p.ssid, p.password, p.security)
        is ActionPayload.ConnectBluetooth -> connectBluetooth(p.macAddress)
        ActionPayload.ToggleFlashlight -> toggleFlashlight()
    }

    private fun openUrl(raw: String): ActionResult {
        val sanitized = urlValidator.sanitize(raw)
            ?: return ActionResult.Failure("Blocked unsafe URL")
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(sanitized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        if (view.resolveActivity(context.packageManager) == null) {
            return ActionResult.Failure("No browser available")
        }
        context.startActivity(view)
        return ActionResult.Success("Opened $sanitized")
    }

    private fun openApp(pkg: String): ActionResult {
        if (!packageValidator.isInstalledAndLaunchable(pkg)) {
            return ActionResult.Failure("App not installed: $pkg")
        }
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ActionResult.Failure("No launcher entry for $pkg")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return ActionResult.Success("Launched $pkg")
    }

    private fun connectWifi(ssid: String, pass: String?, security: String): ActionResult {
        // Modern Android (10+) requires user approval for Wi-Fi changes.
        // For simplicity in this demo/MVP, we'll open the Wi-Fi picker with pre-filled SSID info 
        // if possible, or use the system's "Add Network" dialog.
        return openSettingsPanel(Settings.Panel.ACTION_WIFI, "Wi-Fi ($ssid)")
    }

    private fun connectBluetooth(address: String): ActionResult {
        // Connecting to a specific device usually requires BLUETOOTH_CONNECT.
        // We'll open the Bluetooth settings as a safe, system-sanctioned fallback.
        return openBluetoothSettings()
    }

    private fun openSettingsPanel(action: String, label: String): ActionResult {
        return try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            ActionResult.Success("Opened $label settings")
        } catch (e: Exception) {
            ActionResult.Failure("Cannot open $label settings: ${e.message}")
        }
    }

    private fun openBluetoothSettings(): ActionResult {
        // Deliberately using the settings screen instead of BluetoothAdapter.enable()
        // which is deprecated (API 33) and requires BLUETOOTH_CONNECT. This keeps
        // the user in control and avoids requesting a sensitive runtime permission
        // just to flip a switch.
        return openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
    }

    private fun toggleFlashlight(): ActionResult {
        val cm = context.getSystemService<CameraManager>()
            ?: return ActionResult.Failure("Camera service unavailable")
        return try {
            val backCameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionResult.Failure("No flash on this device")
            val next = !torchState
            cm.setTorchMode(backCameraId, next)
            torchState = next
            ActionResult.Success(if (next) "Flashlight on" else "Flashlight off")
        } catch (e: CameraAccessException) {
            ActionResult.Failure("Flashlight unavailable (${e.reason})")
        } catch (e: IllegalArgumentException) {
            ActionResult.Failure("Flashlight error: ${e.message}")
        }
    }
}
