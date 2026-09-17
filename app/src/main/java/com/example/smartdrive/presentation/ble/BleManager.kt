package com.example.smartdrive.presentation.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val TX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null

    private var commandHandler: BleCommandHandler? = null
    fun setCommandHandler(handler: BleCommandHandler) { commandHandler = handler }

    // Reconnect bookkeeping
    private var reconnectDevice: BluetoothDevice? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null

    // Write queue (chunked writes)
    private val writeQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile private var writing = false

    // ---------------------------------------------------------------
    // SCAN CALLBACK
    // ---------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            if (name == "MY NAV" || name == "SmartDrive") {
                val bleDevice = BleDevice(device, result.rssi, name)
                val existing = _discoveredDevices.value
                if (existing.none { it.device.address == device.address }) {
                    _discoveredDevices.value = existing + bleDevice
                }
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _connectionState.value = ConnectionState.ERROR("Scan failed ($errorCode)")
        }
    }

    // ---------------------------------------------------------------
    // GATT CALLBACK
    // ---------------------------------------------------------------

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "Connected to ${g.device.address}")
                        gatt = g
                        reconnectDevice = g.device
                        // Request larger MTU for chunked writes
                        g.requestMtu(512)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.i(TAG, "Disconnected")
                        closeGatt()
                        if (reconnectDevice != null) {
                            _connectionState.value = ConnectionState.RECONNECTING
                            scheduleReconnect()
                        } else {
                            _connectionState.value = ConnectionState.DISCONNECTED
                        }
                    }
                }
            } else {
                Log.e(TAG, "Connection state change error: $status")
                closeGatt()
                _connectionState.value = ConnectionState.ERROR("GATT error $status")
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu (status=$status)")
            // After MTU negotiation, discover services
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                _connectionState.value = ConnectionState.ERROR("Service discovery failed")
                g.disconnect()
                return
            }
            val service = g.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Service not found")
                _connectionState.value = ConnectionState.ERROR("Service not found")
                g.disconnect()
                return
            }

            rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
            txCharacteristic = service.getCharacteristic(TX_CHAR_UUID)

            // Subscribe to TX notifications (commands from ESP32)
            txCharacteristic?.let { tx ->
                g.setCharacteristicNotification(tx, true)
                val cccd = tx.getDescriptor(CCCD_UUID)
                if (cccd != null) {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                }
            }

            _connectionState.value = ConnectionState.CONNECTED
            Log.i(TAG, "Services ready")

            // Prime the ESP32 with app info + time + battery
            sendData(BlePacket.encodeAppInfo(appCode = 100, appVersion = "1.0.0"))
            sendData(BlePacket.encodeChunkedConfig(true))
            sendData(BlePacket.encodeTime())
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            status: Int
        ) {
            writing = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Write failed: $status")
            }
            processWriteQueue()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            handleIncoming(c.value ?: return)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncoming(value)
        }

        private fun handleIncoming(value: ByteArray) {
            if (commandHandler != null && value.isNotEmpty()) {
                commandHandler!!.handle(value)
            }
        }
    }

    // ---------------------------------------------------------------
    // PUBLIC API
    // ---------------------------------------------------------------

    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionState.ERROR("Bluetooth disabled")
            return
        }
        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.SCANNING

        val filters = listOf(
            ScanFilter.Builder().setDeviceName("MY NAV").build(),
            ScanFilter.Builder().setDeviceName("SmartDrive").build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(filters, settings, scanCallback)
            ?: run { _connectionState.value = ConnectionState.ERROR("Scanner unavailable") }
    }

    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        reconnectDevice = device
        cancelReconnect()
        closeGatt()
        _connectionState.value = ConnectionState.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        reconnectDevice = null
        cancelReconnect()
        gatt?.disconnect()
        closeGatt()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /** Send a full packet; auto-chunks if > 20 bytes. */
    fun sendData(packet: ByteArray): Boolean {
        if (gatt == null || rxCharacteristic == null) {
            Log.e(TAG, "Not connected")
            return false
        }

        if (packet.size <= 20) {
            writeQueue.add(packet)
        } else {
            writeQueue.add(packet.copyOfRange(0, 20))
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

    fun sendData(packet: String): Boolean = sendData(packet.toByteArray(Charsets.UTF_8))

    // ---------------------------------------------------------------
    // INTERNALS
    // ---------------------------------------------------------------

    private fun processWriteQueue() {
        if (writing) return
        val next = writeQueue.poll()
        if (next == null) return
        val g = gatt ?: return
        val c = rxCharacteristic ?: return

        writing = true
        c.value = next
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (!g.writeCharacteristic(c)) {
            writing = false
            Log.w(TAG, "writeCharacteristic returned false")
            processWriteQueue()
        }
    }

    private fun closeGatt() {
        gatt?.close()
        gatt = null
        rxCharacteristic = null
        txCharacteristic = null
        writing = false
        writeQueue.clear()
    }

    private fun scheduleReconnect() {
        cancelReconnect()
        reconnectRunnable = Runnable {
            val d = reconnectDevice ?: return@Runnable
            Log.i(TAG, "Reconnecting to ${d.address}")
            _connectionState.value = ConnectionState.CONNECTING
            gatt = d.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
        reconnectHandler.postDelayed(reconnectRunnable!!, RECONNECT_DELAY_MS)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { reconnectHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }
}

sealed class ConnectionState {
    object DISCONNECTED : ConnectionState()
    object SCANNING : ConnectionState()
    object CONNECTING : ConnectionState()
    object RECONNECTING : ConnectionState()
    object CONNECTED : ConnectionState()
    data class ERROR(val message: String) : ConnectionState()
}