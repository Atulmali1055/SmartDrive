package com.example.smartdrive.presentation.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"

        @Volatile
        private var INSTANCE: BleManager? = null

        fun get(context: Context): BleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Service & characteristic UUIDs (matching ESP32)
        val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val RX_CHAR_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")   // Write (Phone -> ESP32)
        val TX_CHAR_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")   // Notify (ESP32 -> Phone)
    }

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    // UI state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            val rssi = result.rssi
            // Filter for "MY NAV" – we can add a filter in scan builder, but we do it here too
            if (name == "MY NAV") {
                val bleDevice = BleDevice(device, rssi, name)
                _discoveredDevices.value = _discoveredDevices.value + bleDevice
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error $errorCode")
            _connectionState.value = ConnectionState.ERROR("Scan failed")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectionState.value = ConnectionState.CONNECTED
                        Log.i(TAG, "Connected to ${gatt.device.address}")
                        // Discover services
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        this@BleManager.gatt?.close()
                        this@BleManager.gatt = null
                        rxCharacteristic = null
                        Log.i(TAG, "Disconnected")
                    }
                }
            } else {
                Log.e(TAG, "Connection state change error: $status")
                _connectionState.value = ConnectionState.ERROR("GATT error $status")
                gatt.close()
                this@BleManager.gatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
                    // Also enable notifications on TX characteristic if needed
                    val txChar = service.getCharacteristic(TX_CHAR_UUID)
                    txChar?.let {
                        gatt.setCharacteristicNotification(it, true)
                        // For future use: listen to ESP32 messages
                    }
                    _connectionState.value = ConnectionState.CONNECTED
                    Log.i(TAG, "Services discovered")
                } else {
                    Log.e(TAG, "Service not found")
                    _connectionState.value = ConnectionState.ERROR("Service not found")
                    gatt.disconnect()
                }
            } else {
                Log.e(TAG, "Service discovery error: $status")
                _connectionState.value = ConnectionState.ERROR("Service discovery error")
                gatt.disconnect()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Write successful")
            } else {
                Log.e(TAG, "Write failed: $status")
                _connectionState.value = ConnectionState.ERROR("Write failed")
            }
        }
    }

    // Public methods

    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionState.ERROR("Bluetooth disabled")
            return
        }
        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.SCANNING

        val scanFilters = listOf(
            ScanFilter.Builder().setDeviceName("MY NAV").build()
        )
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        bleScanner?.startScan(scanFilters, scanSettings, scanCallback)
            ?: run {
                _connectionState.value = ConnectionState.ERROR("Scanner not available")
            }
    }

    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (gatt != null) {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        }
        _connectionState.value = ConnectionState.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        rxCharacteristic = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendData(packet: String): Boolean {
        if (gatt == null || rxCharacteristic == null) {
            Log.e(TAG, "Not connected or characteristic missing")
            return false
        }
        val bytes = packet.toByteArray(Charsets.UTF_8)
        rxCharacteristic?.value = bytes
        rxCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return gatt?.writeCharacteristic(rxCharacteristic) ?: false
    }
}

sealed class ConnectionState {
    object DISCONNECTED : ConnectionState()
    object SCANNING : ConnectionState()
    object CONNECTING : ConnectionState()
    object CONNECTED : ConnectionState()
    data class ERROR(val message: String) : ConnectionState()
    // For development, we might also add RECONNECTING later
}