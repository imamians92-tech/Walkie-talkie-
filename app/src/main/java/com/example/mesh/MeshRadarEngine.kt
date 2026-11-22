package com.example.mesh

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure, deterministic Bluetooth mesh reachability & link-health engine.
 *
 * Handles:
 * - Up to 20 enrolled group devices with unique slot numbers (#01..#20) & unique labels.
 * - Direct link health via RSSI signal quality + heartbeat delay/jitter detection.
 * - Multi-hop relay route resolution: a node whose direct Bluetooth link is unavailable
 *   remains IN_RANGE (GREEN) whenever reachable via a valid relay neighbor route.
 * - Automatic recovery back to GREEN when connectivity returns.
 * - Explicit distinction between DISCONNECTED, UNREACHABLE, and WEAK_UNSTABLE.
 */
class MeshRadarEngine(
    private val directTimeoutMs: Long = DEFAULT_DIRECT_TIMEOUT_MS,
    private val weakDelayThresholdMs: Long = DEFAULT_WEAK_DELAY_MS,
    private val relayRouteTtlMs: Long = DEFAULT_RELAY_TTL_MS,
    private val weakRssiThresholdDbm: Int = DEFAULT_WEAK_RSSI_DBM,
    private val unstableSpreadDb: Int = DEFAULT_RSSI_SPREAD_UNSTABLE_DB,
    private val unstableJitterMs: Long = DEFAULT_UNSTABLE_JITTER_MS
) {

    private data class MutableNodeState(
        val slotNumber: Int,
        val address: String,
        var label: String,
        var isMeshPeer: Boolean,
        var directRssiDbm: Int? = null,
        val rssiWindow: ArrayDeque<Int> = ArrayDeque(),
        val interArrivalIntervals: ArrayDeque<Long> = ArrayDeque(),
        var lastDirectSeenMs: Long? = null,
        var lastHeartbeatMs: Long? = null,
        var lastSeenMs: Long? = null,
        var packetJitterMs: Long = 0L,
        var heartbeatSeq: Int = 0,
        var isTransmittingPtt: Boolean = false,
        var explicitlyDisconnected: Boolean = false,
        var isProfileConnected: Boolean = false,
        var reportedNeighbors: List<RelayNeighborReport> = emptyList(),
        var reportedNeighborsAtMs: Long? = null
    )

    private val nodesByAddress = LinkedHashMap<String, MutableNodeState>()

    @Synchronized
    fun loadEnrolledDevices(records: List<EnrolledDeviceRecord>) {
        nodesByAddress.clear()
        records
            .sortedBy { it.slotNumber }
            .take(MAX_GROUP_DEVICES)
            .forEach { rec ->
                val normalizedAddr = rec.address.uppercase()
                val uniqueLabel = ensureUniqueLabel(rec.label, excludeAddress = null)
                nodesByAddress[normalizedAddr] = MutableNodeState(
                    slotNumber = rec.slotNumber,
                    address = normalizedAddr,
                    label = uniqueLabel,
                    isMeshPeer = rec.isMeshPeer,
                    lastSeenMs = rec.lastSeenMs
                )
            }
    }

    @Synchronized
    fun exportEnrolledRecords(): List<EnrolledDeviceRecord> {
        return nodesByAddress.values
            .sortedBy { it.slotNumber }
            .map {
                EnrolledDeviceRecord(
                    slotNumber = it.slotNumber,
                    address = it.address,
                    label = it.label,
                    isMeshPeer = it.isMeshPeer,
                    lastSeenMs = it.lastSeenMs
                )
            }
    }

    @Synchronized
    fun enrollDevice(
        address: String,
        preferredLabel: String,
        isMeshPeer: Boolean,
        nowMs: Long,
        initialRssiDbm: Int? = null
    ): RadarDeviceNode? {
        val normalizedAddr = address.uppercase()
        val existing = nodesByAddress[normalizedAddr]
        if (existing != null) {
            if (isMeshPeer) existing.isMeshPeer = true
            if (initialRssiDbm != null) {
                recordDirectPacket(existing, initialRssiDbm, nowMs, existing.heartbeatSeq, existing.isTransmittingPtt, existing.reportedNeighbors)
            }
            return evaluateSingle(existing, nowMs)
        }

        if (nodesByAddress.size >= MAX_GROUP_DEVICES) return null
        val usedSlots = nodesByAddress.values.map { it.slotNumber }.toSet()
        val freeSlot = (1..MAX_GROUP_DEVICES).firstOrNull { it !in usedSlots } ?: return null
        val cleanBase = preferredLabel.trim().ifEmpty { "Unit-${freeSlot.toString().padStart(2, '0')}" }
        val uniqueLabel = ensureUniqueLabel(cleanBase, excludeAddress = null)

        val state = MutableNodeState(
            slotNumber = freeSlot,
            address = normalizedAddr,
            label = uniqueLabel,
            isMeshPeer = isMeshPeer
        )
        if (initialRssiDbm != null) {
            recordDirectPacket(state, initialRssiDbm, nowMs, 0, false, emptyList())
        }
        nodesByAddress[normalizedAddr] = state
        return evaluateSingle(state, nowMs)
    }

    @Synchronized
    fun removeDevice(address: String): Boolean {
        return nodesByAddress.remove(address.uppercase()) != null
    }

    @Synchronized
    fun renameDevice(address: String, newLabel: String): Boolean {
        val node = nodesByAddress[address.uppercase()] ?: return false
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty()) return false
        node.label = ensureUniqueLabel(trimmed, excludeAddress = node.address)
        return true
    }

    @Synchronized
    fun hasDevice(address: String): Boolean = nodesByAddress.containsKey(address.uppercase())

    @Synchronized
    fun enrolledCount(): Int = nodesByAddress.size

    @Synchronized
    fun onDirectObservation(
        address: String,
        deviceName: String?,
        rssiDbm: Int?,
        isMeshPeer: Boolean,
        isPttActive: Boolean = false,
        heartbeatSeq: Int = 0,
        relayedNeighbors: List<RelayNeighborReport> = emptyList(),
        autoEnrollIfMeshPeer: Boolean = true,
        nowMs: Long
    ) {
        val normalizedAddr = address.uppercase()
        var state = nodesByAddress[normalizedAddr]

        if (state == null) {
            if (!isMeshPeer || !autoEnrollIfMeshPeer || nodesByAddress.size >= MAX_GROUP_DEVICES) {
                return
            }
            val usedSlots = nodesByAddress.values.map { it.slotNumber }.toSet()
            val freeSlot = (1..MAX_GROUP_DEVICES).firstOrNull { it !in usedSlots } ?: return
            val candidateName = deviceName?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Mesh-${freeSlot.toString().padStart(2, '0')}"
            state = MutableNodeState(
                slotNumber = freeSlot,
                address = normalizedAddr,
                label = ensureUniqueLabel(candidateName, excludeAddress = null),
                isMeshPeer = true
            )
            nodesByAddress[normalizedAddr] = state
        }

        if (isMeshPeer) state.isMeshPeer = true
        recordDirectPacket(state, rssiDbm, nowMs, heartbeatSeq, isPttActive, relayedNeighbors)
    }

    @Synchronized
    fun onProfileConnectedState(address: String, isConnected: Boolean, nowMs: Long) {
        val state = nodesByAddress[address.uppercase()] ?: return
        state.isProfileConnected = isConnected
        if (isConnected) {
            state.explicitlyDisconnected = false
            state.lastDirectSeenMs = nowMs
            state.lastHeartbeatMs = nowMs
            state.lastSeenMs = nowMs
        } else {
            state.explicitlyDisconnected = true
            state.directRssiDbm = null
        }
    }

    @Synchronized
    fun onExplicitDisconnect(address: String, nowMs: Long) {
        val state = nodesByAddress[address.uppercase()] ?: return
        state.explicitlyDisconnected = true
        state.isProfileConnected = false
        state.directRssiDbm = null
        state.isTransmittingPtt = false
        state.reportedNeighbors = emptyList()
        state.reportedNeighborsAtMs = null
        if (state.lastSeenMs == null) {
            state.lastSeenMs = nowMs
        }
    }

    /**
     * Builds the list of currently reachable direct neighbors to advertise to other Walkie Talkie Pro nodes.
     */
    @Synchronized
    fun buildLocalRelayAdvertisementNeighbors(nowMs: Long): List<RelayNeighborReport> {
        return nodesByAddress.values
            .filter { node ->
                val lastDirect = node.lastDirectSeenMs
                !node.explicitlyDisconnected &&
                    lastDirect != null &&
                    (nowMs - lastDirect) <= directTimeoutMs
            }
            .sortedBy { it.slotNumber }
            .map { node ->
                val directWeak = isDirectWeak(node, nowMs)
                RelayNeighborReport(
                    neighborSlot = node.slotNumber,
                    isWeakLink = directWeak
                )
            }
            .take(8)
    }

    @Synchronized
    fun evaluateNetworkState(nowMs: Long): List<RadarDeviceNode> {
        if (nodesByAddress.isEmpty()) return emptyList()

        val allNodes = nodesByAddress.values.sortedBy { it.slotNumber }
        val bySlot = allNodes.associateBy { it.slotNumber }

        // Step 1: Evaluate direct reachability for every node
        val directHealthySet = HashSet<Int>()
        val directWeakSet = HashSet<Int>()
        val directReachableAtMs = HashMap<Int, Boolean>()

        for (node in allNodes) {
            val lastDirect = node.lastDirectSeenMs
            val isDirectAlive = !node.explicitlyDisconnected &&
                ((lastDirect != null && (nowMs - lastDirect) <= directTimeoutMs) || node.isProfileConnected)

            directReachableAtMs[node.slotNumber] = isDirectAlive
            if (isDirectAlive) {
                if (isDirectWeak(node, nowMs)) {
                    directWeakSet.add(node.slotNumber)
                } else {
                    directHealthySet.add(node.slotNumber)
                }
            } else {
                // Direct link expired or disconnected
                node.directRssiDbm = null
                node.isTransmittingPtt = false
            }
        }

        // Step 2: Multi-hop BFS for relay route reachability
        // Any reachable node whose neighbor table is fresh within relayRouteTtlMs can relay packets
        // to its reported neighbors (and transitively across hops).
        val relayBestRoute = HashMap<Int, RelayPath>()
        val queue = ArrayDeque<Int>()

        for (node in allNodes) {
            if (directHealthySet.contains(node.slotNumber) || directWeakSet.contains(node.slotNumber)) {
                queue.addLast(node.slotNumber)
            }
        }

        while (queue.isNotEmpty()) {
            val currentSlot = queue.removeFirst()
            val currentNode = bySlot[currentSlot] ?: continue
            val reportTime = currentNode.reportedNeighborsAtMs ?: continue
            if ((nowMs - reportTime) > relayRouteTtlMs) continue

            val currentRelay = relayBestRoute[currentSlot]
            val currentHopCount = if (directReachableAtMs[currentSlot] == true) 1 else (currentRelay?.hopCount ?: continue)
            if (currentHopCount >= 4) continue // Cap mesh hop depth at 4 for loop prevention

            val currentIsDegraded = if (directReachableAtMs[currentSlot] == true) {
                directWeakSet.contains(currentSlot)
            } else {
                currentRelay?.isDegraded ?: true
            }

            for (report in currentNode.reportedNeighbors) {
                val targetSlot = report.neighborSlot
                if (targetSlot == currentSlot) continue
                val targetNode = bySlot[targetSlot] ?: continue
                if (directReachableAtMs[targetSlot] == true) continue // Already directly reachable

                val candidateHops = currentHopCount + 1
                val candidateDegraded = currentIsDegraded || report.isWeakLink
                val existing = relayBestRoute[targetSlot]

                val isBetter = existing == null ||
                    (!candidateDegraded && existing.isDegraded) ||
                    (candidateDegraded == existing.isDegraded && candidateHops < existing.hopCount)

                if (isBetter) {
                    val rootViaSlot = if (directReachableAtMs[currentSlot] == true) currentSlot else currentRelay!!.viaSlotNumber
                    val rootViaLabel = bySlot[rootViaSlot]?.label ?: "#${rootViaSlot.toString().padStart(2, '0')}"
                    val rootRssi = bySlot[rootViaSlot]?.directRssiDbm

                    relayBestRoute[targetSlot] = RelayPath(
                        viaSlotNumber = rootViaSlot,
                        viaLabel = rootViaLabel,
                        hopCount = candidateHops,
                        nextHopRssiDbm = rootRssi,
                        updatedAtMs = reportTime,
                        isDegraded = candidateDegraded
                    )
                    // Update target's lastSeenMs because we have verified active mesh presence via relay
                    val currentSeen = targetNode.lastSeenMs ?: 0L
                    if (reportTime > currentSeen) {
                        targetNode.lastSeenMs = reportTime
                    }
                    queue.addLast(targetSlot)
                }
            }
        }

        // Step 3: Materialize final RadarDeviceNode snapshots
        return allNodes.map { node ->
            val directAlive = directReachableAtMs[node.slotNumber] == true
            val relayPath = relayBestRoute[node.slotNumber]

            when {
                directAlive -> {
                    val weak = directWeakSet.contains(node.slotNumber)
                    val rssi = node.directRssiDbm
                    val tier = classifyRssiTier(rssi, node.isProfileConnected)
                    val pct = rssiToQualityPercent(rssi, node.isProfileConnected)
                    RadarDeviceNode(
                        slotNumber = node.slotNumber,
                        address = node.address,
                        label = node.label,
                        isMeshPeer = node.isMeshPeer,
                        status = if (weak) DeviceStatus.WEAK_UNSTABLE else DeviceStatus.IN_RANGE,
                        routeType = ConnectionRouteType.DIRECT,
                        lossReason = LossReason.NONE,
                        voiceAvailable = true,
                        directRssiDbm = rssi,
                        signalQualityTier = tier,
                        signalQualityPercent = pct,
                        packetJitterMs = node.packetJitterMs,
                        lastHeartbeatMs = node.lastHeartbeatMs,
                        lastDirectSeenMs = node.lastDirectSeenMs,
                        lastSeenMs = node.lastSeenMs,
                        relayPath = null,
                        isTransmittingPtt = node.isTransmittingPtt,
                        heartbeatSeq = node.heartbeatSeq,
                        relayedNeighborSlots = node.reportedNeighbors
                    )
                }

                relayPath != null -> {
                    // Direct Bluetooth link is unavailable, but device is reachable via relay route!
                    // Must remain IN_RANGE (GREEN) unless relay link itself is weak/degraded.
                    val status = if (relayPath.isDegraded) {
                        DeviceStatus.WEAK_UNSTABLE
                    } else {
                        DeviceStatus.IN_RANGE
                    }
                    RadarDeviceNode(
                        slotNumber = node.slotNumber,
                        address = node.address,
                        label = node.label,
                        isMeshPeer = node.isMeshPeer,
                        status = status,
                        routeType = ConnectionRouteType.RELAY,
                        lossReason = LossReason.NONE,
                        voiceAvailable = true,
                        directRssiDbm = null,
                        signalQualityTier = if (relayPath.isDegraded) SignalQualityTier.POOR else SignalQualityTier.GOOD,
                        signalQualityPercent = if (relayPath.isDegraded) 38 else 76,
                        packetJitterMs = node.packetJitterMs,
                        lastHeartbeatMs = relayPath.updatedAtMs,
                        lastDirectSeenMs = node.lastDirectSeenMs,
                        lastSeenMs = node.lastSeenMs ?: relayPath.updatedAtMs,
                        relayPath = relayPath,
                        isTransmittingPtt = false,
                        heartbeatSeq = node.heartbeatSeq,
                        relayedNeighborSlots = node.reportedNeighbors
                    )
                }

                else -> {
                    // Neither direct nor relay route available -> RED (OUT OF RANGE)
                    val lossReason = if (node.explicitlyDisconnected) {
                        LossReason.DISCONNECTED
                    } else {
                        LossReason.UNREACHABLE
                    }
                    RadarDeviceNode(
                        slotNumber = node.slotNumber,
                        address = node.address,
                        label = node.label,
                        isMeshPeer = node.isMeshPeer,
                        status = DeviceStatus.OUT_OF_RANGE,
                        routeType = ConnectionRouteType.NONE,
                        lossReason = lossReason,
                        voiceAvailable = false,
                        directRssiDbm = null,
                        signalQualityTier = SignalQualityTier.UNAVAILABLE,
                        signalQualityPercent = null,
                        packetJitterMs = node.packetJitterMs,
                        lastHeartbeatMs = node.lastHeartbeatMs,
                        lastDirectSeenMs = node.lastDirectSeenMs,
                        lastSeenMs = node.lastSeenMs,
                        relayPath = null,
                        isTransmittingPtt = false,
                        heartbeatSeq = node.heartbeatSeq,
                        relayedNeighborSlots = emptyList()
                    )
                }
            }
        }
    }

    private fun evaluateSingle(node: MutableNodeState, nowMs: Long): RadarDeviceNode {
        return evaluateNetworkState(nowMs).first { it.address == node.address }
    }

    private fun recordDirectPacket(
        state: MutableNodeState,
        rssiDbm: Int?,
        nowMs: Long,
        heartbeatSeq: Int,
        isPttActive: Boolean,
        relayedNeighbors: List<RelayNeighborReport>
    ) {
        val prevDirect = state.lastDirectSeenMs
        if (prevDirect != null && nowMs >= prevDirect) {
            val delta = nowMs - prevDirect
            if (state.interArrivalIntervals.size >= 5) state.interArrivalIntervals.removeFirst()
            state.interArrivalIntervals.addLast(delta)
            state.packetJitterMs = computeJitter(state.interArrivalIntervals)
        }

        if (rssiDbm != null) {
            state.directRssiDbm = rssiDbm
            if (state.rssiWindow.size >= 5) state.rssiWindow.removeFirst()
            state.rssiWindow.addLast(rssiDbm)
        }

        state.explicitlyDisconnected = false
        state.lastDirectSeenMs = nowMs
        state.lastHeartbeatMs = nowMs
        state.lastSeenMs = nowMs
        state.heartbeatSeq = heartbeatSeq
        state.isTransmittingPtt = isPttActive
        if (relayedNeighbors.isNotEmpty() || state.isMeshPeer) {
            state.reportedNeighbors = relayedNeighbors
            state.reportedNeighborsAtMs = nowMs
        }
    }

    private fun isDirectWeak(node: MutableNodeState, nowMs: Long): Boolean {
        val lastDirect = node.lastDirectSeenMs ?: return !node.isProfileConnected
        val ageMs = nowMs - lastDirect
        if (ageMs > weakDelayThresholdMs && !node.isProfileConnected) return true

        val rssi = node.directRssiDbm
        if (rssi != null && rssi < weakRssiThresholdDbm) return true

        if (node.packetJitterMs > unstableJitterMs) return true

        if (node.rssiWindow.size >= 3) {
            val minRssi = node.rssiWindow.min()
            val maxRssi = node.rssiWindow.max()
            if ((maxRssi - minRssi) > unstableSpreadDb) return true
        }
        return false
    }

    private fun computeJitter(intervals: ArrayDeque<Long>): Long {
        if (intervals.size < 2) return 0L
        val avg = intervals.average()
        val meanAbsDev = intervals.map { abs(it - avg) }.average()
        return meanAbsDev.roundToInt().toLong()
    }

    private fun ensureUniqueLabel(baseLabel: String, excludeAddress: String?): String {
        val existingLabels = nodesByAddress.values
            .filter { it.address != excludeAddress }
            .map { it.label.lowercase() }
            .toSet()

        if (baseLabel.lowercase() !in existingLabels) return baseLabel
        var suffix = 2
        while ("$baseLabel ($suffix)".lowercase() in existingLabels) {
            suffix++
        }
        return "$baseLabel ($suffix)"
    }

    companion object {
        const val DEFAULT_DIRECT_TIMEOUT_MS = 12_000L
        const val DEFAULT_WEAK_DELAY_MS = 5_500L
        const val DEFAULT_RELAY_TTL_MS = 15_000L
        const val DEFAULT_WEAK_RSSI_DBM = -82
        const val DEFAULT_RSSI_SPREAD_UNSTABLE_DB = 18
        const val DEFAULT_UNSTABLE_JITTER_MS = 2_800L

        fun classifyRssiTier(rssiDbm: Int?, isProfileConnected: Boolean): SignalQualityTier {
            if (rssiDbm == null) {
                return if (isProfileConnected) SignalQualityTier.GOOD else SignalQualityTier.UNAVAILABLE
            }
            return when {
                rssiDbm >= -62 -> SignalQualityTier.EXCELLENT
                rssiDbm >= -74 -> SignalQualityTier.GOOD
                rssiDbm >= -82 -> SignalQualityTier.FAIR
                else -> SignalQualityTier.POOR
            }
        }

        fun rssiToQualityPercent(rssiDbm: Int?, isProfileConnected: Boolean): Int? {
            if (rssiDbm == null) return if (isProfileConnected) 80 else null
            // Normalized signal quality indicator (not physical distance) from -100 dBm (0%) to -45 dBm (100%)
            val clamped = rssiDbm.coerceIn(-100, -45)
            return (((clamped + 100) / 55.0) * 100.0).roundToInt().coerceIn(5, 100)
        }

        fun summarize(nodes: List<RadarDeviceNode>): MeshHealthSummary {
            return MeshHealthSummary(
                inRangeCount = nodes.count { it.status == DeviceStatus.IN_RANGE },
                weakCount = nodes.count { it.status == DeviceStatus.WEAK_UNSTABLE },
                outOfRangeCount = nodes.count { it.status == DeviceStatus.OUT_OF_RANGE },
                relayCount = nodes.count { it.routeType == ConnectionRouteType.RELAY },
                enrolledCount = nodes.size,
                maxCapacity = MAX_GROUP_DEVICES
            )
        }
    }
}
