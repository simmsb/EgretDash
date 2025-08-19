package com.simmsb.egretdash

import com.juul.kable.Filter
import com.juul.kable.GattStatusException
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.logs.Logging.Level.Events
import com.juul.khronicle.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid


val diagnosticsServiceUuid = Uuid.parse("2B419D90-ADEF-4CA3-8652-C02093B6C84E")
val diagnosticsCommandCharacteristic = characteristicOf(service = diagnosticsServiceUuid, characteristic = Uuid.parse("2B419D91-ADEF-4CA3-8652-C02093B6C84E"))
val diagnosticsStatusCharacteristic = characteristicOf(service = diagnosticsServiceUuid, characteristic = Uuid.parse("2B419D92-ADEF-4CA3-8652-C02093B6C84E"))
val diagnosticsChargeHistoryCharacteristic = characteristicOf(service = diagnosticsServiceUuid, characteristic = Uuid.parse("2B419D93-ADEF-4CA3-8652-C02093B6C84E"))
val diagnosticsErrorHistoryCharacteristic = characteristicOf(service = diagnosticsServiceUuid, characteristic = Uuid.parse("2B419D94-ADEF-4CA3-8652-C02093B6C84E"))

val batteryServiceUuid = Uuid.parse("F4B68C10-9E9E-4B97-A9C9-272E32453252")
val batteryLevelCharacteristic = characteristicOf(service = batteryServiceUuid, characteristic = Uuid.parse("F4B68C11-9E9E-4B97-A9C9-272E32453252"))
val batteryInfoCharacteristic = characteristicOf(service = batteryServiceUuid, characteristic = Uuid.parse("F4B68C13-9E9E-4B97-A9C9-272E32453252"))
val batteryDiagnosticsCharacteristic = characteristicOf(service = batteryServiceUuid, characteristic =Uuid.parse("F4B68C14-9E9E-4B97-A9C9-272E32453252"))

val operationServiceUuid = Uuid.parse("7D971CD0-8EBD-4684-B771-B2362B7E922A")
val operationCommandCharacteristic = characteristicOf(service = operationServiceUuid, characteristic = Uuid.parse("7D971CD1-8EBD-4684-B771-B2362B7E922A"))
val operationStatsCharacteristic = characteristicOf(service = operationServiceUuid, characteristic = Uuid.parse("7D971CD2-8EBD-4684-B771-B2362B7E922A"))
val operationOdoCharacteristic = characteristicOf(service = operationServiceUuid, characteristic = Uuid.parse("7D971CD3-8EBD-4684-B771-B2362B7E922A"))

val tripsServiceUuid = Uuid.parse("3F9836D0-BCD8-4754-B1B2-216A6FDF255D")
val tripsCharacteristic = characteristicOf(service = tripsServiceUuid, characteristic = Uuid.parse("3F9836D1-BCD8-4754-B1B2-216A6FDF255D"))

private val rssiInterval = 5.seconds

class Scooter (private val peripheral: Peripheral) {

    companion object {
        val discoveryService = Uuid.parse("70D1B670-EBA5-4D76-868F-6D1B66108FDC")

        val AdvertisedServices = listOf(discoveryService)

        val scanner by lazy {
            Scanner {
                logging {
                    level = Events
                }
                filters {
                   match { name = Filter.Name.Exact("EGRET GT") }
                }
            }
        }
    }

    val state = peripheral.state

    private val _battery = MutableStateFlow<ByteArray?>(null)

    /** Battery percent level (0-100). */
    val battery = merge(
        _battery.filterNotNull(),
        peripheral.observe(batteryLevelCharacteristic),
    ).map(ByteArray::first)
        .map(Byte::toInt)

    private val _status = MutableStateFlow<ByteArray?>(null)

    val scooterStatus = merge(
        _status.filterNotNull(),
        peripheral.observe(operationStatsCharacteristic)
    ).map(ScooterStatus::fromByteArray)

    private val _odo = MutableStateFlow<ByteArray?>(null)

    val odometer = merge(
        _odo.filterNotNull(),
        peripheral.observe(operationOdoCharacteristic)
    ).map(Odometer::fromByteArray)

    private val _diag = MutableStateFlow<ByteArray?>(null)

    val diag = merge(
        _diag.filterNotNull(),
        peripheral.observe(diagnosticsStatusCharacteristic)
    ).map(Diagnostics::fromBytes)

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi = _rssi.asStateFlow()

//    private val _periodMillis = MutableStateFlow<Duration?>(null)
//    val periodMillis = _periodMillis.filterNotNull()

//    suspend fun setPeriod(period: Duration) {
//        writeGyroPeriod(period)
//        _periodMillis.value = period
//    }


