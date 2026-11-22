package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.mesh.ConnectionRouteType
import com.example.mesh.DeviceStatus
import com.example.mesh.DiscoveredBluetoothCandidate
import com.example.mesh.MAX_GROUP_DEVICES
import com.example.mesh.RadarDeviceNode
import com.example.mesh.RadarFilter
import com.example.mesh.ScanPowerMode
import com.example.ui.theme.RadarTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    uiState: WalkieRadarUiState,
    onNavigateBack: () -> Unit,
    onToggleScanPaused: () -> Unit,
    onTogglePowerMode: () -> Unit,
    onSelectFilter: (RadarFilter) -> Unit,
    onSelectNode: (String?) -> Unit,
    onEnrollCandidate: (DiscoveredBluetoothCandidate, String?) -> Unit,
    onRenameNode: (String, String) -> Unit,
    onRemoveNode: (String) -> Unit,
    onToggleBroadcast: (Boolean) -> Unit,
    onRefreshDiscovery: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onClearToast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radarColors = RadarTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }

    var showEnrollSheet by remember { mutableStateOf(false) }
    var inspectedNode by remember { mutableStateOf<RadarDeviceNode?>(null) }

    LaunchedEffect(uiState.toastNotice) {
        val msg = uiState.toastNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onClearToast()
    }

    val filteredNodes = remember(uiState.nodes, uiState.selectedFilter) {
        when (uiState.selectedFilter) {
            RadarFilter.ALL -> uiState.nodes
            RadarFilter.IN_RANGE -> uiState.nodes.filter { it.status == DeviceStatus.IN_RANGE }
            RadarFilter.WEAK -> uiState.nodes.filter { it.status == DeviceStatus.WEAK_UNSTABLE }
            RadarFilter.OUT_OF_RANGE -> uiState.nodes.filter { it.status == DeviceStatus.OUT_OF_RANGE }
            RadarFilter.RELAY -> uiState.nodes.filter { it.routeType == ConnectionRouteType.RELAY }
        }
    }

    val enrolledAddresses = remember(uiState.nodes) {
        uiState.nodes.map { it.address.uppercase() }.toSet()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = radarColors.chassisBg,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("radar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.radar_screen_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.hardwareState.statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onTogglePowerMode,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("power_mode_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.scanPowerMode == ScanPowerMode.BATTERY_SAVER) {
                                Icons.Rounded.BatterySaver
                            } else {
                                Icons.Rounded.Speed
                            },
                            contentDescription = "Scan power mode: ${uiState.scanPowerMode.label}",
                            tint = if (uiState.scanPowerMode == ScanPowerMode.BATTERY_SAVER) {
                                radarColors.inRange
                            } else {
                                radarColors.relayAccent
                            }
                        )
                    }

                    IconButton(
                        onClick = onToggleScanPaused,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_scan_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isScanPaused) {
                                Icons.Rounded.PlayArrow
                            } else {
                                Icons.Rounded.Pause
                            },
                            contentDescription = stringResource(R.string.cd_toggle_scan),
                            tint = if (uiState.isScanPaused) radarColors.weakUnstable else radarColors.inRange
                        )
                    }

                    Button(
                        onClick = {
                            onRefreshDiscovery()
                            showEnrollSheet = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = radarColors.inRange,
                            contentColor = Color(0xFF04140D)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("enroll_device_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_enroll_device),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("BT", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isSideBySide = maxWidth >= 720.dp

            if (isSideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BluetoothReadinessBanner(
                            hardwareState = uiState.hardwareState,
                            onRequestPermissions = onRequestPermissions,
                            onRequestEnableBluetooth = onRequestEnableBluetooth,
                            onRetryHardware = onRefreshDiscovery
                        )
                        RadarScopeCanvas(
                            nodes = uiState.nodes,
                            selectedNodeAddress = uiState.selectedNodeAddress,
                            isScanningActive = !uiState.isScanPaused && uiState.hardwareState.isBluetoothEnabled,
                            isPttTransmitting = uiState.isPttTransmitting,
                            localSlotNumber = uiState.localSlotNumber,
                            onSelectNode = { onSelectNode(it) }
                        )
                        AccessibleRadarLegend()
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadarFilterBar(
                            uiState = uiState,
                            onSelectFilter = onSelectFilter
                        )
                        DeviceRosterList(
                            nodes = filteredNodes,
                            totalEnrolled = uiState.nodes.size,
                            selectedAddress = uiState.selectedNodeAddress,
                            nowMs = uiState.nowMs,
                            onSelectNode = onSelectNode,
                            onInspectNode = { inspectedNode = it },
                            onOpenEnrollSheet = {
                                onRefreshDiscovery()
                                showEnrollSheet = true
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "banner") {
                        BluetoothReadinessBanner(
                            hardwareState = uiState.hardwareState,
                            onRequestPermissions = onRequestPermissions,
                            onRequestEnableBluetooth = onRequestEnableBluetooth,
                            onRetryHardware = onRefreshDiscovery
                        )
                    }

                    item(key = "radar_scope") {
                        RadarScopeCanvas(
                            nodes = uiState.nodes,
                            selectedNodeAddress = uiState.selectedNodeAddress,
                            isScanningActive = !uiState.isScanPaused && uiState.hardwareState.isBluetoothEnabled,
                            isPttTransmitting = uiState.isPttTransmitting,
                            localSlotNumber = uiState.localSlotNumber,
                            onSelectNode = { onSelectNode(it) }
                        )
                    }

                    item(key = "legend") {
                        AccessibleRadarLegend()
                    }

                    item(key = "filters") {
                        RadarFilterBar(
                            uiState = uiState,
                            onSelectFilter = onSelectFilter
                        )
                    }

                    if (filteredNodes.isEmpty()) {
                        item(key = "empty_roster") {
                            EmptyGroupRosterCard(
                                totalEnrolled = uiState.nodes.size,
                                onOpenEnrollSheet = {
                                    onRefreshDiscovery()
                                    showEnrollSheet = true
                                }
                            )
                        }
                    } else {
                        items(filteredNodes, key = { it.address }) { node ->
                            RadarDeviceCard(
                                node = node,
                                isSelected = node.address == uiState.selectedNodeAddress,
                                nowMs = uiState.nowMs,
                                onSelect = { onSelectNode(node.address) },
                                onInspect = { inspectedNode = node }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEnrollSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEnrollSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            EnrollBluetoothSheetContent(
                candidates = uiState.discoveredCandidates,
                enrolledAddresses = enrolledAddresses,
                enrolledCount = uiState.nodes.size,
                isBroadcastEnabled = uiState.isBroadcastEnabled,
                isAdvertisingActive = uiState.hardwareState.isAdvertisingActive,
                onToggleBroadcast = onToggleBroadcast,
                onRefresh = onRefreshDiscovery,
                onEnroll = { candidate, label ->
                    onEnrollCandidate(candidate, label)
                }
            )
        }
    }

    inspectedNode?.let { node ->
        NodeInspectorDialog(
            node = uiState.nodes.firstOrNull { it.address == node.address } ?: node,
            nowMs = uiState.nowMs,
            onDismiss = { inspectedNode = null },
            onRename = { newLabel ->
                onRenameNode(node.address, newLabel)
                inspectedNode = null
            },
            onRemove = {
                onRemoveNode(node.address)
                inspectedNode = null
            }
        )
    }
}

@Composable
private fun AccessibleRadarLegend() {
    val radarColors = RadarTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, radarColors.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(
                icon = Icons.Rounded.CheckCircle,
                label = "IN RANGE",
                color = radarColors.inRange
            )
            LegendItem(
                icon = Icons.Rounded.Hub,
                label = "RELAY",
                color = radarColors.relayAccent
            )
            LegendItem(
                icon = Icons.Rounded.WarningAmber,
                label = "WEAK",
                color = radarColors.weakUnstable
            )
            LegendItem(
                icon = Icons.Rounded.LinkOff,
                label = "LOST",
                color = radarColors.outOfRange
            )
        }
    }
}

@Composable
private fun LegendItem(
    icon: ImageVector,
    label: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun RadarFilterBar(
    uiState: WalkieRadarUiState,
    onSelectFilter: (RadarFilter) -> Unit
) {
    val summary = uiState.summary
    val items = listOf(
        RadarFilter.ALL to "All (${summary.enrolledCount})",
        RadarFilter.IN_RANGE to "In Range (${summary.inRangeCount})",
        RadarFilter.WEAK to "Weak (${summary.weakCount})",
        RadarFilter.OUT_OF_RANGE to "Lost (${summary.outOfRangeCount})",
        RadarFilter.RELAY to "Relay (${summary.relayCount})"
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.first.name }) { (filter, label) ->
            val selected = uiState.selectedFilter == filter
            FilterChip(
                selected = selected,
                onClick = { onSelectFilter(filter) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RadarTheme.colors.inRangeContainer,
                    selectedLabelColor = RadarTheme.colors.inRange
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("filter_chip_${filter.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun DeviceRosterList(
    nodes: List<RadarDeviceNode>,
    totalEnrolled: Int,
    selectedAddress: String?,
    nowMs: Long,
    onSelectNode: (String) -> Unit,
    onInspectNode: (RadarDeviceNode) -> Unit,
    onOpenEnrollSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (nodes.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth()) {
            EmptyGroupRosterCard(
                totalEnrolled = totalEnrolled,
                onOpenEnrollSheet = onOpenEnrollSheet
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(nodes, key = { it.address }) { node ->
                RadarDeviceCard(
                    node = node,
                    isSelected = node.address == selectedAddress,
                    nowMs = nowMs,
                    onSelect = { onSelectNode(node.address) },
                    onInspect = { onInspectNode(node) }
                )
            }
        }
    }
}

@Composable
private fun EmptyGroupRosterCard(
    totalEnrolled: Int,
    onOpenEnrollSheet: () -> Unit
) {
    val radarColors = RadarTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, radarColors.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.BluetoothSearching,
                contentDescription = null,
                tint = radarColors.relayAccent,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (totalEnrolled == 0) {
                    "Listening for Real Bluetooth Group Nodes"
                } else {
                    "No Nodes Match This Filter"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (totalEnrolled == 0) {
                    "Other phones running Walkie Talkie Pro auto-enroll up to $MAX_GROUP_DEVICES units. You can also enroll any real nearby or paired Bluetooth device manually."
                } else {
                    "Switch filters above to inspect other group nodes."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (totalEnrolled == 0) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onOpenEnrollSheet,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = radarColors.inRange,
                        contentColor = Color(0xFF04140D)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("empty_enroll_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Scan & Enroll Bluetooth Device")
                }
            }
        }
    }
}

@Composable
private fun EnrollBluetoothSheetContent(
    candidates: List<DiscoveredBluetoothCandidate>,
    enrolledAddresses: Set<String>,
    enrolledCount: Int,
    isBroadcastEnabled: Boolean,
    isAdvertisingActive: Boolean,
    onToggleBroadcast: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEnroll: (DiscoveredBluetoothCandidate, String?) -> Unit
) {
    val radarColors = RadarTheme.colors
    var labelDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Real Bluetooth Discovery",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$enrolledCount / $MAX_GROUP_DEVICES group slots used",
                    style = MaterialTheme.typography.labelMedium,
                    color = radarColors.relayAccent
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh nearby Bluetooth devices"
                )
            }
        }

        // Broadcast self as a Walkie Talkie Pro BLE Mesh Beacon
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, radarColors.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Broadcast Walkie Mesh Beacon",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isAdvertisingActive) {
                            "Broadcasting node heartbeat & relay vector"
                        } else {
                            "Enable so nearby Walkie Talkie Pro phones auto-discover this unit"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isBroadcastEnabled,
                    onCheckedChange = onToggleBroadcast
                )
            }
        }

        if (candidates.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Bluetooth devices detected yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Ensure Bluetooth & Nearby permissions are enabled. Real BLE peripherals, paired devices, and Walkie Talkie Pro peers appear here automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(candidates, key = { it.address }) { candidate ->
                    val alreadyEnrolled = candidate.address.uppercase() in enrolledAddresses
                    val customText = labelDrafts[candidate.address] ?: candidate.name

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (candidate.isMeshPeer) radarColors.inRange.copy(alpha = 0.6f)
                            else radarColors.cardBorder
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = buildString {
                                            append(candidate.address)
                                            if (candidate.rssiDbm != null) append(" • ${candidate.rssiDbm} dBm")
                                            if (candidate.isMeshPeer) append(" • MESH PEER")
                                            else if (candidate.isBonded) append(" • PAIRED")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = { onEnroll(candidate, customText) },
                                    enabled = !alreadyEnrolled && enrolledCount < MAX_GROUP_DEVICES,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = radarColors.inRange,
                                        contentColor = Color(0xFF04140D)
                                    ),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(if (alreadyEnrolled) "Enrolled" else "Enroll")
                                }
                            }

                            if (!alreadyEnrolled) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = customText,
                                    onValueChange = { updated ->
                                        labelDrafts = labelDrafts + (candidate.address to updated)
                                    },
                                    label = { Text("Unit Label") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NodeInspectorDialog(
    node: RadarDeviceNode,
    nowMs: Long,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit
) {
    val radarColors = RadarTheme.colors
    var labelInput by remember(node.address) { mutableStateOf(node.label) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${node.unitCode} • ${node.label}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Status: ${node.status.accessibleLabel}",
                    style = MaterialTheme.typography.titleSmall,
                    color = when (node.status) {
                        DeviceStatus.IN_RANGE -> radarColors.inRange
                        DeviceStatus.WEAK_UNSTABLE -> radarColors.weakUnstable
                        DeviceStatus.OUT_OF_RANGE -> radarColors.outOfRange
                    }
                )
                Text(
                    text = "Route: ${node.routeType.label}" + (node.relayPath?.let {
                        " (via ${it.viaLabel}, ${it.hopCount} hops)"
                    } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "RSSI: ${node.directRssiDbm?.let { "$it dBm (${node.signalQualityTier.label})" } ?: "Direct RSSI unavailable"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Last Seen: ${formatLastSeen(node.lastSeenMs, nowMs)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Address: ${node.address}",
                    style = MaterialTheme.typography.labelSmall,
                    color = radarColors.mutedText
                )

                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text("Unique Unit Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRename(labelInput) },
                enabled = labelInput.isNotBlank()
            ) {
                Text("Save Label")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = radarColors.outOfRange),
                    border = BorderStroke(1.dp, radarColors.outOfRange.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
