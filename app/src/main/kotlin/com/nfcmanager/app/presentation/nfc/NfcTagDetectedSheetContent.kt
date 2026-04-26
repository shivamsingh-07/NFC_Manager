package com.nfcmanager.app.presentation.nfc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContactMail
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nfcmanager.app.R
import com.nfcmanager.app.domain.model.NfcTag
import com.nfcmanager.app.domain.model.TagPayload
import com.nfcmanager.app.presentation.theme.ButtonShape
import com.nfcmanager.app.presentation.theme.LocalAppColors

@Composable
fun NfcTagDetectedSheetContent(
    tag: NfcTag,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val payload = tag.primaryPayload) {
            is TagPayload.Text -> TextSheet(payload = payload, context = context)
            is TagPayload.Uri -> UriSheet(payload = payload, context = context)
            is TagPayload.Contact -> ContactSheet(payload = payload, context = context)
            is TagPayload.WiFi -> WifiSheet(payload = payload, context = context)
            is TagPayload.Bluetooth -> BluetoothSheet(payload = payload, context = context)
            is TagPayload.Raw -> RawSheet(payload = payload, context = context)
            null -> EmptyTagSheet(tag = tag)
        }
    }
}

@Composable
private fun TextSheet(payload: TagPayload.Text, context: Context) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(LocalAppColors.current.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.TaskAlt,
            contentDescription = null,
            tint = LocalAppColors.current.onAccentContainer,
            modifier = Modifier.size(32.dp),
        )
    }
    Spacer(Modifier.height(0.dp)) // Already using spacedBy
    Text(
        text = "Text Tag Detected",
        style = MaterialTheme.typography.headlineSmall,
    )
    ContentBox {
        Text(
            text = payload.text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
        )
    }
    ActionRow {
        CopyButton(text = payload.text, context = context)
        ShareButton(text = payload.text, context = context)
    }
}

@Composable
private fun UriSheet(payload: TagPayload.Uri, context: Context) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(LocalAppColors.current.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.TaskAlt,
            contentDescription = null,
            tint = LocalAppColors.current.onAccentContainer,
            modifier = Modifier.size(32.dp),
        )
    }
    Text(
        text = "Link Tag Detected",
        style = MaterialTheme.typography.headlineSmall,
    )
    ContentBox {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Link, contentDescription = null, tint = LocalAppColors.current.accent, modifier = Modifier.size(18.dp))
            Text(
                text = payload.sanitizedUrl ?: payload.raw,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
    ActionRow {
        if (payload.isSafeToOpen) {
            FilledTonalButton(
                onClick = { openUrl(context, payload.sanitizedUrl!!) },
                shape = ButtonShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Open Link")
            }
        }
        CopyButton(text = payload.raw, context = context, label = "Copy URL")
    }
}

@Composable
private fun ContactSheet(payload: TagPayload.Contact, context: Context) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(LocalAppColors.current.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.TaskAlt,
            contentDescription = null,
            tint = LocalAppColors.current.onAccentContainer,
            modifier = Modifier.size(32.dp),
        )
    }
    Text(
        text = "Contact Tag Detected",
        style = MaterialTheme.typography.headlineSmall,
    )
    ContentBox {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            payload.displayName?.let {
                ContactRow(icon = { Icon(Icons.Rounded.Person, null, Modifier.size(16.dp), tint = LocalAppColors.current.accent) }, text = it, style = MaterialTheme.typography.titleMedium)
            }
            payload.phoneNumbers.firstOrNull()?.let {
                ContactRow(icon = { Icon(Icons.Rounded.Phone, null, Modifier.size(16.dp), tint = LocalAppColors.current.accent) }, text = it)
            }
            payload.emails.firstOrNull()?.let {
                ContactRow(icon = { Icon(Icons.Rounded.Email, null, Modifier.size(16.dp), tint = LocalAppColors.current.accent) }, text = it)
            }
            payload.organization?.let {
                ContactRow(icon = { Icon(Icons.Rounded.ContactMail, null, Modifier.size(16.dp), tint = LocalAppColors.current.accent) }, text = it)
            }
        }
    }
    ActionRow {
        FilledTonalButton(
            onClick = { saveContact(context, payload) },
            shape = ButtonShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Rounded.ContactMail, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("Save Contact")
        }
    }
}

