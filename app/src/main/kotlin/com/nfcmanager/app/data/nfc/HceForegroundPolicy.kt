package com.nfcmanager.app.data.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pins our [MessageEmulationService] as the preferred APDU responder while
 * the user is actively emulating a tag.
 *
 * Why this exists
 * ----------------
 * Symptom that motivated this class:
 *
 *   - Android 13 sender → Android 16 receiver: works (NDEF transferred).
 *   - Android 16 sender → Android 13 receiver: receiver shows "Empty Tag".
 *
 * The receive-side code path is identical on both phones — we run a manual
 * Type 4 NDEF reader over IsoDep, see [Type4NdefReader]. So the failure
 * must be on the *sender* and must be Android-version-specific. It is.
 *
 * Starting with Android 14 (API 34) and tightened further in 15/16, the NFC
 * stack stopped implicitly routing AIDs from a `category="other"` HCE
 * service to that service when the owning app is in the foreground. The
 * pre-Android-14 behaviour was: "if there's exactly one app registered for
 * AID X, route X to it." The new behaviour is: "if a foreground app has
 * called `setPreferredService(activity, component)`, route everything to
 * that service; otherwise, do not implicitly route `category="other"` AIDs
 * at all on some OEM builds."
 *
 * Net effect: an Android 16 sender running our app would never even see
 * `processCommandApdu` get called, because the system short-circuits the
 * SELECT AID before our service is bound. The receiver gets nothing → empty
 * tag. No crash, no log, just silence.
 *
 * What we do
 * ----------
 * When the user enters emulation, the [com.nfcmanager.app.presentation.write.WriteScreen]
 * calls [makeForeground] with the hosting activity. We then ask
 * [CardEmulation] to make our service the foreground-preferred responder.
 * The system gates this on the activity actually being on top, so we can't
 * accidentally steal routing from another NFC app — leaving the activity
 * un-pins us automatically.
 *
 * `unsetPreferredService` is also called explicitly in [clearForeground]
 * from `onDispose`, which covers the case where the user just dismisses the
 * emulation sheet without leaving the activity.
 *
 * `setPreferredService` is API 19+, so it's safe to call unconditionally
 * within our minSdk=31 floor. We still log return values so misconfigurations
 * (e.g. service not declared, AID conflict, NFC off) are observable.
 */
@Singleton
class HceForegroundPolicy @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
    private val cardEmulation: CardEmulation? = nfcAdapter?.let {
        runCatching { CardEmulation.getInstance(it) }
            .onFailure { Log.w(TAG, "CardEmulation.getInstance failed", it) }
            .getOrNull()
    }
    private val component: ComponentName =
        ComponentName(context, MessageEmulationService::class.java)

    init {
        // Surface routing-policy truth once, at construction. The platform
        // exposes no public API to enumerate registered HCE services, so the
        // best we can do without falling back to @SystemApi is verify that
        // the device permits foreground preference for our category.
        cardEmulation?.let { ce ->
            val allows = runCatching {
                ce.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_OTHER)
            }.getOrDefault(false)
            Log.d(TAG, "init: categoryAllowsForegroundPreference(other)=$allows component=$component")
        }
    }

    /**
     * Make our HCE service the preferred APDU responder for the supplied
     * [activity]'s foreground lifetime. Returns `true` when the system
     * accepted the preference, `false` when NFC is unavailable, the service
     * isn't registered, or the system rejected the request (rare; usually
     * because [activity] is not currently resumed).
     */
    fun makeForeground(activity: Activity): Boolean {
        val ce = cardEmulation ?: run {
            Log.w(TAG, "makeForeground: CardEmulation unavailable on this device")
            return false
        }
        val accepted = runCatching { ce.setPreferredService(activity, component) }
            .onFailure { Log.w(TAG, "setPreferredService threw", it) }
            .getOrDefault(false)
        Log.d(TAG, "setPreferredService($component) → $accepted (sdk=${Build.VERSION.SDK_INT})")
        return accepted
    }

    /**
     * Release the foreground preference. Safe to call multiple times and safe
     * to call when [makeForeground] was never called for this activity.
     */
    fun clearForeground(activity: Activity) {
        val ce = cardEmulation ?: return
        runCatching { ce.unsetPreferredService(activity) }
            .onFailure { Log.w(TAG, "unsetPreferredService threw", it) }
        Log.d(TAG, "unsetPreferredService done")
    }

    companion object {
        private const val TAG = "HceForegroundPolicy"
    }
}
