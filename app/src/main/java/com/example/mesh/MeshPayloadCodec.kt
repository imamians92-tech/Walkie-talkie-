package com.example.mesh

/**
 * Compact binary codec for Walkie Talkie Pro BLE Mesh Service Data.
 *
 * Frame layout (fits inside standard 31-byte legacy BLE advertisement):
 * - Byte 0: Protocol version (high nibble = 1) + flags (bit 0 = PTT active, bit 1 = voice ready)
 * - Byte 1: Sender Node Slot Number (1..20)
 * - Byte 2: Heartbeat sequence (0..255 wrapping)
 * - Byte 3: Relayed neighbor count N (0..8)
 * - Bytes 4..(4 + 2*N - 1): Neighbor pairs [slotNumber (1..20), flags (bit 0 = weak link)]
 */
object MeshPayloadCodec {

    private const val PROTOCOL_VERSION = 0x10
    private const val FLAG_PTT_ACTIVE = 0x01
    private const val FLAG_VOICE_READY = 0x02
    private const val MAX_ADVERTISED_NEIGHBORS = 8

    data class DecodedMeshBeacon(
        val senderSlot: Int,
        val heartbeatSeq: Int,
        val isPttActive: Boolean,
        val isVoiceReady: Boolean,
        val neighbors: List<RelayNeighborReport>
    )

    fun encode(
        localSlot: Int,
        heartbeatSeq: Int,
        isPttActive: Boolean,
        isVoiceReady: Boolean,
        neighbors: List<RelayNeighborReport>
    ): ByteArray {
        val clampedNeighbors = neighbors
            .filter { it.neighborSlot in 1..MAX_GROUP_DEVICES && it.neighborSlot != localSlot }
            .take(MAX_ADVERTISED_NEIGHBORS)

        var header = PROTOCOL_VERSION
        if (isPttActive) header = header or FLAG_PTT_ACTIVE
        if (isVoiceReady) header = header or FLAG_VOICE_READY

        val bytes = ByteArray(4 + clampedNeighbors.size * 2)
        bytes[0] = header.toByte()
        bytes[1] = localSlot.coerceIn(1, MAX_GROUP_DEVICES).toByte()
        bytes[2] = (heartbeatSeq and 0xFF).toByte()
        bytes[3] = clampedNeighbors.size.toByte()

        clampedNeighbors.forEachIndexed { index, report ->
            val offset = 4 + index * 2
            bytes[offset] = report.neighborSlot.toByte()
            bytes[offset + 1] = if (report.isWeakLink) 0x01 else 0x00
        }
        return bytes
    }

    fun decode(data: ByteArray?): DecodedMeshBeacon? {
        if (data == null || data.size < 4) return null
        val header = data[0].toInt() and 0xFF
        val version = header and 0xF0
        if (version != PROTOCOL_VERSION) return null

        val senderSlot = data[1].toInt() and 0xFF
        if (senderSlot !in 1..MAX_GROUP_DEVICES) return null

        val seq = data[2].toInt() and 0xFF
        val count = (data[3].toInt() and 0xFF).coerceAtMost(MAX_ADVERTISED_NEIGHBORS)
        if (data.size < 4 + count * 2) return null

        val neighbors = ArrayList<RelayNeighborReport>(count)
        for (i in 0 until count) {
            val offset = 4 + i * 2
            val nSlot = data[offset].toInt() and 0xFF
            val flags = data[offset + 1].toInt() and 0xFF
            if (nSlot in 1..MAX_GROUP_DEVICES) {
                neighbors.add(
                    RelayNeighborReport(
                        neighborSlot = nSlot,
                        isWeakLink = (flags and 0x01) != 0
                    )
                )
            }
        }

        return DecodedMeshBeacon(
            senderSlot = senderSlot,
            heartbeatSeq = seq,
            isPttActive = (header and FLAG_PTT_ACTIVE) != 0,
            isVoiceReady = (header and FLAG_VOICE_READY) != 0,
            neighbors = neighbors
        )
    }
}
