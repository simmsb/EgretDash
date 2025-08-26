package com.simmsb.egretdash.dashboard

sealed class ViewState {
    data object BluetoothOff : ViewState()

    data object NotGranted : ViewState()
    data object Connecting : ViewState()
    data class Connected(
        val battery: Int,
        val rssi: Int?,
    ) : ViewState()
    data object Disconnecting : ViewState()
    data object Disconnected : ViewState()
}