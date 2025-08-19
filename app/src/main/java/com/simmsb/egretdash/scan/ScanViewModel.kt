package com.simmsb.egretdash.scan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.kable.Bluetooth
import com.juul.kable.ExperimentalApi
import com.juul.kable.PlatformAdvertisement
import com.simmsb.egretdash.Navigator
import com.simmsb.egretdash.PREFERRED_DEVICE_KEY
import com.simmsb.egretdash.Route
import com.simmsb.egretdash.ScreenDash
import com.simmsb.egretdash.requestPermission
import com.simmsb.egretdash.requirements.BluetoothRequirements
import com.simmsb.egretdash.requirements.Deficiency
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.bluetooth.BLUETOOTH_CONNECT
import dev.icerock.moko.permissions.bluetooth.BLUETOOTH_SCAN
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScanViewModel(
    private val navigator: Navigator<Route>,
    val permissionsController: PermissionsController,
    bluetoothRequirements: BluetoothRequirements,
) : ViewModel() {

    private val _showConnectPermissionAlertDialog = MutableStateFlow(false)
    val showConnectPermissionAlertDialog = _showConnectPermissionAlertDialog.asStateFlow()

    private val isBluetoothSupported = MutableStateFlow<Boolean?>(null)
    private val permissionState = MutableStateFlow(PermissionState.NotDetermined)
    private val isPermissionGranted = permissionState.map { it == PermissionState.Granted }

    /** `null` until scan permission has been requested. */
    private val isRequestingScanPermission = MutableStateFlow<Boolean?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val requirements = isPermissionGranted.flatMapLatest { isGranted ->
        bluetoothRequirements.deficiencies.takeIf { isGranted } ?: flowOf(null)
    }

    private val deviceLocator = ScannerDeviceLocator(
        viewModelScope,
        {}
    )

    val viewState: StateFlow<ViewState> = combine(
        isBluetoothSupported,
        permissionState,
        requirements,
        deviceLocator.state,
        deviceLocator.advertisements,
    ) { isSupported, permissionState, requirements, scanState, advertisements ->
        when (isSupported) {
            false -> ViewState.Unsupported
            true -> when (permissionState) {
                PermissionState.NotDetermined, PermissionState.NotGranted, PermissionState.Denied -> ViewState.Scan
                PermissionState.Granted -> when {
                    requirements == null -> null
                    scanState == DeviceLocator.State.NotYetScanned -> ViewState.Scan
                    Deficiency.BluetoothOff in requirements -> ViewState.BluetoothOff
                    Deficiency.LocationServicesDisabled in requirements -> ViewState.LocationServicesDisabled
                    else -> ViewState.Devices(scanState, advertisements)
                }
                PermissionState.DeniedAlways -> ViewState.PermissionDenied
                else -> error("Unhandled permission state: $permissionState")
            }
            null -> ViewState.Scan
        }
    }.filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState.Scan)

    init {
        viewModelScope.launch {
            @OptIn(ExperimentalApi::class)
            isBluetoothSupported.value = Bluetooth.isSupported()
        }
    }

    /**
     * Re-check permissions when screen resumes (can occur after coming back from app settings
     * screen where user may have granted needed permissions).
     */
    fun onResumed() {
        viewModelScope.launch {
            when (isRequestingScanPermission.value) {
                // After requesting permission, onResume is triggered, so we reset the
                // "is requesting" state here.
                true -> isRequestingScanPermission.value = false

                false -> requestAndUpdateScanPermission()

                // On Apple (until authorized) even checking the permission state will show a
                // permission dialog. We guard against showing the dialog prior to actually wanting
                // to request the permission by only checking permission after we've explicitly
                // requested permission.
                null -> {} // No-op
            }
        }
    }

    fun scan() {
        if (deviceLocator.state.value == DeviceLocator.State.Scanning) return // Scan already in progress.

        if (permissionState.value == PermissionState.Granted) {
            deviceLocator.run()
        } else {
            viewModelScope.launch {
                requestAndUpdateScanPermission()
                if (permissionState.value == PermissionState.Granted) {
                    deviceLocator.run()
                }
            }
        }
    }

    fun onAdvertisementClicked(advertisement: PlatformAdvertisement, preferredDeviceDataStore: DataStore<Preferences>) {
        viewModelScope.launch {
            when (requestConnectPermission()) {
                PermissionState.Granted -> navigateToSensorScreen(advertisement, preferredDeviceDataStore)
                PermissionState.DeniedAlways -> _showConnectPermissionAlertDialog.value = true
                else -> {} // No-op
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            deviceLocator.clear()
        }
    }

    fun openAppSettings() {
        permissionsController.openAppSettings()
    }

    fun dismissAlert() {
        _showConnectPermissionAlertDialog.value = false
    }

    private suspend fun navigateToSensorScreen(advertisement: PlatformAdvertisement, preferredDeviceDataStore: DataStore<Preferences>) {
        deviceLocator.cancelAndJoin()
        preferredDeviceDataStore.edit { settings ->
            settings[PREFERRED_DEVICE_KEY] = advertisement.identifier
        }
        navigator.navigate(ScreenDash(advertisement.identifier))
    }

    private suspend fun requestAndUpdateScanPermission() {
        // Once we've been granted permission we no longer need to request permission. Apple and
        // Android will kill the app if permissions are revoked.
        if (permissionState.value == PermissionState.Granted) return

        isRequestingScanPermission.value = true
        permissionsController.requestPermission(Permission.BLUETOOTH_SCAN)?.let {
            permissionState.value = it
        }
    }

    private suspend fun requestConnectPermission() =
        permissionsController.requestPermission(Permission.BLUETOOTH_CONNECT)
}