package com.nfcmanager.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nfcmanager.app.data.local.NfcActionDao
import com.nfcmanager.app.nfc.AppProcessForeground
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class NfcManagerApplication : Application() {

    @Inject lateinit var actionDao: NfcActionDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    AppProcessForeground.setInForeground(true)
                }

                override fun onStop(owner: LifecycleOwner) {
                    AppProcessForeground.setInForeground(false)
                }
            },
        )

        // One-shot cleanup of action rows whose `typeName` no longer maps
        // to a supported ActionType. Right now this catches legacy
        // APP_ACTIVITY rows from before the App Action / Open App merge —
        // without this they'd silently squat the unique uidHash index and
        // prevent remapping the same physical tag. Cheap query, fire and
        // forget; failure here is non-fatal.
        appScope.launch {
            runCatching { actionDao.purgeUnsupportedTypes() }
                .onSuccess { count ->
                    if (count > 0) Log.i(TAG, "Purged $count unsupported action row(s)")
                }
                .onFailure { Log.w(TAG, "Legacy action purge failed", it) }
        }
    }

    companion object {
        private const val TAG = "NfcManagerApp"
    }
}
