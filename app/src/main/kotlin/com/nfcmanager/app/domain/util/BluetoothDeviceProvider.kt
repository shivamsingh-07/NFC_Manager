package com.nfcmanager.app.domain.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothDeviceProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    data class DeviceEntry(val name: String, val address: String)

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<DeviceEntry> {
        if (adapter == null || !hasPermission()) return emptyList()
        
        return adapter.bondedDevices.map { 
            DeviceEntry(it.name ?: "Unknown Device", it.address)
        }.sortedBy { it.name.lowercase() }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Legacy permissions handled by manifest
        }
    }
}
