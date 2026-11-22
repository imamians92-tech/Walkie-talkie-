package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.mesh.MAX_GROUP_DEVICES
import com.example.mesh.RadioChannel
import com.example.ui.theme.RadarTheme

@Composable
fun DashboardScreen(
    uiState: WalkieRadarUiState,
    onOpenRadar: () -> Unit,
    onSelectChannel: (Int) -> Unit,
    onSetPttTransmitting: (Boolean) -> Unit,
    onToggleBroadcast: (Boolean) -> Unit,
    onUpdateOperatorIdentity: (String, Int) -> Unit,
    onRefreshRadio: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = RadarTheme.colors
    val haptics = LocalHapticFeedback.current
    var showIdentityDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideLayout = maxWidth >= 720.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 980.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Brand & Editable Local Node Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showIdentityDialog = true }
                                .padding(vertical = 4.dp)
                                .testTag("operator_identity_button")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = radarColors.inRangeContainer,
                                border = BorderStroke(1.dp, radarColors.inRange.copy(alpha = 0.4f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Radio,
                                    contentDescription = null,
                                    tint = radarColors.inRange,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.dashboard_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "UNIT #${uiState.localSlotNumber.toString().padStart(2, '0')} • ${uiState.operatorCallsign}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = radarColors.relayAccent
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit local callsign and unit slot",
                                        tint = radarColors.relayAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        // Live BT Radio Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (uiState.hardwareState.isBluetoothEnabled) {
                                radarColors.inRangeContainer
                            } else {
                                radarColors.outOfRangeContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bluetooth,
                                    contentDescription = null,
                                    tint = if (uiState.hardwareState.isBluetoothEnabled) {
                                        radarColors.inRange
                                    } else {
                                        radarColors.outOfRange
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.hardwareState.isBluetoothEnabled) "BT ON" else "BT OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.hardwareState.isBluetoothEnabled) {
                                        radarColors.inRange
                                    } else {
                                        radarColors.outOfRange
                                    }
                                )
                            }
                        }
                    }

                    BluetoothReadinessBanner(
                        hardwareState = uiState.hardwareState,
                        onRequestPermissions = onRequestPermissions,
                        onRequestEnableBluetooth = onRequestEnableBluetooth,
                        onRetryHardware = onRefreshRadio
                    )

                    // Dedicated RADAR Button prominently placed on Main Dashboard
                    DedicatedRadarLaunchCard(
                        uiState = uiState,
                        onOpenRadar = onOpenRadar
                    )

                    if (isWideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ChannelConsoleCard(
                                    channels = uiState.channels,
                                    selectedChannel = uiState.selectedChannel,
                                    isBroadcastEnabled = uiState.isBroadcastEnabled,
                                    onSelectChannel = onSelectChannel,
                                    onToggleBroadcast = onToggleBroadcast
                                )
                                MeshSummaryGrid(uiState = uiState, onOpenRadar = onOpenRadar)
                            }
                            PttTransceiverCard(
                                selectedChannel = uiState.selectedChannel,
                                isPttTransmitting = uiState.isPttTransmitting,
                                isBluetoothReady = uiState.hardwareState.isBluetoothEnabled && uiState.hardwareState.hasRequiredPermissions,
                                inRangeCount = uiState.summary.inRangeCount,
                                onSetPtt = { active ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSetPttTransmitting(active)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        ChannelConsoleCard(
                            channels = uiState.channels,
                            selectedChannel = uiState.selectedChannel,
                            isBroadcastEnabled = uiState.isBroadcastEnabled,
                            onSelectChannel = onSelectChannel,
                            onToggleBroadcast = onToggleBroadcast
                        )

                        PttTransceiverCard(
                            selectedChannel = uiState.selectedChannel,
                            isPttTransmitting = uiState.isPttTransmitting,
                            isBluetoothReady = uiState.hardwareState.isBluetoothEnabled && uiState.hardwareState.hasRequiredPermissions,
                            inRangeCount = uiState.summary.inRangeCount,
                            onSetPtt = { active ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSetPttTransmitting(active)
                            }
                        )

                        MeshSummaryGrid(uiState = uiState, onOpenRadar = onOpenRadar)
                    }
                }
            }
        }
    }

    if (showIdentityDialog) {
        OperatorIdentityDialog(
            initialCallsign = uiState.operatorCallsign,
            initialSlot = uiState.localSlotNumber,
            onDismiss = { showIdentityDialog = false },
            onSave = { callsign, slot ->
                onUpdateOperatorIdentity(callsign, slot)
                showIdentityDialog = false
            }
        )
    }
}

