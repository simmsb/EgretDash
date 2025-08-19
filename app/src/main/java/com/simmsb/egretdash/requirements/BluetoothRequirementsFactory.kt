package com.simmsb.egretdash.requirements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

fun interface BluetoothRequirementsFactory {
    fun create(): BluetoothRequirements
}

@Composable
fun rememberBluetoothRequirementsFactory(): BluetoothRequirementsFactory {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        BluetoothRequirementsFactory { BluetoothRequirements(applicationContext) }
    }
}