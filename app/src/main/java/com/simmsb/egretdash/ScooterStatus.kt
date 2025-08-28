package com.simmsb.egretdash

import com.simmsb.egretdash.PayloadUtils.flag
import com.simmsb.egretdash.PayloadUtils.uInt16
import com.simmsb.egretdash.PayloadUtils.uInt8

data class ScooterStatus(
    val charging: Boolean,
    val drivingMode: DrivingMode,
    val ecoModeRange: Float,
    val errorCode: Int,
    val findMyStatus: Int,
    val lightsOn: Boolean,
    val locked: Boolean,
    val powerOutput: Int,
    val poweredOn: Boolean,
    val rangeFactor: Int,
    val speed: Float,
    val sportModeRange: Float,
    val temperatureHigh: Boolean,
    val temperatureLow: Boolean,
    val throttle: Int,
    val tourModeRange: Float,
) {
    enum class DrivingMode {
        Walk,
        Eco,
        Tour,
        Sport;

        companion object {
            fun fromByte(byte: Int): DrivingMode {
                return when (byte) {
                    0 -> Walk
                    1 -> Eco
                    2 -> Tour
                    3 -> Sport
                    else -> throw RuntimeException("Oopsie")
                }
            }
        }
    }

    companion object {
        val mock = ScooterStatus(
            charging = false,
            drivingMode = DrivingMode.Tour,
            ecoModeRange = 0f,
            errorCode = 0,
            findMyStatus = 0,
            lightsOn = false,
            locked = true,
            powerOutput = 0,
            poweredOn = false,
            rangeFactor = 100,
            speed = 0f,
            sportModeRange = 0f,
            temperatureLow = false,
            temperatureHigh = false,
            throttle = 0,
            tourModeRange = 0f
        )

        fun fromByteArray(bytes: ByteArray): ScooterStatus {
            val poweredOn: Boolean = flag(bytes[0], 0)
            val locked: Boolean = flag(bytes[0], 1)
            val lightsOn: Boolean = flag(bytes[0], 2)
            val charging: Boolean = flag(bytes[0], 3)
            val temperatureLow: Boolean = flag(bytes[0], 4)
            val temperatureHigh: Boolean = flag(bytes[0], 5)
            val speed: Float = uInt16(bytes, 1) / 10.0f
            val powerOutput: Int = uInt8(bytes, 3)
            val ecoModeRange: Float = uInt16(bytes, 4) / 10.0f
            val tourModeRange: Float = uInt16(bytes, 6) / 10.0f
            val sportModeRange: Float = uInt16(bytes, 8) / 10.0f
            val rangeFactor: Int = uInt8(bytes, 10)
            val throttle: Int = uInt8(bytes, 11)
            val drivingMode = DrivingMode.fromByte(bytes[12].toInt())
            val errorCode = bytes[13].toInt()
            val findMyStatus = bytes[14].toInt()
            return ScooterStatus(
                charging,
                drivingMode,
                ecoModeRange,
                errorCode,
                findMyStatus,
                lightsOn,
                locked,
                powerOutput,
                poweredOn,
                rangeFactor,
                speed,
                sportModeRange,
                temperatureHigh,
                temperatureLow,
                throttle,
                tourModeRange
            )
        }
    }
}

