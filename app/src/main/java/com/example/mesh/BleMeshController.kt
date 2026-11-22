package com.example.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BluetoothHardwareState(
    val isBleSupported: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val hasRequiredPermissions: Boolean = false,
    val isScanningActive: Boolean = false,
    val isAdvertisingActive: Boolean = false,
    val statusMessage: String = "Initializing Bluetooth radio...",
    val errorNotice: String? = null
)

/**
 * Real Android Bluetooth Low Energy & Classic/GATT controller.
 * Never fabricates or simulates devices; all observations come from Android Bluetooth APIs.
 */
class BleMeshController(
    private val context: Context,
    private val engine: MeshRadarEngine,
    private val scope: CoroutineScope,
    private val onGroupAutoUpdated: () -> Unit
) {
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _hardwareState = MutableStateFlow(BluetoothHardwareState())
    val hardwareState: StateFlow<BluetoothHardwareState> = _hardwareState.asStateFlow()

    private val _discoveredCandidates = MutableStateFlow<List<DiscoveredBluetoothCandidate>>(emptyList())
    val discoveredCandidates: StateFlow<List<DiscoveredBluetoothCandidate>> = _discoveredCandidates.asStateFlow()

    private val candidateMap = LinkedHashMap<String, DiscoveredBluetoothCandidate>()

    private var scanCycleJob: Job? = null
    private var isBleScanningWindowOpen = false
    private var isAdvertising = false
    private var lastHardwareError: String? = null
    private var receiverRegistered = false

    private var currentScanMode: ScanPowerMode = ScanPowerMode.BALANCED
    private var isUserScanEnabled: Boolean = true
    private var isForegroundActive: Boolean = false
    private var isBroadcastWanted: Boolean = true
    private var localSlotNumber: Int = 1
    private var isPttActive: Boolean = false
    private var heartbeatCounter: Int = 0

    private val serviceParcelUuid = ParcelUuid(WALKIE_MESH_SERVICE_UUID)

    private val systemBluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (btState == BluetoothAdapter.STATE_OFF || btState == BluetoothAdapter.STATE_TURNING_OFF) {
                        stopScanAndAdvertise()
                        lastHardwareError = null
                        engine.exportEnrolledRecords().forEach {
                            engine.onExplicitDisconnect(it.address, System.currentTimeMillis())
                        }
                    } else if (btState == BluetoothAdapter.STATE_ON) {
                        lastHardwareError = null
                        syncScanAndAdvertiseState()
                    }
                    refreshHardwareStatus()
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = extractBluetoothDevice(intent) ?: return
                    if (engine.hasDevice(device.address)) {
                        engine.onProfileConnectedState(device.address, isConnected = true, nowMs = System.currentTimeMillis())
                    }
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = extractBluetoothDevice(intent) ?: return
                    if (engine.hasDevice(device.address)) {
                        engine.onExplicitDisconnect(device.address, System.currentTimeMillis())
                    }
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            lastHardwareError = null
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            lastHardwareError = null
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isBleScanningWindowOpen = false
            lastHardwareError = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "BLE scan already active"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "BLE scanner registration failed; retrying"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Hardware does not support this BLE scan mode"
                SCAN_FAILED_INTERNAL_ERROR -> "Bluetooth stack scan error; retrying"
                else -> "BLE scan error ($errorCode)"
            }
            refreshHardwareStatus()
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            lastHardwareError = null
            refreshHardwareStatus()
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            lastHardwareError = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Mesh beacon payload exceeded BLE limit"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "No free BLE advertiser slots on this device"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE peripheral advertising not supported on this device"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Bluetooth advertiser internal error"
                else -> "BLE advertise error ($errorCode)"
            }
            refreshHardwareStatus()
        }
    }

    fun startLifecycle() {
        registerReceiverIfNeeded()
        isForegroundActive = true
        refreshHardwareStatus()
        refreshBondedAndConnectedDevices()
        syncScanAndAdvertiseState()
    }

    fun stopLifecycle() {
        isForegroundActive = false
        stopScanAndAdvertise()
        unregisterReceiverIfNeeded()
        refreshHardwareStatus()
    }

    fun onPermissionsChanged() {
        lastHardwareError = null
        refreshHardwareStatus()
        refreshBondedAndConnectedDevices()
        syncScanAndAdvertiseState()
    }

    fun setScanningEnabled(enabled: Boolean) {
        isUserScanEnabled = enabled
        lastHardwareError = null
        syncScanAndAdvertiseState()
    }

    fun setScanPowerMode(mode: ScanPowerMode) {
        if (currentScanMode != mode) {
            currentScanMode = mode
            if (isUserScanEnabled && isForegroundActive) {
                restartScanDutyCycle()
            }
        }
    }

    fun setBroadcastConfig(enabled: Boolean, localSlot: Int, pttActive: Boolean) {
        isBroadcastWanted = enabled
        localSlotNumber = localSlot
        isPttActive = pttActive
        if (isForegroundActive) {
            updateAdvertisingBeacon()
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBondedAndConnectedDevices() {
        val adapter = bluetoothAdapter ?: return
        if (!hasConnectPermission()) return

        val now = System.currentTimeMillis()
        runCatching {
            adapter.bondedDevices?.forEach { device ->
                val addr = device.address ?: return@forEach
                val name = safeDeviceName(device) ?: addr
                val existing = candidateMap[addr.uppercase()]
                candidateMap[addr.uppercase()] = DiscoveredBluetoothCandidate(
                    address = addr.uppercase(),
                    name = name,
                    rssiDbm = existing?.rssiDbm,
                    isBonded = true,
                    isMeshPeer = existing?.isMeshPeer ?: false,
                    lastSeenMs = existing?.lastSeenMs ?: now,
                    meshPeerSlot = existing?.meshPeerSlot
                )
            }
        }

        runCatching {
            val gattConnected = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
            val connectedAddrs = gattConnected.mapNotNull { it.address?.uppercase() }.toSet()
            engine.exportEnrolledRecords().forEach { record ->
                if (record.address.uppercase() in connectedAddrs) {
                    engine.onProfileConnectedState(record.address, isConnected = true, nowMs = now)
                }
            }
        }

        emitCandidates()
    }

    fun refreshHardwareStatus() {
        val bleSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val adapter = bluetoothAdapter
        val enabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        val permsOk = hasAllRequiredPermissions()

        val msg = when {
            adapter == null || !bleSupported -> "Bluetooth LE hardware unavailable on this device"
            !permsOk -> "Nearby Bluetooth permissions required to scan mesh nodes"
            !enabled -> "Bluetooth radio is turned off"
            !isUserScanEnabled -> "Radar scanning paused (battery save)"
            lastHardwareError != null -> lastHardwareError!!
            isBleScanningWindowOpen -> "Live BLE Mesh scan active (${currentScanMode.label})"
            else -> "Duty-cycle idle window (${currentScanMode.label})"
        }

        _hardwareState.value = BluetoothHardwareState(
            isBleSupported = bleSupported && adapter != null,
            isBluetoothEnabled = enabled,
            hasRequiredPermissions = permsOk,
            isScanningActive = isBleScanningWindowOpen,
            isAdvertisingActive = isAdvertising,
            statusMessage = msg,
            errorNotice = lastHardwareError
        )
    }

    private fun syncScanAndAdvertiseState() {
        refreshHardwareStatus()
        if (isForegroundActive && isUserScanEnabled && canScanNow()) {
            if (scanCycleJob?.isActive != true) {
                restartScanDutyCycle()
            }
        } else {
            stopScanningOnly()
        }
        updateAdvertisingBeacon()
    }

    private fun restartScanDutyCycle() {
        stopScanningOnly()
        if (!isForegroundActive || !isUserScanEnabled || !canScanNow()) return

        scanCycleJob = scope.launch(Dispatchers.Default) {
            while (isActive && isForegroundActive && isUserScanEnabled && canScanNow()) {
                startBleScanWindow()
                delay(currentScanMode.scanWindowMs)
                stopBleScanWindow()
                heartbeatCounter = (heartbeatCounter + 1) and 0xFF
                updateAdvertisingBeacon()
                refreshBondedAndConnectedDevices()
                delay(currentScanMode.idlePauseMs)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScanWindow() {
        val scanner: BluetoothLeScanner = runCatching { bluetoothAdapter?.bluetoothLeScanner }.getOrNull() ?: return
        if (!hasScanPermission()) return

        val settings = ScanSettings.Builder()
            .setScanMode(
                if (currentScanMode == ScanPowerMode.BALANCED) {
                    ScanSettings.SCAN_MODE_BALANCED
                } else {
                    ScanSettings.SCAN_MODE_LOW_POWER
                }
            )
            .build()

        runCatching {
            scanner.startScan(null, settings, scanCallback)
            isBleScanningWindowOpen = true
            lastHardwareError = null
            refreshHardwareStatus()
        }.onFailure { err ->
            isBleScanningWindowOpen = false
            lastHardwareError = "Unable to start BLE scan: ${err.message ?: "Permission or radio error"}"
            refreshHardwareStatus()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScanWindow() {
        if (!isBleScanningWindowOpen) return
        val scanner: BluetoothLeScanner? = runCatching { bluetoothAdapter?.bluetoothLeScanner }.getOrNull()
        if (scanner != null && hasScanPermission()) {
            runCatching { scanner.stopScan(scanCallback) }
        }
        isBleScanningWindowOpen = false
        refreshHardwareStatus()
    }

    private fun stopScanningOnly() {
        scanCycleJob?.cancel()
        scanCycleJob = null
        stopBleScanWindow()
    }

    @SuppressLint("MissingPermission")
    private fun updateAdvertisingBeacon() {
        val advertiser: BluetoothLeAdvertiser? = runCatching { bluetoothAdapter?.bluetoothLeAdvertiser }.getOrNull()
        if (!isForegroundActive || !isBroadcastWanted || !canAdvertiseNow() || advertiser == null) {
            stopAdvertisingOnly()
            return
        }

        runCatching { advertiser.stopAdvertising(advertiseCallback) }

        val neighbors = engine.buildLocalRelayAdvertisementNeighbors(System.currentTimeMillis())
        val payload = MeshPayloadCodec.encode(
            localSlot = localSlotNumber,
            heartbeatSeq = heartbeatCounter,
            isPttActive = isPttActive,
            isVoiceReady = true,
            neighbors = neighbors
        )

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(
                if (isPttActive) AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                else AdvertiseSettings.ADVERTISE_MODE_BALANCED
            )
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(serviceParcelUuid)
            .addServiceData(serviceParcelUuid, payload)
            .build()

        runCatching {
            advertiser.startAdvertising(settings, data, advertiseCallback)
        }.onFailure { err ->
            isAdvertising = false
            lastHardwareError = "BLE advertise failed: ${err.message ?: "Unsupported"}"
            refreshHardwareStatus()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertisingOnly() {
        if (!isAdvertising) return
        val advertiser = runCatching { bluetoothAdapter?.bluetoothLeAdvertiser }.getOrNull()
        if (advertiser != null && hasAdvertisePermission()) {
            runCatching { advertiser.stopAdvertising(advertiseCallback) }
        }
        isAdvertising = false
        refreshHardwareStatus()
    }

    private fun stopScanAndAdvertise() {
        stopScanningOnly()
        stopAdvertisingOnly()
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val addr = device.address?.uppercase() ?: return
        val now = System.currentTimeMillis()
        val rssi = result.rssi
        val record = result.scanRecord

        val serviceData = record?.getServiceData(serviceParcelUuid)
        val hasMeshUuid = record?.serviceUuids?.any { it == serviceParcelUuid } == true || serviceData != null
        val decodedBeacon = MeshPayloadCodec.decode(serviceData)

        val deviceName = record?.deviceName?.takeIf { it.isNotBlank() }
            ?: safeDeviceName(device)
            ?: if (hasMeshUuid && decodedBeacon != null) {
                "Walkie-#${decodedBeacon.senderSlot.toString().padStart(2, '0')}"
            } else {
                addr
            }

        val isBonded = if (hasConnectPermission()) {
            runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
        } else {
            false
        }

        candidateMap[addr] = DiscoveredBluetoothCandidate(
            address = addr,
            name = deviceName,
            rssiDbm = rssi,
            isBonded = isBonded,
            isMeshPeer = hasMeshUuid,
            lastSeenMs = now,
            meshPeerSlot = decodedBeacon?.senderSlot
        )
        emitCandidates()

        val wasNewMeshPeer = hasMeshUuid && !engine.hasDevice(addr)
        if (hasMeshUuid || engine.hasDevice(addr)) {
            engine.onDirectObservation(
                address = addr,
                deviceName = deviceName,
                rssiDbm = rssi,
                isMeshPeer = hasMeshUuid,
                isPttActive = decodedBeacon?.isPttActive ?: false,
                heartbeatSeq = decodedBeacon?.heartbeatSeq ?: 0,
                relayedNeighbors = decodedBeacon?.neighbors ?: emptyList(),
                autoEnrollIfMeshPeer = true,
                nowMs = now
            )
            if (wasNewMeshPeer) {
                onGroupAutoUpdated()
            }
        }
    }

    private fun emitCandidates() {
        val sorted = candidateMap.values.sortedWith(
            compareByDescending<DiscoveredBluetoothCandidate> { it.isMeshPeer }
                .thenByDescending { it.isBonded }
                .thenByDescending { it.rssiDbm ?: Int.MIN_VALUE }
        )
        _discoveredCandidates.value = sorted
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? {
        if (!hasConnectPermission()) return null
        return runCatching { device.name?.takeIf { it.isNotBlank() } }.getOrNull()
    }

    private fun canScanNow(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return runCatching { adapter.isEnabled }.getOrDefault(false) && hasScanPermission()
    }

    private fun canAdvertiseNow(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return runCatching { adapter.isEnabled }.getOrDefault(false) && hasAdvertisePermission()
    }

    fun hasAllRequiredPermissions(): Boolean {
        return requiredBluetoothPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            context,
            systemBluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterReceiverIfNeeded() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(systemBluetoothReceiver) }
        receiverRegistered = false
    }

    @Suppress("DEPRECATION")
    private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    companion object {
        fun requiredBluetoothPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
                )
            }
        }
    }
}
