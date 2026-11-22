package com.example.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshRadarEngineTest {

    @Test
    fun enrollsUpToTwentyUniqueGroupDevices() {
        val engine = MeshRadarEngine()
        val now = 10_000L

        for (i in 1..22) {
            val addr = "AA:BB:CC:DD:EE:%02X".format(i)
            val result = engine.enrollDevice(
                address = addr,
                preferredLabel = "Squad",
                isMeshPeer = true,
                nowMs = now,
                initialRssiDbm = -60
            )
            if (i <= MAX_GROUP_DEVICES) {
                assertNotNull("Device $i should enroll within 20-slot limit", result)
            } else {
                assertNull("Device $i must be rejected above 20-slot cap", result)
            }
        }

        val evaluated = engine.evaluateNetworkState(now + 1_000L)
        assertEquals(20, evaluated.size)
        assertEquals(20, evaluated.map { it.slotNumber }.toSet().size)
        assertEquals(20, evaluated.map { it.label }.toSet().size)
    }

    @Test
    fun deviceReachableViaRelayRemainsGreenWhenDirectLinkIsUnavailable() {
        val engine = MeshRadarEngine(
            directTimeoutMs = 10_000L,
            weakDelayThresholdMs = 5_000L,
            relayRouteTtlMs = 15_000L
        )

        val t0 = 100_000L
        // Enroll #01 (Relay host) and #02 (Target node)
        engine.onDirectObservation(
            address = "11:22:33:44:55:01",
            deviceName = "Relay-One",
            rssiDbm = -58,
            isMeshPeer = true,
            relayedNeighbors = listOf(RelayNeighborReport(neighborSlot = 2, isWeakLink = false)),
            nowMs = t0
        )
        engine.onDirectObservation(
            address = "11:22:33:44:55:02",
            deviceName = "Bravo-Two",
            rssiDbm = -64,
            isMeshPeer = true,
            relayedNeighbors = emptyList(),
            nowMs = t0
        )

        // Advance 11s: #02 direct link has timed out (> 10s), while #01 just refreshed and still relays #02
        val t1 = t0 + 11_000L
        engine.onDirectObservation(
            address = "11:22:33:44:55:01",
            deviceName = "Relay-One",
            rssiDbm = -60,
            isMeshPeer = true,
            relayedNeighbors = listOf(RelayNeighborReport(neighborSlot = 2, isWeakLink = false)),
            nowMs = t1
        )

        val nodes = engine.evaluateNetworkState(t1)
        val node2 = nodes.first { it.slotNumber == 2 }

        assertEquals(DeviceStatus.IN_RANGE, node2.status)
        assertEquals(ConnectionRouteType.RELAY, node2.routeType)
        assertTrue(node2.voiceAvailable)
        assertNull(node2.directRssiDbm)
        assertNotNull(node2.relayPath)
        assertEquals(1, node2.relayPath?.viaSlotNumber)
        assertEquals(2, node2.relayPath?.hopCount)
    }

    @Test
    fun distinguishesWeakDisconnectedUnreachableAndAutoRecoversGreen() {
        val engine = MeshRadarEngine(
            directTimeoutMs = 10_000L,
            weakDelayThresholdMs = 5_000L,
            relayRouteTtlMs = 15_000L,
            weakRssiThresholdDbm = -82
        )

        val t0 = 50_000L
        engine.onDirectObservation(
            address = "AA:00:00:00:00:01",
            deviceName = "Echo-1",
            rssiDbm = -88, // Weak RSSI
            isMeshPeer = true,
            nowMs = t0
        )
        var echo = engine.evaluateNetworkState(t0 + 1_000L).first()
        assertEquals(DeviceStatus.WEAK_UNSTABLE, echo.status)

        // Explicit disconnect -> RED + DISCONNECTED
        engine.onExplicitDisconnect("AA:00:00:00:00:01", t0 + 2_000L)
        echo = engine.evaluateNetworkState(t0 + 2_500L).first()
        assertEquals(DeviceStatus.OUT_OF_RANGE, echo.status)
        assertEquals(LossReason.DISCONNECTED, echo.lossReason)
        assertFalse(echo.voiceAvailable)
        assertEquals(t0, echo.lastSeenMs)

        // Returns with strong signal -> automatically recovers to GREEN
        engine.onDirectObservation(
            address = "AA:00:00:00:00:01",
            deviceName = "Echo-1",
            rssiDbm = -61,
            isMeshPeer = true,
            nowMs = t0 + 4_000L
        )
        echo = engine.evaluateNetworkState(t0 + 4_500L).first()
        assertEquals(DeviceStatus.IN_RANGE, echo.status)
        assertEquals(LossReason.NONE, echo.lossReason)
        assertTrue(echo.voiceAvailable)

        // Heartbeat timeout without explicit disconnect -> RED + UNREACHABLE
        echo = engine.evaluateNetworkState(t0 + 20_000L).first()
        assertEquals(DeviceStatus.OUT_OF_RANGE, echo.status)
        assertEquals(LossReason.UNREACHABLE, echo.lossReason)
    }

    @Test
    fun codecRoundTripsMeshBeaconPayload() {
        val neighbors = listOf(
            RelayNeighborReport(neighborSlot = 3, isWeakLink = false),
            RelayNeighborReport(neighborSlot = 17, isWeakLink = true)
        )
        val encoded = MeshPayloadCodec.encode(
            localSlot = 5,
            heartbeatSeq = 142,
            isPttActive = true,
            isVoiceReady = true,
            neighbors = neighbors
        )
        val decoded = MeshPayloadCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(5, decoded!!.senderSlot)
        assertEquals(142, decoded.heartbeatSeq)
        assertTrue(decoded.isPttActive)
        assertTrue(decoded.isVoiceReady)
        assertEquals(neighbors, decoded.neighbors)
    }
}
