package com.simmsb.egretdash

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.juul.kable.Peripheral
import com.juul.kable.logs.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.time.Clock


class RestartLoggerReceiver : BroadcastReceiver() {
    companion object {
        const val TAG: String = "RestartLoggerReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.startService(Intent(context.applicationContext, LoggerService::class.java))
    }
}

class LoggerService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Toast.makeText(this, "Egret Dash background service stopped", Toast.LENGTH_SHORT).show()
        sendBroadcast(Intent("RestartLoggerService"))
        super.onDestroy()
        job.cancel()
    }

    override fun onCreate() {
        super.onCreate()

        start()
    }

    fun start() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Foreground bluetooth worker",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Channel for foreground service notification"
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .build()
        ServiceCompat.startForeground(this, 100, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                val database = DashboardDatabase.getDatabase(applicationContext)

                val device = applicationContext.preferredDeviceDataStore.data.first()
                    .get(PREFERRED_DEVICE_KEY) ?: return@launch

                val peripheral = Peripheral(device) {
                    autoConnectIf { true }
                    logging { level = Logging.Level.Warnings }
                }

                val scooter = Scooter(peripheral)

                val statusJob = launch {
                    scooter.scooterStatus.collect { s ->
                        database.run {
                            statusDao().insert(
                                ScooterStatusDB(
                                    id = 0,
                                    date = Clock.System.now(),
                                    charging = s.charging,
                                    drivingMode = s.drivingMode,
                                    ecoModeRange = s.ecoModeRange,
                                    errorCode = s.errorCode,
                                    findMyStatus = s.findMyStatus,
                                    lightsOn = s.lightsOn,
                                    locked = s.locked,
                                    powerOutput = s.powerOutput,
                                    poweredOn = s.poweredOn,
                                    rangeFactor = s.rangeFactor,
                                    speed = s.speed,
                                    sportModeRange = s.sportModeRange,
                                    temperatureHigh = s.temperatureHigh,
                                    temperatureLow = s.temperatureLow,
                                    throttle = s.throttle,
                                    tourModeRange = s.tourModeRange,
                                )
                            )
                        }
                    }
                }

                launch {
                    scooter.odometer.collect { s ->
                        database.run {
                            odoDao().insert(OdometerDB(
                                id = 0,
                                date = Clock.System.now(),
                                hectoMeters = s.hectoMeters,
                                total = s.total,
                                totalEco = s.totalEco,
                                totalTour = s.totalTour,
                                totalSport = s.totalSport,
                            ))
                        }
                    }
                }

                joinAll(statusJob)
            } finally {
                stopSelf()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    companion object {
        const val CHANNEL_ID = "ForegroundBTWorkerChannel"
    }

}