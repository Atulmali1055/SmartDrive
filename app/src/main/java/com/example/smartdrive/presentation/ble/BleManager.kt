package com.example.smartdrive.presentation.ble

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

    // Write queue for chunked writes
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

                        // Request a larger MTU for efficient chunked writes.
                        // This is best-effort — discovery is not gated on it.
                        g.requestMtu(512)

                        // Start service discovery immediately.
                        g.discoverServices()
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

        /**
         * MTU negotiation result. Do NOT call discoverServices() from here —
         * it was already called in onConnectionStateChange. If MTU succeeds,
         * we just log it; if it fails, the default 23-byte MTU still works
         * (chunking handles oversized packets).
         */
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU negotiated: $mtu")
            } else {
                Log.d(TAG, "MTU negotiation failed (status=$status), using default")
            }
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
                Log.e(TAG, "Service not found (UUID mismatch?)")
                _connectionState.value = ConnectionState.ERROR("Service not found")
                g.disconnect()
                return
            }

            rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
            txCharacteristic = service.getCharacteristic(TX_CHAR_UUID)

            if (rxCharacteristic == null) {
                Log.e(TAG, "RX characteristic not found")
                _connectionState.value = ConnectionState.ERROR("RX characteristic missing")
                g.disconnect()
                return
            }

            // Subscribe to TX notifications (commands from ESP32 → phone)
            txCharacteristic?.let { tx ->
                g.setCharacteristicNotification(tx, true)
                val cccd = tx.getDescriptor(CCCD_UUID)
                if (cccd != null) {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                } else {
                    Log.w(TAG, "CCCD descriptor not found on TX")
                }
            }

            _connectionState.value = ConnectionState.CONNECTED
            Log.i(TAG, "Services ready — connection fully established")

            // Prime the ESP32 with app info, chunked config, and time
            sendData(BlePacket.encodeAppInfo(appCode = 100, appVersion = "1.0.0"))
            sendData(BlePacket.encodeChunkedConfig(true))
            sendData(BlePacket.encodeTime())
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write OK (notifications enabled)")
            } else {
                Log.w(TAG, "Descriptor write failed: $status")
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            status: Int
        ) {
            writing = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Characteristic write failed: $status")
            }
            processWriteQueue()
        }

        // API < 33
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            handleIncoming(c.value ?: return)
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncoming(value)
        }

        private fun handleIncoming(value: ByteArray) {
            if (value.isEmpty()) return
            Log.d(TAG, "RX from ESP32: ${value.size} bytes")
            commandHandler?.handle(value)
        }
    }

    // ---------------------------------------------------------------
    // PUBLIC API — SCAN
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
            ?: run {
                _connectionState.value = ConnectionState.ERROR("Scanner unavailable")
            }
    }

    fun stopScan() {
        bleScanner?.stopScan(scanCallback)
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // ---------------------------------------------------------------
    // PUBLIC API — CONNECT / DISCONNECT
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // PUBLIC API — WRITE
    // ---------------------------------------------------------------

    /**
     * Send a full packet over BLE. Automatically chunks if > 20 bytes,
     * using the Chronos chunked format:
     *
     *   - First write: 20 bytes as-is (header + len + footer + type + subtype + payload[0..13])
     *   - Subsequent:  [seq] + up to 19 data bytes
     */
    fun sendData(packet: ByteArray): Boolean {
        if (gatt == null || rxCharacteristic == null) {
            Log.w(TAG, "sendData: not connected")
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

    fun sendData(packet: String): Boolean =
        sendData(packet.toByteArray(Charsets.UTF_8))

    // ---------------------------------------------------------------
    // INTERNALS
    // ---------------------------------------------------------------

    private fun processWriteQueue() {
        if (writing) return
        val next = writeQueue.poll() ?: return
        val g = gatt ?: return
        val c = rxCharacteristic ?: return

        writing = true
        c.value = next
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        val ok = try {
            g.writeCharacteristic(c)
        } catch (e: Exception) {
            Log.e(TAG, "writeCharacteristic threw", e)
            false
        }

        if (!ok) {
            writing = false
            Log.w(TAG, "writeCharacteristic returned false — retrying next")
            processWriteQueue()
        }
    }

    private fun closeGatt() {
        try { gatt?.close() } catch (_: Exception) {}
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
            Log.i(TAG, "Auto-reconnect → ${d.address}")
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