@Composable
private fun WifiSheet(payload: TagPayload.WiFi, context: Context) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(LocalAppColors.current.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.TaskAlt,
            contentDescription = null,
            tint = LocalAppColors.current.onAccentContainer,
            modifier = Modifier.size(32.dp),
        )
    }
    Text(
        text = "Wi‑Fi Tag Detected",
        style = MaterialTheme.typography.headlineSmall,
    )
    ContentBox {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Wifi, contentDescription = null, tint = LocalAppColors.current.accent, modifier = Modifier.size(18.dp))
                Text(payload.ssid, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = payload.security.label(),
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.textSecondary,
            )
        }
    }
    ActionRow {
        FilledTonalButton(
            onClick = { connectToWifi(context, payload) },
            shape = ButtonShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text("Connect")
        }
    }
}

@Composable
private fun BluetoothSheet(payload: TagPayload.Bluetooth, context: Context) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(LocalAppColors.current.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.TaskAlt,
            contentDescription = null,
            tint = LocalAppColors.current.onAccentContainer,
            modifier = Modifier.size(32.dp),
        )
    }
    Text(
        text = "Bluetooth Tag Detected",
        style = MaterialTheme.typography.headlineSmall,
    )
    ContentBox {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = LocalAppColors.current.accent, modifier = Modifier.size(18.dp))
                Text(payload.deviceName, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "MAC: ${payload.macAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalAppColors.current.textSecondary,
            )
        }
    }
}

@Composable
private fun RawSheet(payload: TagPayload.Raw, context: Context) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(LocalAppColors.current.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.TaskAlt,
            contentDescription = null,
            tint = LocalAppColors.current.onAccentContainer,
            modifier = Modifier.size(32.dp),
        )
    }
    Text(
        text = "Tag Detected",
        style = MaterialTheme.typography.headlineSmall,
    )
    val hex = remember(payload) {
        payload.bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    }
    ContentBox {
        Text(
            text = hex.take(256).let { if (hex.length > 256) "$it…" else it },
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
    ActionRow {
        CopyButton(text = hex, context = context, label = "Copy hex")
    }
}

@Composable
private fun EmptyTagSheet(tag: NfcTag) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (tag.isWritable) LocalAppColors.current.accentContainer else LocalAppColors.current.errorContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (tag.isWritable) Icons.Rounded.TaskAlt else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (tag.isWritable) LocalAppColors.current.onAccentContainer else LocalAppColors.current.onErrorContainer,
            modifier = Modifier.size(32.dp),
        )
    }

    Text(
        text = "Empty Tag",
        style = MaterialTheme.typography.headlineSmall,
    )

    val statusColor = if (tag.isWritable) LocalAppColors.current.success else LocalAppColors.current.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (tag.isWritable) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = if (tag.isWritable) "Writable" else "Not Writable",
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ContactRow(
    icon: @Composable () -> Unit,
    text: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon()
        Text(text = text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ContentBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = LocalAppColors.current.elevatedSurface,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun ActionRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        content()
    }
}

@Composable
private fun CopyButton(text: String, context: Context, label: String = "Copy") {
    var copied by remember { mutableStateOf(false) }
    FilledTonalButton(
        onClick = {
            copyToClipboard(context, label, text)
            copied = true
        },
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        AnimatedContent(
            targetState = copied,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "copyLabel",
        ) { wasCopied -> Text(if (wasCopied) "Copied!" else label) }
    }
}

@Composable
private fun ShareButton(text: String, context: Context) {
    FilledTonalButton(
        onClick = { shareText(context, text) },
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text("Share")
    }
}

private fun TagPayload.WiFi.Security.label() = when (this) {
    TagPayload.WiFi.Security.OPEN -> "Open network"
    TagPayload.WiFi.Security.WEP -> "WEP security"
    TagPayload.WiFi.Security.WPA_WPA2_PSK -> "WPA/WPA2 secured"
    TagPayload.WiFi.Security.WPA3_SAE -> "WPA3 secured"
    TagPayload.WiFi.Security.UNKNOWN -> "Unknown security"
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun saveContact(context: Context, payload: TagPayload.Contact) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        payload.displayName?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
        payload.phoneNumbers.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        payload.emails.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        payload.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
    }
    runCatching { context.startActivity(intent) }
}

@Suppress("DEPRECATION")
private fun connectToWifi(context: Context, payload: TagPayload.WiFi) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(payload.ssid)
            .setIsHiddenSsid(payload.hidden)
        when (payload.security) {
            TagPayload.WiFi.Security.WPA_WPA2_PSK,
            TagPayload.WiFi.Security.WPA3_SAE,
            TagPayload.WiFi.Security.WEP,
            -> payload.password?.let { builder.setWpa2Passphrase(it) }
            else -> Unit
        }
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        wifiManager?.addNetworkSuggestions(listOf(builder.build()))
    }
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
