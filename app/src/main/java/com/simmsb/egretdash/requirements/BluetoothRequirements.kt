package com.simmsb.egretdash.requirements

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.ERROR
import android.bluetooth.BluetoothAdapter.EXTRA_STATE
import android.bluetooth.BluetoothAdapter.STATE_ON
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.location.LocationManager
import android.location.LocationManager.EXTRA_PROVIDER_ENABLED
import android.location.LocationManager.PROVIDERS_CHANGED_ACTION
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.R
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.juul.tuulbox.coroutines.flow.broadcastReceiverFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED as BLUETOOTH_STATE_CHANGED

enum class Deficiency {
    LocationServicesDisabled, // Android (API 30 and lower) only.
    BluetoothOff,
}


class BluetoothRequirements(applicationContext: Context)  {

    private val isBluetoothOn: Flow<Boolean> =
        broadcastReceiverFlow(IntentFilter(BLUETOOTH_STATE_CHANGED))
            .map { intent -> intent.getIntExtra(EXTRA_STATE, ERROR) }
            .map { state -> state == STATE_ON }
            .onStart { emit(applicationContext.getBluetoothAdapterOrNull()?.isEnabled ?: false) }

    /** @throws IllegalStateException If [LocationManager] system service is not available. */
    private val areLocationServicesEnabled: Flow<Boolean> =
        broadcastReceiverFlow(IntentFilter(PROVIDERS_CHANGED_ACTION))
            .map { intent ->
                when (SDK_INT) {
                    R -> intent.getBooleanExtra(EXTRA_PROVIDER_ENABLED, false)
                    else -> applicationContext.isLocationEnabled
                }
            }
            .onStart { emit(applicationContext.isLocationEnabled) }
            .distinctUntilChanged()

    val deficiencies: Flow<Set<Deficiency>> =
        // Location services are not required on Android API > R.
        isBluetoothOn.map { if (it) emptySet() else setOf(Deficiency.BluetoothOff) }
}

private fun Context.getBluetoothManagerOrNull(): BluetoothManager? =
    ContextCompat.getSystemService(this, BluetoothManager::class.java)

/**
 * Per documentation, `BluetoothAdapter.getDefaultAdapter()` returns `null` when "Bluetooth is not
 * supported on this hardware platform".
 *
 * https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#getDefaultAdapter()
 */
fun Context.getBluetoothAdapterOrNull(): BluetoothAdapter? =
    getBluetoothManagerOrNull()?.adapter

private val Context.locationManager: LocationManager
    get() = ContextCompat.getSystemService(this, LocationManager::class.java)
        ?: error("LocationManager system service unavailable")

private val Context.isLocationEnabled: Boolean
    get() = LocationManagerCompat.isLocationEnabled(locationManager)