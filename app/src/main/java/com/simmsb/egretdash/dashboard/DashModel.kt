package com.simmsb.egretdash.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.juul.kable.Identifier
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.logs.Logging
import com.juul.khronicle.Log
import com.simmsb.egretdash.DashboardDatabase
import com.simmsb.egretdash.Diagnostics
import com.simmsb.egretdash.Navigator
import com.simmsb.egretdash.Odometer
import com.simmsb.egretdash.Route
import com.simmsb.egretdash.Scooter
import com.simmsb.egretdash.ScooterStatus
import com.simmsb.egretdash.Trip
import com.simmsb.egretdash.requirements.BluetoothRequirements
import com.simmsb.egretdash.requirements.Deficiency
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

inline fun <K, V> Map<K, V>.mergeReduce(other: Map<K, V>, reduce: (V, V) -> V = { a, _ -> a }): Map<K, V> {
    return mergeReduceTo(LinkedHashMap(this.size + other.size), other, reduce)
}

inline fun <K, V, M : MutableMap<K, V>> Map<K, V>.mergeReduceTo(destination: M, other: Map<K, V>, reduce: (V, V) -> V = { a, _ -> a }): M {
    destination.putAll(this)
    for ((key, value) in other) {
        destination[key] = destination[key]?.let { reduce(it, value) } ?: value
    }
    return destination
}

@OptIn(ExperimentalTime::class)
class DashModel(
    navigator: Navigator<Route>,
    val database: DashboardDatabase,
    identifier: Identifier,
    bluetoothRequirements: BluetoothRequirements,
) : ViewModel() {
    val peripheral = Peripheral(identifier) {
        logging { level = Logging.Level.Warnings }
    }

    private val scooter = peripheral.let(::Scooter)

    val scooterStatus = scooter.scooterStatus.stateIn(viewModelScope, Eagerly, ScooterStatus.mock)
    val odometer = scooter.odometer.stateIn(viewModelScope, Eagerly, Odometer.mock)

    val diagnostics = scooter.diag.stateIn(viewModelScope, Eagerly, Diagnostics.mock)

    val numChartSamples = 100
    val chartVals = scooterStatus.runningFold(persistentListOf<ScooterStatus>(), { soFar, st ->
        // Log.info { "sofar.size: ${soFar.size}, st: ${st}"}
        if (soFar.size > numChartSamples) {
            soFar.mutate { it.drop(soFar.size - numChartSamples) } + st
        } else {
            soFar + st
        }
    })

    val trips = flow {
        var allTrips = mapOf<Int, Trip>()
        while (currentCoroutineContext().isActive) {
            val trips = scooter.readTrips()

            allTrips = allTrips.mergeReduce(trips, { old, new ->
                if (new.end != null && old.end == null) { new } else { old }
            })

            Log.info { "TRIPS: ${trips.size} ${trips}" }
            emit(allTrips)
            delay(10.seconds)
        }
    }.stateIn(viewModelScope, Eagerly, mapOf())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = bluetoothRequirements.deficiencies
        .map { Deficiency.BluetoothOff in it }
        .distinctUntilChanged()
        .flatMapLatest { isBluetoothOff ->
            if (isBluetoothOff) {
                flowOf(ViewState.BluetoothOff)
            } else {
                scooter.state.flatMapLatest { state ->
                    Log.info { state.toString() }
                    when (state) {
                        is State.Connecting -> flowOf(ViewState.Connecting)
                        is State.Connected -> combine(
                            scooter.battery,
                            scooter.rssi,
                            ViewState::Connected,
                        )
                        is State.Disconnecting -> flowOf(ViewState.Disconnecting)
                        is State.Disconnected -> flowOf(ViewState.Disconnected)
                    }
                }
            }
        }
        .stateIn(viewModelScope, Eagerly, ViewState.Connecting)

    init {
        onDisconnected {
            Log.info { "Reconnecting..." }
            scooter.connect()
        }
    }

    fun markTrip() {
        viewModelScope.launch {
            scooter.markTrip()
        }
    }

    fun displayText(msg: String) {
        viewModelScope.launch {
            scooter.sendDisplayText(msg)
        }
    }
//
//    fun setPeriod(period: Duration) {
//        screenModelScope.launch {
//            scooter.setPeriod(period)
//        }
//    }

    fun onDispose() {
        // GlobalScope to allow disconnect process to continue after leaving screen.
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            scooter.disconnect()
        }
    }

    @OptIn(FlowPreview::class) // For `debounce`.
    private fun onDisconnected(action: suspend (ViewState.Disconnected) -> Unit) {
        // When bluetooth is turned off, race conditions can occur w.r.t. bluetooth off vs. device
        // disconnected, so debounce allows us to handle the most recent/relevant state.
        state
            .debounce(1.seconds)
            .filterIsInstance<ViewState.Disconnected>()
            .onEach(action)
            .launchIn(viewModelScope)
    }
}