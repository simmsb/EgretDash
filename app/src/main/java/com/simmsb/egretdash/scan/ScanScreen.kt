package com.simmsb.egretdash.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juul.kable.PlatformAdvertisement
import com.simmsb.egretdash.Navigator
import com.simmsb.egretdash.Route
import com.simmsb.egretdash.components.ActionRequired
import com.simmsb.egretdash.components.BluetoothDisabled
import com.simmsb.egretdash.preferredDeviceDataStore
import com.simmsb.egretdash.rememberSystemControl
import com.simmsb.egretdash.requirements.rememberBluetoothRequirementsFactory
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory

@Composable
fun ScanScreen(navigator: Navigator<Route>) {
    val screenModel = rememberScreenModel(navigator)

    OnLifecycleResumed(screenModel::onResumed)

    val viewState by screenModel.viewState.collectAsState()
    val showConnectPermissionAlertDialog by screenModel.showConnectPermissionAlertDialog.collectAsState()
    val preferredDevice = LocalContext.current.preferredDeviceDataStore

    Column(
        Modifier
            .fillMaxSize()
    ) {
        Refresher(
            viewState,
            onRefreshClick = screenModel::scan,
        ) {
            Box(Modifier.weight(1f).fillMaxSize()) {
//                    ProvideTextStyle(
//                        TextStyle(color = contentColorFor(backgroundColor =))
//                    ) {
                val systemControl = rememberSystemControl()
                ScanPane(
                    viewState,
                    onScanClick = screenModel::scan,
                    onShowAppSettingsClick = screenModel::openAppSettings,
                    onShowLocationSettingsClick = systemControl::showLocationSettings,
                    onEnableBluetoothClick = systemControl::requestToTurnBluetoothOn,
                    onAdvertisementClicked = { screenModel.onAdvertisementClicked(it, preferredDevice) },
                )
//                    }

                if (showConnectPermissionAlertDialog) {
                    ConnectPermissionsAlertDialog(
                        onOpenAppSettings = screenModel::openAppSettings,
                        onCancel = screenModel::dismissAlert,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberScreenModel(navigator: Navigator<Route>): ScanViewModel {
    val permissionsFactory = rememberPermissionsControllerFactory()
    val bluetoothRequirementsFactory = rememberBluetoothRequirementsFactory()
    val screenModel = remember {
        val permissionsController = permissionsFactory.createPermissionsController()
        val bluetoothRequirements = bluetoothRequirementsFactory.create()
        ScanViewModel(navigator, permissionsController, bluetoothRequirements)
    }
    BindEffect(screenModel.permissionsController)
    return screenModel
}


@Composable
private fun ConnectPermissionsAlertDialog(
    onOpenAppSettings: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onCancel,
        title = { Text("Permission required") },
        text = { Text("Bluetooth connect permission needed to connect to device. Please grant permission via App settings.") },
        confirmButton = {
            TextButton(onClick = onOpenAppSettings) {
                Text("Open App Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ScanPane(
    viewState: ViewState,
    onScanClick: () -> Unit,
    onShowAppSettingsClick: () -> Unit,
    onShowLocationSettingsClick: () -> Unit,
    onEnableBluetoothClick: () -> Unit,
    onAdvertisementClicked: (PlatformAdvertisement) -> Unit,
) {
    when (viewState) {
        is ViewState.Unsupported -> BluetoothNotSupported()
        is ViewState.Scan -> Scan(message = null, onScanClick)
        is ViewState.PermissionDenied -> BluetoothPermissionsDenied(onShowAppSettingsClick)
        is ViewState.LocationServicesDisabled -> LocationServicesDisabled(
            onShowLocationSettingsClick
        )

        is ViewState.BluetoothOff -> BluetoothDisabled(onEnableBluetoothClick)
        is ViewState.Devices -> AdvertisementsList(
            viewState.scanState, viewState.advertisements, onScanClick, onAdvertisementClicked
        )
    }
}

@Composable
private fun BluetoothNotSupported() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Bluetooth not supported.")
    }
}

@Composable
private fun Loading() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun Scan(message: String?, onScanClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = CenterHorizontally) {
            if (message != null) Text(message)
            Button(onScanClick) {
                Text("Scan")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refresher(
    viewState: ViewState,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isRefreshing = ((viewState as? ViewState.Devices)?.scanState == DeviceLocator.State.Scanning) && viewState.advertisements.isEmpty()
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefreshClick, modifier = modifier) {
        content()
    }
}

@Composable
private fun LocationServicesDisabled(enableAction: () -> Unit) {
    ActionRequired(
        icon = Icons.Filled.LocationOn,
        contentDescription = "Location services disabled",
        description = "Location services are disabled.",
        buttonText = "Enable",
        onClick = enableAction,
    )
}

@Composable
private fun BluetoothPermissionsDenied(onShowAppSettingsClick: () -> Unit) {
    ActionRequired(
        icon = Icons.Filled.Warning,
        contentDescription = "Bluetooth permissions required",
        description = "Bluetooth permissions are required for scanning. Please grant the permission.",
        buttonText = "Open Settings",
        onClick = onShowAppSettingsClick,
    )
}

@Composable
private fun AdvertisementsList(
    scanState: DeviceLocator.State,
    advertisements: List<PlatformAdvertisement>,
    onScanClick: () -> Unit,
    onAdvertisementClick: (PlatformAdvertisement) -> Unit,
) {
    when {
        scanState == DeviceLocator.State.NotYetScanned -> Scan(message = null, onScanClick)

        // Scanning or Scanned w/ advertisements found.
        advertisements.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
            items(advertisements.size) { index ->
                val advertisement = advertisements[index]
                AdvertisementRow(advertisement) { onAdvertisementClick(advertisement) }
            }
        }

        // Scanning w/ no advertisements yet found.
        scanState == DeviceLocator.State.Scanning -> Loading()

        // Scanned w/ no advertisements found.
        else -> Scan("No devices found.", onScanClick)
    }
}

@Composable
private fun AdvertisementRow(advertisement: PlatformAdvertisement, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                fontSize = 22.sp,
                text = advertisement.name ?: "Unknown",
            )
            Text(advertisement.identifier.toString())
        }

        Text(
            modifier = Modifier.align(CenterVertically),
            text = "${advertisement.rssi} dBm",
        )
    }
}
