package com.simmsb.egretdash

import kotlin.time.Instant

data class Trip(val index: Int, val start: TripStart, val end: TripEnd?) {
    class TripStart(val batteryLevel: Int, val odoTotal: Int, val timestamp: Instant) {
        companion object {
            fun fromBytes(bytes: ByteArray): TripStart {
                return TripStart(
                    PayloadUtils.uInt8(bytes, 6),
                    PayloadUtils.uInt24(bytes, 7),
                    Instant.fromEpochSeconds(PayloadUtils.uInt32(bytes, 2).toLong()),
                )
            }
        }
    }
    class TripEnd(val batteryLevel: Int, val odoEco: Int, val odoTour: Int, val odoSport: Int, val odoTotal: Int, val timestamp: Instant) {
        companion object {
            fun fromBytes(bytes: ByteArray): TripEnd {
                return TripEnd(
                    PayloadUtils.uInt8(bytes, 6),
                    PayloadUtils.uInt24(bytes, 10),
                    PayloadUtils.uInt24(bytes, 13),
                    PayloadUtils.uInt24(bytes, 16),
                    PayloadUtils.uInt24(bytes, 7),
                    Instant.fromEpochSeconds(PayloadUtils.uInt32(bytes, 2).toLong()),

                    )
            }
        }
    }

    companion object {
        fun fromBytes(start: ByteArray, end: ByteArray?): Trip {
            val index = start[0].toInt()
            val start = TripStart.fromBytes(start)
            val end = end?.let { TripEnd.fromBytes(it) }
            return Trip(index, start, end)
        }
    }
}
