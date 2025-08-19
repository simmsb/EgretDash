package com.simmsb.egretdash.scan

import com.juul.kable.Identifier
import com.juul.kable.PlatformAdvertisement
import com.juul.khronicle.Log
import com.simmsb.egretdash.Scooter
import com.simmsb.egretdash.Scooter.Companion.AdvertisedServices
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class ScannerDeviceLocator(
    private val scope: CoroutineScope,
    private val onStatus: suspend (String?) -> Unit,
) : DeviceLocator {

    private val found = mutableMapOf<Identifier, PlatformAdvertisement>()

    private val _isScanning = MutableStateFlow(DeviceLocator.State.NotYetScanned)
    override val state: StateFlow<DeviceLocator.State> = _isScanning.asStateFlow()

    private val _advertisements = MutableStateFlow<List<PlatformAdvertisement>>(emptyList())
    override val advertisements = _advertisements.asStateFlow()

    private var scanJob: Job? = null

    override fun run() {
        if (_isScanning.value == DeviceLocator.State.Scanning) return

        scanJob = scope.launch(CoroutineName("Scanner")) {
            _isScanning.value = DeviceLocator.State.Scanning
            try {
                Log.info { AdvertisedServices.toString() }

                withTimeout(10.seconds) {
                    Scooter.scanner.advertisements
                        .onStart { onStatus("Scanning") }
                        .collect { advertisement ->
                            found[advertisement.identifier] = advertisement
                            _advertisements.value = found.values.toList()
                        }
                }
            } catch (e: Exception) {
                onStatus(
                    when (e) {
                        is CancellationException -> null
                        else -> e.message ?: "Unknown error"
                    }
                )
            } finally {
                _isScanning.value = DeviceLocator.State.Scanned
            }
        }
    }

    override suspend fun cancelAndJoin() {
        scanJob?.cancelAndJoin()
    }

    override suspend fun clear() {
        cancelAndJoin()
        found.clear()
        _isScanning.value = DeviceLocator.State.NotYetScanned
    }
}