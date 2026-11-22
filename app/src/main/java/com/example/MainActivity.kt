package com.example

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mesh.BleMeshController
import com.example.mesh.ScanPowerMode
import com.example.ui.DashboardScreen
import com.example.ui.RadarScreen
import com.example.ui.RadarViewModel
import com.example.ui.theme.WalkieTalkieProTheme

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_RADAR = "radar"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WalkieTalkieProTheme {
                WalkieTalkieProApp()
            }
        }
    }
}

@Composable
fun WalkieTalkieProApp(
    radarViewModel: RadarViewModel = viewModel()
) {
    val uiState by radarViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        radarViewModel.onPermissionsUpdated()
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        radarViewModel.onPermissionsUpdated()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> radarViewModel.onLifecycleStart()
                Lifecycle.Event.ON_STOP -> radarViewModel.onLifecycleStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestPermissions = {
        runCatching {
            permissionLauncher.launch(BleMeshController.requiredBluetoothPermissions())
        }
        Unit
    }
    val requestEnableBluetooth = {
        runCatching {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(intent)
        }
        Unit
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_DASHBOARD
    ) {
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                uiState = uiState,
                onOpenRadar = {
                    navController.navigate(ROUTE_RADAR) {
                        launchSingleTop = true
                    }
                },
                onSelectChannel = radarViewModel::selectChannel,
                onSetPttTransmitting = radarViewModel::setPttTransmitting,
                onToggleBroadcast = radarViewModel::setBroadcastEnabled,
                onUpdateOperatorIdentity = radarViewModel::updateOperatorIdentity,
                onRefreshRadio = radarViewModel::refreshDiscoveryNow,
                onRequestPermissions = requestPermissions,
                onRequestEnableBluetooth = requestEnableBluetooth
            )
        }

        composable(ROUTE_RADAR) {
            RadarScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() },
                onToggleScanPaused = radarViewModel::toggleScanPaused,
                onTogglePowerMode = {
                    val next = if (uiState.scanPowerMode == ScanPowerMode.BALANCED) {
                        ScanPowerMode.BATTERY_SAVER
                    } else {
                        ScanPowerMode.BALANCED
                    }
                    radarViewModel.setScanPowerMode(next)
                },
                onSelectFilter = radarViewModel::selectFilter,
                onSelectNode = radarViewModel::selectNode,
                onEnrollCandidate = radarViewModel::enrollCandidate,
                onRenameNode = radarViewModel::renameNode,
                onRemoveNode = radarViewModel::removeNode,
                onToggleBroadcast = radarViewModel::setBroadcastEnabled,
                onRefreshDiscovery = radarViewModel::refreshDiscoveryNow,
                onRequestPermissions = requestPermissions,
                onRequestEnableBluetooth = requestEnableBluetooth,
                onClearToast = radarViewModel::clearToastNotice
            )
        }
    }
}