@Composable
private fun OperatorIdentityDialog(
    initialCallsign: String,
    initialSlot: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var callsign by remember(initialCallsign) { mutableStateOf(initialCallsign) }
    var slot by remember(initialSlot) { mutableIntStateOf(initialSlot) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Local Radio Identity", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Assign a unique unit number (#01-#20) and callsign broadcast to nearby Walkie Talkie Pro peers.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = callsign,
                    onValueChange = { callsign = it.take(16) },
                    label = { Text("Operator Callsign") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Unit Slot: #${slot.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (slot > 1) slot-- },
                            enabled = slot > 1,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("-")
                        }
                        OutlinedButton(
                            onClick = { if (slot < MAX_GROUP_DEVICES) slot++ },
                            enabled = slot < MAX_GROUP_DEVICES,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("+")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(callsign, slot) },
                enabled = callsign.isNotBlank()
            ) {
                Text("Save Identity")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DedicatedRadarLaunchCard(
    uiState: WalkieRadarUiState,
    onOpenRadar: () -> Unit
) {
    val radarColors = RadarTheme.colors
    val summary = uiState.summary
    val radarButtonCd = stringResource(R.string.cd_radar_button)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = radarColors.chassisBg,
        border = BorderStroke(1.5.dp, radarColors.inRange.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            radarColors.inRange.copy(alpha = 0.12f),
                            radarColors.relayAccent.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE BLUETOOTH MESH RADAR",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${summary.enrolledCount}/$MAX_GROUP_DEVICES Group Devices • ${summary.inRangeCount} In Range • ${summary.relayCount} Relayed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = onOpenRadar,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = radarColors.inRange,
                        contentColor = Color(0xFF04140D)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("radar_button")
                        .semantics { contentDescription = radarButtonCd }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Radar,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.radar_button_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelConsoleCard(
    channels: List<RadioChannel>,
    selectedChannel: RadioChannel,
    isBroadcastEnabled: Boolean,
    onSelectChannel: (Int) -> Unit,
    onToggleBroadcast: (Boolean) -> Unit
) {
    val radarColors = RadarTheme.colors

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, radarColors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ACTIVE CHANNEL",
                        style = MaterialTheme.typography.labelMedium,
                        color = radarColors.mutedText
                    )
                    Text(
                        text = selectedChannel.bandLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Mesh Beacon",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = isBroadcastEnabled,
                        onCheckedChange = onToggleBroadcast,
                        modifier = Modifier.testTag("mesh_beacon_switch")
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(channels, key = { it.number }) { ch ->
                    val active = ch.number == selectedChannel.number
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) radarColors.relayContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (active) radarColors.relayAccent else radarColors.cardBorder
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectChannel(ch.number) }
                            .testTag("channel_chip_${ch.number}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "CH 0${ch.number}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) radarColors.relayAccent else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = ch.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PttTransceiverCard(
    selectedChannel: RadioChannel,
    isPttTransmitting: Boolean,
    isBluetoothReady: Boolean,
    inRangeCount: Int,
    onSetPtt: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = RadarTheme.colors
    val pttCd = stringResource(R.string.cd_ptt_button)
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_ring")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ptt_scale"
    )
    val ringColor by animateColorAsState(
        targetValue = if (isPttTransmitting) radarColors.pttAccent else radarColors.inRange,
        label = "ptt_color"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isPttTransmitting) radarColors.pttAccent else radarColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isPttTransmitting) {
                    "TRANSMITTING ON ${selectedChannel.name}"
                } else {
                    "PUSH TO TALK • ${selectedChannel.name}"
                },
                style = MaterialTheme.typography.labelLarge,
                color = ringColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    !isBluetoothReady -> "Local PTT test mode (enable Bluetooth & permissions for mesh TX)"
                    inRangeCount > 0 -> "$inRangeCount group units reachable for voice"
                    else -> "Open Radar to enroll or monitor Bluetooth mesh units"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(164.dp)
                    .scale(if (isPttTransmitting) pulse else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ringColor.copy(alpha = if (isPttTransmitting) 0.42f else 0.18f),
                                radarColors.chassisBg
                            )
                        )
                    )
                    .border(
                        width = 3.dp,
                        color = ringColor,
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onSetPtt(true)
                                tryAwaitRelease()
                                onSetPtt(false)
                            }
                        )
                    }
                    .testTag("ptt_hold_button")
                    .semantics { contentDescription = pttCd },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isPttTransmitting) Icons.Rounded.CellTower else Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = ringColor,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isPttTransmitting) "TX ACTIVE" else "HOLD PTT",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = { onSetPtt(!isPttTransmitting) },
                border = BorderStroke(1.dp, ringColor.copy(alpha = 0.6f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("ptt_latch_button")
            ) {
                Text(
                    text = if (isPttTransmitting) "Release Latch" else "Latch TX Mode",
                    color = ringColor,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun MeshSummaryGrid(
    uiState: WalkieRadarUiState,
    onOpenRadar: () -> Unit
) {
    val radarColors = RadarTheme.colors
    val summary = uiState.summary

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusStatTile(
                label = stringResource(R.string.status_in_range),
                count = summary.inRangeCount,
                icon = Icons.Rounded.CheckCircle,
                accent = radarColors.inRange,
                container = radarColors.inRangeContainer,
                onClick = onOpenRadar,
                modifier = Modifier.weight(1f)
            )
            StatusStatTile(
                label = stringResource(R.string.status_weak_unstable),
                count = summary.weakCount,
                icon = Icons.Rounded.WarningAmber,
                accent = radarColors.weakUnstable,
                container = radarColors.weakUnstableContainer,
                onClick = onOpenRadar,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusStatTile(
                label = stringResource(R.string.status_out_of_range),
                count = summary.outOfRangeCount,
                icon = Icons.Rounded.LinkOff,
                accent = radarColors.outOfRange,
                container = radarColors.outOfRangeContainer,
                onClick = onOpenRadar,
                modifier = Modifier.weight(1f)
            )
            StatusStatTile(
                label = "VIA RELAY",
                count = summary.relayCount,
                icon = Icons.Rounded.Hub,
                accent = radarColors.relayAccent,
                container = radarColors.relayContainer,
                onClick = onOpenRadar,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusStatTile(
    label: String,
    count: Int,
    icon: ImageVector,
    accent: Color,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = count.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = "$label count $count",
                tint = accent,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
