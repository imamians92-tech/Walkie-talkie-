package com.example.mesh

import java.util.UUID

const val MAX_GROUP_DEVICES = 20

/**
 * Custom 128-bit BLE Service UUID for Walkie Talkie Pro Mesh Beacons.
 * Fits compactly in 16-bit/128-bit BLE advertisements with service data.
 */
val WALKIE_MESH_SERVICE_UUID: UUID = UUID.fromString("e7b40001-7c3a-4f1b-9a2e-8c1d00000001")

enum class DeviceStatus(val accessibleLabel: String) {
    IN_RANGE("IN RANGE"),
    WEAK_UNSTABLE("WEAK / UNSTABLE"),
    OUT_OF_RANGE("OUT OF RANGE")
}

enum class ConnectionRouteType(val label: String) {
    DIRECT("Direct Bluetooth"),
    RELAY("Multi-hop Relay"),
    NONE("No Route")
}

enum class LossReason(val label: String) {
    NONE("Connected"),
    DISCONNECTED("Disconnected"),
    UNREACHABLE("Unreachable (Heartbeat Timeout)")
}

enum class SignalQualityTier(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor"),
    UNAVAILABLE("Unavailable")
}

enum class ScanPowerMode(val label: String, val scanWindowMs: Long, val idlePauseMs: Long) {
    BALANCED("Balanced", 6_000L, 4_000L),
    BATTERY_SAVER("Battery Saver", 4_000L, 8_000L)
}

enum class RadarFilter(val label: String) {
    ALL("All"),
    IN_RANGE("In Range"),
    WEAK("Weak"),
    OUT_OF_RANGE("Out of Range"),
    RELAY("Relayed")
}

data class RelayNeighborReport(
    val neighborSlot: Int,
    val isWeakLink: Boolean
)

data class RelayPath(
    val viaSlotNumber: Int,
    val viaLabel: String,
    val hopCount: Int,
    val nextHopRssiDbm: Int?,
    val updatedAtMs: Long,
    val isDegraded: Boolean = false
)

data class RadarDeviceNode(
    val slotNumber: Int,
    val address: String,
    val label: String,
    val isMeshPeer: Boolean,
    val status: DeviceStatus,
    val routeType: ConnectionRouteType,
    val lossReason: LossReason,
    val voiceAvailable: Boolean,
    val directRssiDbm: Int?,
    val signalQualityTier: SignalQualityTier,
    val signalQualityPercent: Int?,
    val packetJitterMs: Long,
    val lastHeartbeatMs: Long?,
    val lastDirectSeenMs: Long?,
    val lastSeenMs: Long?,
    val relayPath: RelayPath?,
    val isTransmittingPtt: Boolean,
    val heartbeatSeq: Int,
    val relayedNeighborSlots: List<RelayNeighborReport> = emptyList()
) {
    val unitCode: String
        get() = "#${slotNumber.toString().padStart(2, '0')}"
}

data class EnrolledDeviceRecord(
    val slotNumber: Int,
    val address: String,
    val label: String,
    val isMeshPeer: Boolean,
    val lastSeenMs: Long?
)

data class DiscoveredBluetoothCandidate(
    val address: String,
    val name: String,
    val rssiDbm: Int?,
    val isBonded: Boolean,
    val isMeshPeer: Boolean,
    val lastSeenMs: Long,
    val meshPeerSlot: Int? = null
)

data class MeshHealthSummary(
    val inRangeCount: Int = 0,
    val weakCount: Int = 0,
    val outOfRangeCount: Int = 0,
    val relayCount: Int = 0,
    val enrolledCount: Int = 0,
    val maxCapacity: Int = MAX_GROUP_DEVICES
)

data class RadioChannel(
    val number: Int,
    val name: String,
    val bandLabel: String
)

val DEFAULT_RADIO_CHANNELS = listOf(
    RadioChannel(1, "ALPHA", "CH 01 • MESH PRI"),
    RadioChannel(2, "BRAVO", "CH 02 • TAC RELAY"),
    RadioChannel(3, "CHARLIE", "CH 03 • FIELD OPS"),
    RadioChannel(4, "RESCUE", "CH 04 • EMERGENCY")
)
