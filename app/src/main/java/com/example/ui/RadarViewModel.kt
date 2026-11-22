package com.example.ui

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mesh.BleMeshController
import com.example.mesh.BluetoothHardwareState
import com.example.mesh.DEFAULT_RADIO_CHANNELS
import com.example.mesh.DiscoveredBluetoothCandidate
import com.example.mesh.GroupRepository
import com.example.mesh.MAX_GROUP_DEVICES
import com.example.mesh.MeshHealthSummary
import com.example.mesh.MeshRadarEngine
import com.example.mesh.RadarDeviceNode
import com.example.mesh.RadarFilter
import com.example.mesh.RadioChannel
import com.example.mesh.ScanPowerMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WalkieRadarUiState(
    val operatorCallsign: String = "ALPHA-LEAD",
    val localSlotNumber: Int = 1,
    val selectedChannel: RadioChannel = DEFAULT_RADIO_CHANNELS.first(),
    val channels: List<RadioChannel> = DEFAULT_RADIO_CHANNELS,
    val isPttTransmitting: Boolean = false,
    val isBroadcastEnabled: Boolean = true,
    val isScanPaused: Boolean = false,
    val scanPowerMode: ScanPowerMode = ScanPowerMode.BALANCED,
    val hardwareState: BluetoothHardwareState = BluetoothHardwareState(),
    val nodes: List<RadarDeviceNode> = emptyList(),
    val summary: MeshHealthSummary = MeshHealthSummary(),
    val selectedFilter: RadarFilter = RadarFilter.ALL,
    val selectedNodeAddress: String? = null,
    val discoveredCandidates: List<DiscoveredBluetoothCandidate> = emptyList(),
    val nowMs: Long = System.currentTimeMillis(),
    val toastNotice: String? = null
)

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GroupRepository(application.applicationContext)
    private val engine = MeshRadarEngine()

    private val bleController = BleMeshController(
        context = application.applicationContext,
        engine = engine,
        scope = viewModelScope,
        onGroupAutoUpdated = { persistCurrentNodes() }
    )

    private val _uiState = MutableStateFlow(WalkieRadarUiState())
    val uiState: StateFlow<WalkieRadarUiState> = _uiState.asStateFlow()

    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
    }.getOrNull()

    init {
        viewModelScope.launch {
            val initial = repository.groupState.first()
            engine.loadEnrolledDevices(initial.enrolledDevices)
            val channel = DEFAULT_RADIO_CHANNELS.firstOrNull { it.number == initial.selectedChannelNumber }
                ?: DEFAULT_RADIO_CHANNELS.first()

            _uiState.value = _uiState.value.copy(
                operatorCallsign = initial.operatorCallsign,
                localSlotNumber = initial.localSlotNumber,
                selectedChannel = channel,
                scanPowerMode = initial.scanPowerMode,
                isBroadcastEnabled = initial.isBroadcastEnabled
            )

            bleController.setScanPowerMode(initial.scanPowerMode)
            bleController.setBroadcastConfig(
                enabled = initial.isBroadcastEnabled,
                localSlot = initial.localSlotNumber,
                pttActive = false
            )
            emitEvaluatedSnapshot()
        }

        viewModelScope.launch {
            bleController.hardwareState.collect { hw ->
                _uiState.value = _uiState.value.copy(hardwareState = hw)
            }
        }

        viewModelScope.launch {
            bleController.discoveredCandidates.collect { candidates ->
                _uiState.value = _uiState.value.copy(discoveredCandidates = candidates)
            }
        }

        // Efficient 1-second evaluation loop for heartbeat timeouts, route TTLs, and last-seen counters
        viewModelScope.launch {
            var persistTick = 0
            while (isActive) {
                emitEvaluatedSnapshot()
                persistTick++
                if (persistTick >= 15) {
                    persistTick = 0
                    persistCurrentNodes()
                }
                delay(1_000L)
            }
        }
    }

    fun onLifecycleStart() {
        bleController.startLifecycle()
        emitEvaluatedSnapshot()
    }

    fun onLifecycleStop() {
        if (_uiState.value.isPttTransmitting) {
            setPttTransmitting(false)
        }
        persistCurrentNodes()
        bleController.stopLifecycle()
    }

    fun onPermissionsUpdated() {
        bleController.onPermissionsChanged()
        emitEvaluatedSnapshot()
    }

    fun refreshDiscoveryNow() {
        bleController.refreshHardwareStatus()
        bleController.refreshBondedAndConnectedDevices()
        emitEvaluatedSnapshot()
    }

    fun toggleScanPaused() {
        val nextPaused = !_uiState.value.isScanPaused
        _uiState.value = _uiState.value.copy(isScanPaused = nextPaused)
        bleController.setScanningEnabled(!nextPaused)
    }

    fun setScanPowerMode(mode: ScanPowerMode) {
        _uiState.value = _uiState.value.copy(scanPowerMode = mode)
        bleController.setScanPowerMode(mode)
        viewModelScope.launch { repository.setScanPowerMode(mode) }
    }

    fun setBroadcastEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isBroadcastEnabled = enabled)
        bleController.setBroadcastConfig(
            enabled = enabled,
            localSlot = _uiState.value.localSlotNumber,
            pttActive = _uiState.value.isPttTransmitting
        )
        viewModelScope.launch { repository.setBroadcastEnabled(enabled) }
    }

    fun selectChannel(channelNumber: Int) {
        val channel = DEFAULT_RADIO_CHANNELS.firstOrNull { it.number == channelNumber } ?: return
        _uiState.value = _uiState.value.copy(selectedChannel = channel)
        runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 70) }
        viewModelScope.launch { repository.setSelectedChannel(channelNumber) }
    }

    fun setPttTransmitting(active: Boolean) {
        if (_uiState.value.isPttTransmitting == active) return
        _uiState.value = _uiState.value.copy(isPttTransmitting = active)
        bleController.setBroadcastConfig(
            enabled = _uiState.value.isBroadcastEnabled,
            localSlot = _uiState.value.localSlotNumber,
            pttActive = active
        )
        if (active) {
            runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 90) }
        } else {
            // Classic walkie-talkie roger beep when releasing PTT
            runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 110) }
        }
    }

    fun selectFilter(filter: RadarFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun selectNode(address: String?) {
        _uiState.value = _uiState.value.copy(selectedNodeAddress = address)
    }

    fun enrollCandidate(candidate: DiscoveredBluetoothCandidate, customLabel: String? = null) {
        if (engine.enrolledCount() >= MAX_GROUP_DEVICES && !engine.hasDevice(candidate.address)) {
            _uiState.value = _uiState.value.copy(
                toastNotice = "Group capacity full (20/20 devices). Remove a unit first."
            )
            return
        }
        val label = customLabel?.trim()?.takeIf { it.isNotEmpty() } ?: candidate.name
        val enrolled = engine.enrollDevice(
            address = candidate.address,
            preferredLabel = label,
            isMeshPeer = candidate.isMeshPeer,
            nowMs = System.currentTimeMillis(),
            initialRssiDbm = candidate.rssiDbm
        )
        if (enrolled != null) {
            persistCurrentNodes()
            emitEvaluatedSnapshot()
            _uiState.value = _uiState.value.copy(
                selectedNodeAddress = enrolled.address,
                toastNotice = "Enrolled ${enrolled.unitCode} ${enrolled.label}"
            )
        }
    }

    fun renameNode(address: String, newLabel: String) {
        if (engine.renameDevice(address, newLabel)) {
            persistCurrentNodes()
            emitEvaluatedSnapshot()
        }
    }

    fun removeNode(address: String) {
        if (engine.removeDevice(address)) {
            if (_uiState.value.selectedNodeAddress == address) {
                _uiState.value = _uiState.value.copy(selectedNodeAddress = null)
            }
            persistCurrentNodes()
            emitEvaluatedSnapshot()
        }
    }

    fun updateOperatorIdentity(callsign: String, slotNumber: Int) {
        val cleanCallsign = callsign.trim().ifEmpty { "ALPHA-LEAD" }.take(16)
        val clampedSlot = slotNumber.coerceIn(1, MAX_GROUP_DEVICES)
        _uiState.value = _uiState.value.copy(
            operatorCallsign = cleanCallsign,
            localSlotNumber = clampedSlot
        )
        bleController.setBroadcastConfig(
            enabled = _uiState.value.isBroadcastEnabled,
            localSlot = clampedSlot,
            pttActive = _uiState.value.isPttTransmitting
        )
        viewModelScope.launch { repository.setOperatorIdentity(cleanCallsign, clampedSlot) }
    }

    fun clearToastNotice() {
        _uiState.value = _uiState.value.copy(toastNotice = null)
    }

    private fun emitEvaluatedSnapshot() {
        val now = System.currentTimeMillis()
        val nodes = engine.evaluateNetworkState(now)
        val summary = MeshRadarEngine.summarize(nodes)
        _uiState.value = _uiState.value.copy(
            nodes = nodes,
            summary = summary,
            nowMs = now
        )
    }

    private fun persistCurrentNodes() {
        val snapshot = engine.exportEnrolledRecords()
        viewModelScope.launch {
            repository.saveEnrolledDevices(snapshot)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleController.stopLifecycle()
        runCatching { toneGenerator?.release() }
    }
}
