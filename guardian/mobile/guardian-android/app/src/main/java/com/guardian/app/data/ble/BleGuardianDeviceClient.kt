package com.guardian.app.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.domain.CrashStatus
import com.guardian.app.domain.DeviceCommand
import com.guardian.app.domain.DeviceStatus
import com.guardian.app.domain.GuardianDeviceClient
import com.guardian.app.domain.LinkState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

object BlePermissions {
    fun required(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun granted(context: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Real BLE link to the ESP32 Guardian device. Android GATT allows one operation at a time,
 * so every operation goes through [gattOp], which serialises and awaits its callback.
 */
@SuppressLint("MissingPermission") // every entry point checks BlePermissions.granted()
class BleGuardianDeviceClient(
    private val context: Context,
    private val scope: CoroutineScope,
) : GuardianDeviceClient {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _link = MutableStateFlow(LinkState.DISCONNECTED)
    private val _status = MutableStateFlow<DeviceStatus?>(null)
    private val _crash = MutableStateFlow<CrashStatus?>(null)
    private val _events = MutableSharedFlow<CrashEventRecord>(extraBufferCapacity = 64)

    override val linkState: StateFlow<LinkState> = _link
    override val status: StateFlow<DeviceStatus?> = _status
    override val crashStatus: StateFlow<CrashStatus?> = _crash
    override val events: SharedFlow<CrashEventRecord> = _events

    private var gatt: BluetoothGatt? = null
    private var autoReconnect = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private val opMutex = Mutex()
    @Volatile private var pendingOp: CompletableDeferred<Boolean>? = null

    override fun connect() {
        autoReconnect = true
        reconnectJob?.cancel()
        if (_link.value == LinkState.DISCONNECTED) startScan()
    }

    override fun disconnect(userInitiated: Boolean) {
        if (userInitiated) autoReconnect = false
        reconnectJob?.cancel()
        stopScan()
        if (BlePermissions.granted(context)) {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        pendingOp?.complete(false)
        _link.value = LinkState.DISCONNECTED
    }

    override suspend fun send(command: DeviceCommand): Boolean {
        val g = gatt ?: return false
        if (_link.value != LinkState.CONNECTED) return false
        val c = g.getService(GuardianBleUuids.SERVICE)?.getCharacteristic(GuardianBleUuids.COMMAND) ?: return false
        return gattOp { writeChar(g, c, BlePayloadParser.encodeCommand(command)) }
    }

    override suspend fun sendBlackBox(json: String): Boolean {
        val g = gatt ?: return false
        if (_link.value != LinkState.CONNECTED) return false
        val c = g.getService(GuardianBleUuids.SERVICE)?.getCharacteristic(GuardianBleUuids.BLACKBOX) ?: return false
        val data = (json + "\u0004").toByteArray()  // EOT marks the end of the bundle
        var i = 0
        while (i < data.size) {
            val end = minOf(i + BLACKBOX_CHUNK, data.size)
            if (!gattOp { writeChar(g, c, data.copyOfRange(i, end)) }) return false
            i = end
        }
        return true
    }

    private fun writeChar(g: BluetoothGatt, c: BluetoothGattCharacteristic, bytes: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            legacyWrite(g, c, bytes)
        }

    @Suppress("DEPRECATION")
    private fun legacyWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        c.value = bytes
        return g.writeCharacteristic(c)
    }

    @Suppress("DEPRECATION")
    private fun legacyWriteDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor, value: ByteArray): Boolean {
        d.value = value
        return g.writeDescriptor(d)
    }

    @Suppress("DEPRECATION")
    private fun legacyValue(c: BluetoothGattCharacteristic): ByteArray = c.value ?: ByteArray(0)

    // ---- scanning --------------------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (_link.value != LinkState.SCANNING) return
            stopScan()
            connectTo(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed $errorCode")
            _link.value = LinkState.DISCONNECTED
            scheduleReconnect()
        }
    }

    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || adapter?.isEnabled != true || !BlePermissions.granted(context)) {
            Log.w(TAG, "BLE unavailable or permission missing")
            _link.value = LinkState.DISCONNECTED
            return
        }
        _link.value = LinkState.SCANNING
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(GuardianBleUuids.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        scanTimeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_link.value == LinkState.SCANNING) {
                stopScan()
                _link.value = LinkState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    private fun stopScan() {
        scanTimeoutJob?.cancel()
        if (BlePermissions.granted(context)) adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private fun connectTo(device: BluetoothDevice) {
        _link.value = LinkState.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun scheduleReconnect() {
        if (!autoReconnect) return
        reconnectJob?.cancel()
        val backoff = (2000L shl reconnectAttempt.coerceAtMost(4)).coerceAtMost(30_000L)
        reconnectAttempt++
        reconnectJob = scope.launch {
            delay(backoff)
            if (autoReconnect && _link.value == LinkState.DISCONNECTED) startScan()
        }
    }

    // ---- GATT ------------------------------------------------------------------------------

    private suspend fun gattOp(timeoutMs: Long = 5000, start: () -> Boolean): Boolean = opMutex.withLock {
        val done = CompletableDeferred<Boolean>()
        pendingOp = done
        val started = runCatching(start).getOrDefault(false)
        val ok = started && (withTimeoutOrNull(timeoutMs) { done.await() } ?: false)
        pendingOp = null
        ok
    }

    private suspend fun setup(g: BluetoothGatt) {
        gattOp { g.requestMtu(247) }
        if (!gattOp(10_000) { g.discoverServices() }) {
            g.disconnect()
            return
        }
        val svc = g.getService(GuardianBleUuids.SERVICE) ?: run {
            Log.w(TAG, "Guardian service not found")
            g.disconnect()
            return
        }
        for (uuid in GuardianBleUuids.NOTIFY_CHARACTERISTICS) {
            svc.getCharacteristic(uuid)?.let { enableNotify(g, it) }
        }
        for (uuid in GuardianBleUuids.NOTIFY_CHARACTERISTICS - GuardianBleUuids.EVENT) {
            svc.getCharacteristic(uuid)?.let { c -> gattOp { g.readCharacteristic(c) } }
        }
        reconnectAttempt = 0
        _link.value = LinkState.CONNECTED
        // Recovery: ask the device to replay events that happened while the phone was away.
        send(DeviceCommand.GetHistory)
    }

    private suspend fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
        if (!g.setCharacteristicNotification(c, true)) return false
        val d = c.getDescriptor(GuardianBleUuids.CCCD) ?: return false
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return gattOp {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
            } else {
                legacyWriteDescriptor(g, d, value)
            }
        }
    }

    private fun handleValue(uuid: UUID, value: ByteArray) {
        when (uuid) {
            GuardianBleUuids.CRASH_STATUS -> BlePayloadParser.parseCrashStatus(value)?.let { _crash.value = it }
            GuardianBleUuids.EVENT -> BlePayloadParser.parseEvent(value)?.let { _events.tryEmit(it) }
            else -> BlePayloadParser.applyStatus(_status.value ?: DeviceStatus(), uuid, value, System.currentTimeMillis())
                ?.let { _status.value = it }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                scope.launch { setup(g) }
            } else {
                Log.i(TAG, "disconnected (status=$status) - device keeps its own emergency path")
                g.close()
                if (gatt === g) gatt = null
                pendingOp?.complete(false)
                _link.value = LinkState.DISCONNECTED
                scheduleReconnect()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            pendingOp?.complete(true)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            pendingOp?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pendingOp?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingOp?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) handleValue(c.uuid, value)
            pendingOp?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Used on Android 12 and lower")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            if (status == BluetoothGatt.GATT_SUCCESS) handleValue(c.uuid, legacyValue(c))
            pendingOp?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handleValue(c.uuid, value)
        }

        @Deprecated("Used on Android 12 and lower")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            handleValue(c.uuid, legacyValue(c))
        }
    }

    private companion object {
        const val TAG = "GuardianBle"
        const val SCAN_TIMEOUT_MS = 15_000L
        const val BLACKBOX_CHUNK = 180
    }
}
