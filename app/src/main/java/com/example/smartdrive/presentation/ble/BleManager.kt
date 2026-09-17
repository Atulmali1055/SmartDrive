package com.example.smartdrive.presentation.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

@SuppressLint("MissingPermission")
class BleManager(val context: Context) {

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
        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")   // Write (Phone -> ESP32)
        val TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")   // Notify (ESP32 -> Phone)
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
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
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var commandHandler: BleCommandHandler? = null

    private val writeQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile
    private var writing = false

    fun setCommandHandler(handler: BleCommandHandler) {
        commandHandler = handler
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            val rssi = result.rssi
            if (name == "MY NAV") {
                val bleDevice = BleDevice(device, rssi, name)
                if (_discoveredDevices.value.none { it.device.address == device.address }) {
                    _discoveredDevices.value = _discoveredDevices.value + bleDevice
                }
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
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        resetGatt()
                        Log.i(TAG, "Disconnected")
                    }
                }
            } else {
                Log.e(TAG, "Connection state change error: $status")
                _connectionState.value = ConnectionState.ERROR("GATT error $status")
                resetGatt()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
                    val txChar = service.getCharacteristic(TX_CHAR_UUID)
                    txChar?.let {
                        txCharacteristic = it
                        gatt.setCharacteristicNotification(it, true)
                        val cccd = it.getDescriptor(CCCD_UUID)
                        if (cccd != null) {
                            @Suppress("DEPRECATION")
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(cccd)
                        }
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

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == TX_CHAR_UUID) {
                val data = characteristic.value ?: return
                commandHandler?.handle(data)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writing = false
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Write successful")
            } else {
                Log.e(TAG, "Write failed: $status")
            }
            processWriteQueue()
        }
    }

    private fun resetGatt() {
        this@BleManager.gatt?.close()
        this@BleManager.gatt = null
        rxCharacteristic = null
        txCharacteristic = null
        writeQueue.clear()
        writing = false
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
            resetGatt()
        }
        _connectionState.value = ConnectionState.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
        resetGatt()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /** Send a full packet, automatically chunking if > 20 bytes. */
    fun sendData(packet: ByteArray): Boolean {
        if (gatt == null || rxCharacteristic == null) {
            Log.e(TAG, "Not connected")
            return false
        }

        if (packet.size <= 20) {
            // Single write
            writeQueue.add(packet)
        } else {
            // Chunk 1: first 20 bytes as-is
            writeQueue.add(packet.copyOfRange(0, 20))
            // Chunks 2..N: [seq][up to 19 bytes]
            var offset = 20
            var seq = 0
            while (offset < packet.size) {
                val chunkSize = minOf(19, packet.size - offset)
                val chunk = ByteArray(chunkSize + 1)
                chunk[0] = seq.toByte()
                System.arraycopy(packet, offset, chunk, 1, chunkSize)
                writeQueue.add(chunk)
                offset += chunkSize
                seq++
            }
        }

        processWriteQueue()
        return true
    }

    private fun processWriteQueue() {
        if (writing) return
        val next = writeQueue.poll() ?: return
        val g = gatt ?: return
        val ch = rxCharacteristic ?: return

        writing = true
        @Suppress("DEPRECATION")
        ch.value = next
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        @Suppress("DEPRECATION")
        if (!g.writeCharacteristic(ch)) {
            writing = false
            Log.e(TAG, "writeCharacteristic returned false")
            processWriteQueue() // try next
        }
    }

    // Keep the String overload for backward compat
    fun sendData(packet: String): Boolean = sendData(packet.toByteArray(Charsets.UTF_8))
}

sealed class ConnectionState {
    object DISCONNECTED : ConnectionState()
    object SCANNING : ConnectionState()
    object CONNECTING : ConnectionState()
    object RECONNECTING : ConnectionState()
    object CONNECTED : ConnectionState()
    data class ERROR(val message: String) : ConnectionState()
}
