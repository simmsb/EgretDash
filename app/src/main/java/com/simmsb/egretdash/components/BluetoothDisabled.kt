package com.simmsb.egretdash.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable

@Composable
fun BluetoothDisabled(enableAction: () -> Unit) {
    ActionRequired(
        icon = Icons.Filled.Info,
        contentDescription = "Bluetooth disabled",
        description = "Bluetooth is disabled.",
        buttonText = "Enable",
        onClick = enableAction,
    )
}