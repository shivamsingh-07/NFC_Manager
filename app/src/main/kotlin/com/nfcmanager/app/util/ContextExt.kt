package com.nfcmanager.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks up the [ContextWrapper] chain to find the hosting [Activity], if any.
 * Compose's LocalContext is a ContextThemeWrapper; unwrap before using APIs
 * that require an Activity (like NFC Reader Mode).
 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
