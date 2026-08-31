package com.example.smartdrive.presentation.ble

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val device: BluetoothDevice,
    val rssi: Int,
    val name: String? = device.name
)