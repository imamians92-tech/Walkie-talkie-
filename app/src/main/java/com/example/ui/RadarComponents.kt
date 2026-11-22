package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mesh.BluetoothHardwareState
import com.example.mesh.ConnectionRouteType
import com.example.mesh.DeviceStatus
import com.example.mesh.LossReason
import com.example.mesh.RadarDeviceNode
import com.example.ui.theme.RadarTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BluetoothReadinessBanner(
    hardwareState: BluetoothHardwareState,
    onRequestPermissions: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onRetryHardware: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val radarColors = RadarTheme.colors
    if (hardwareState.isBleSupported &&
        hardwareState.hasRequiredPermissions &&
        hardwareState.isBluetoothEnabled &&
        hardwareState.errorNotice == null
    ) {
        return
    }

    val (icon, title, body, buttonText, onClick) = when {
        !hardwareState.isBleSupported -> BannerSpec(
            icon = Icons.Rounded.BluetoothDisabled,
            title = "Bluetooth LE Radio Unavailable",
            body = "This hardware lacks a Bluetooth Low Energy adapter. Live peer scanning requires a physical BLE radio.",
            buttonLabel = null,
            action = null
        )

        !hardwareState.hasRequiredPermissions -> BannerSpec(
            icon = Icons.Rounded.Bluetooth,
            title = "Nearby Bluetooth Permission Required",
            body = "Grant Nearby Devices access so Walkie Talkie Pro can scan real Bluetooth mesh peers and read RSSI signal telemetry.",
            buttonLabel = "Grant Permission",
            action = onRequestPermissions
        )

        !hardwareState.isBluetoothEnabled -> BannerSpec(
            icon = Icons.Rounded.BluetoothDisabled,
            title = "Bluetooth Radio Is Off",
            body = "Turn on Bluetooth to discover group phones, evaluate relay routes, and broadcast your node heartbeat.",
            buttonLabel = "Enable Bluetooth",
            action = onRequestEnableBluetooth
        )

        else -> BannerSpec(
            icon = Icons.Rounded.WarningAmber,
            title = "Bluetooth Mesh Hardware Notice",
            body = hardwareState.errorNotice ?: "Bluetooth scan/beacon interrupted.",
            buttonLabel = if (onRetryHardware != null) "Retry Radio" else null,
            action = onRetryHardware
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = radarColors.weakUnstableContainer,
        border = BorderStroke(1.dp, radarColors.weakUnstable.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = radarColors.weakUnstable,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (buttonText != null && onClick != null) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = radarColors.weakUnstable,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("bt_banner_action_button")
                    ) {
                        Text(buttonText, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private data class BannerSpec(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val buttonLabel: String?,
    val action: (() -> Unit)?
)

@Composable
fun RadarDeviceCard(
    node: RadarDeviceNode,
    isSelected: Boolean,
    nowMs: Long,
    onSelect: () -> Unit,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = RadarTheme.colors

    val statusColor: Color
    val statusContainer: Color
    val statusIcon: ImageVector
    val statusLabel: String

    when (node.status) {
        DeviceStatus.IN_RANGE -> {
            statusColor = radarColors.inRange
            statusContainer = radarColors.inRangeContainer
            statusIcon = if (node.routeType == ConnectionRouteType.RELAY) {
                Icons.Rounded.Hub
            } else {
                Icons.Rounded.CheckCircle
            }
            statusLabel = "IN RANGE"
        }

        DeviceStatus.WEAK_UNSTABLE -> {
            statusColor = radarColors.weakUnstable
            statusContainer = radarColors.weakUnstableContainer
            statusIcon = Icons.Rounded.WarningAmber
            statusLabel = "WEAK / UNSTABLE"
        }

        DeviceStatus.OUT_OF_RANGE -> {
            statusColor = radarColors.outOfRange
            statusContainer = radarColors.outOfRangeContainer
            statusIcon = Icons.Rounded.LinkOff
            statusLabel = "OUT OF RANGE"
        }
    }

    val routeBadgeText = when (node.routeType) {
        ConnectionRouteType.DIRECT -> "DIRECT BLE"
        ConnectionRouteType.RELAY -> {
            val relay = node.relayPath
            if (relay != null) "RELAY ${relay.hopCount}-HOP VIA #${relay.viaSlotNumber.toString().padStart(2, '0')}"
            else "RELAY ROUTE"
        }
        ConnectionRouteType.NONE -> {
            if (node.lossReason == LossReason.DISCONNECTED) "DISCONNECTED" else "UNREACHABLE"
        }
    }

    val routeColor = when (node.routeType) {
        ConnectionRouteType.DIRECT -> statusColor
        ConnectionRouteType.RELAY -> radarColors.relayAccent
        ConnectionRouteType.NONE -> radarColors.outOfRange
    }

    val accessibilitySummary = buildString {
        append("${node.unitCode} ${node.label}, status $statusLabel, ")
        append("route $routeBadgeText, ")
        if (node.directRssiDbm != null) append("RSSI ${node.directRssiDbm} dBm, ")
        append(if (node.voiceAvailable) "voice communication available" else "voice communication unavailable")
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .semantics { contentDescription = accessibilitySummary }
            .testTag("device_card_${node.slotNumber}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) statusColor else radarColors.cardBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unique Device Number Pill (#01..#20)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusContainer)
                        .border(1.5.dp, statusColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = node.unitCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = node.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (node.isTransmittingPtt && node.status != DeviceStatus.OUT_OF_RANGE) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = radarColors.pttAccent.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = "Transmitting voice",
                                        tint = radarColors.pttAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = "TX",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = radarColors.pttAccent
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = node.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = radarColors.mutedText
                    )
                }

                // Accessible Status Pill (Icon + Text Label + Color)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusContainer,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = statusLabel,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onInspect,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Inspect or rename ${node.label}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Telemetry Row: Route Path, Signal Quality / RSSI, Voice State
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Route Pill (Direct vs Relay vs Disconnected/Unreachable)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = routeColor.copy(alpha = 0.13f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (node.routeType) {
                                ConnectionRouteType.DIRECT -> Icons.Rounded.Bluetooth
                                ConnectionRouteType.RELAY -> Icons.Rounded.Hub
                                ConnectionRouteType.NONE -> Icons.Rounded.LinkOff
                            },
                            contentDescription = null,
                            tint = routeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = routeBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = routeColor
                        )
                    }
                }

                // Signal Quality & RSSI
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SignalCellularAlt,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        val signalText = when {
                            node.directRssiDbm != null ->
                                "${node.signalQualityTier.label} (${node.directRssiDbm} dBm)"
                            node.routeType == ConnectionRouteType.RELAY ->
                                "Relay ${node.signalQualityTier.label}"
                            else -> "Signal N/A"
                        }
                        Text(
                            text = signalText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Voice readiness indicator
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (node.voiceAvailable) radarColors.inRangeContainer else radarColors.outOfRangeContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (node.voiceAvailable) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                            contentDescription = null,
                            tint = if (node.voiceAvailable) radarColors.inRange else radarColors.outOfRange,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (node.voiceAvailable) "VOICE" else "NO VOICE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (node.voiceAvailable) radarColors.inRange else radarColors.outOfRange
                        )
                    }
                }
            }

            // Additional context for Relay fallback or Disconnected Last-Seen time
            if (node.routeType == ConnectionRouteType.RELAY && node.relayPath != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Direct BT unavailable • Reachable via ${node.relayPath.viaLabel} (${node.relayPath.hopCount} hops)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = radarColors.relayAccent
                )
            } else if (node.status == DeviceStatus.WEAK_UNSTABLE) {
                Spacer(Modifier.height(6.dp))
                val reasonDetail = buildString {
                    append("Unstable link")
                    if (node.packetJitterMs > 1_500L) {
                        append(" • Packet delay/jitter ${node.packetJitterMs / 1000.0}s")
                    }
                    if (node.directRssiDbm != null && node.directRssiDbm < -82) {
                        append(" • Weak RSSI ${node.directRssiDbm} dBm")
                    }
                }
                Text(
                    text = reasonDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = radarColors.weakUnstable
                )
            } else if (node.status == DeviceStatus.OUT_OF_RANGE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${node.lossReason.label} • Last seen: ${formatLastSeen(node.lastSeenMs, nowMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = radarColors.outOfRange
                )
            }
        }
    }
}

fun formatLastSeen(lastSeenMs: Long?, nowMs: Long): String {
    if (lastSeenMs == null) return "Never detected"
    val diffSec = ((nowMs - lastSeenMs).coerceAtLeast(0L)) / 1_000L
    val rel = when {
        diffSec < 60L -> "${diffSec}s ago"
        diffSec < 3_600L -> "${diffSec / 60L}m ${diffSec % 60L}s ago"
        else -> "${diffSec / 3_600L}h ${(diffSec % 3_600L) / 60L}m ago"
    }
    val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastSeenMs))
    return "$clock ($rel)"
}
