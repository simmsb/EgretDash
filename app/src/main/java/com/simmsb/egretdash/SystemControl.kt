package com.simmsb.egretdash

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val EnableBluetoothRequestCode = 55001

class SystemControl(private val activity: Activity) {
    fun showLocationSettings() {
        activity.startActivity(Intent(ACTION_LOCATION_SOURCE_SETTINGS))
    }

    /** @throws SecurityException If [BLUETOOTH_CONNECT] permission has not been granted on Android 12 (API 31) or newer. */
    @SuppressLint("MissingPermission")
    fun requestToTurnBluetoothOn() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity.startActivityForResult(intent, EnableBluetoothRequestCode)
    }
}

@Composable
fun rememberSystemControl(): SystemControl {
    val context = LocalContext.current
    val activity = context as? Activity ?: error("$context is not an instance of Activity")
    return remember(activity) {
        SystemControl(activity)
    }
}