    suspend fun connect() {
        Log.info { "Connecting" }
        try {
            peripheral.connect().launch { monitorRssi() }
            _battery.value = readBatteryLevel()
//            _periodMillis.value = readGyroPeriod()
//            enableGyro()
            Log.info { "Connected" }
        } catch (e: IOException) {
            Log.warn(e) { "Connection attempt failed" }
            peripheral.disconnect()
        }
    }

    suspend fun disconnect() {
        peripheral.disconnect()
    }

    private suspend fun monitorRssi() {
        try {
            while (coroutineContext.isActive) {
                _rssi.value = peripheral.rssi()

                Log.debug { "RSSI: ${_rssi.value}" }
                delay(rssiInterval)
            }
        } catch (e: UnsupportedOperationException) {
            // As of Chrome 128, RSSI is not yet supported (even with
            // `chrome://flags/#enable-experimental-web-platform-features` flag enabled).
            Log.warn(e) { "RSSI is not supported" }
        }
    }
//
//    /** Set period, allowable range is 100-2550 ms. */
//    private suspend fun writeGyroPeriod(period: Duration) {
//        require(period in PeriodRange) { "Period must be in the range $PeriodRange, was $period." }
//
//        val value = period.inWholeMilliseconds / 10
//        val data = byteArrayOf(value.toByte())
//
//        Log.verbose { "Writing gyro period of $period" }
//        peripheral.write(movementPeriodCharacteristic, data, WithResponse)
//        Log.info { "Writing gyro period complete" }
//    }
//
//    /** Period within the range 100-2550 ms. */
//    private suspend fun readGyroPeriod(): Duration {
//        val value = peripheral.read(movementPeriodCharacteristic)
//        return ((value[0].toInt() and 0xFF) * 10).milliseconds
//    }
//
//    private suspend fun enableGyro() {
//        Log.info { "Enabling gyro" }
//        peripheral.write(movementConfigCharacteristic, byteArrayOf(0x7F, 0x0), WithResponse)
//        Log.info { "Gyro enabled" }
//    }
//
//    private suspend fun disableGyro() {
//        peripheral.write(movementConfigCharacteristic, byteArrayOf(0x0, 0x0), WithResponse)
//    }

    private suspend fun readBatteryLevel(): ByteArray =
        peripheral.read(batteryLevelCharacteristic)

    private val tripsmutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun readTrips(): Map<Int, Trip> {
        tripsmutex.withLock {
            try {
                Log.info { "About to read trips" }
                var sentStart = false;
                val results = peripheral.observe(tripsCharacteristic, {
                    if (!sentStart) {
                        Log.info { "Writing trips fetch command" }
                        peripheral.write(
                            tripsCharacteristic,
                            byteArrayOf(1),
                            WriteType.WithResponse
                        )
                        sentStart = true
                    }
                })

                val tripStarts = mutableMapOf<Int, ByteArray>()
                val tripEnds = mutableMapOf<Int, ByteArray>()

                results
                    .onEach { x ->
                        Log.info {
                            "TRIPS: ${
                                x.joinToString(
                                    separator = " ",
                                    prefix = "[",
                                    postfix = "]"
                                ) { eachByte -> "%02x".format(eachByte) }
                            }"
                        }
                    }
                    .takeWhile { x -> !(x.size == 1 && x[0].toInt() == 4) }
                    .collect { x ->
                        val id = x[0].toInt()
                        if (x[1].toInt() == 0) {
                            tripStarts.put(id, x)
                        } else {
                            tripEnds.put(id, x)
                        }
                    }

                val trips = tripStarts.mapValues { entry ->
                    val end = tripEnds[entry.key]
                    try {
                        Trip.fromBytes(entry.value, end)
                    } catch (e: ArrayIndexOutOfBoundsException) {
                        null
                    }
                }.filterValues { it != null }.mapValues { it.value!! }


//
//                .mapNotNull { x ->
//                    try {
//                        Trip.fromBytes(x[0], x.getOrNull(1))
//                    } catch (e: ArrayIndexOutOfBoundsException) {
//                        null
//                    }
//                }
//                .toCollection(trips)

                Log.info { "Finished reading trips" }
                return trips
            } catch (e: GattStatusException) {
                Log.debug(e) { "Failed to read trips, probably DCd" }

                return mapOf()
            }
        }
    }

    suspend fun writeOperationCommand(idx: Int, body: ByteArray) {
        peripheral.write(operationCommandCharacteristic, byteArrayOf(idx.toByte()) + body, WriteType.WithResponse)
    }

    suspend fun markTrip() {
        writeOperationCommand(9, byteArrayOf())
    }

    suspend fun sendDisplayText(msg: String) {
        writeOperationCommand(12, byteArrayOf())
        msg.encodeToByteArray().toList().chunked(18)
            .mapIndexed { index, bytes -> byteArrayOf(index.toByte()) + bytes }
            .forEach { buf -> writeOperationCommand(4, buf) }
        writeOperationCommand(12, byteArrayOf())
    }
}