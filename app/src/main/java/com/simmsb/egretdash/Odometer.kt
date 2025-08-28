package com.simmsb.egretdash

data class Odometer(
    val hectoMeters: Int,
    val total: Int,
    val totalEco: Int,
    val totalTour: Int,
    val totalSport: Int,
) {
    companion object {
        val mock = Odometer(
            hectoMeters = 0,
            total = 0,
            totalEco = 0,
            totalTour = 0,
            totalSport = 0
        )

        fun fromByteArray(bytes: ByteArray): Odometer {
            val hectoMeters = PayloadUtils.uInt16(bytes, 0)
            val total = PayloadUtils.uInt24(bytes, 2)
            val totalEco = PayloadUtils.uInt24(bytes, 5)
            val totalTour = PayloadUtils.uInt24(bytes, 8)
            val totalSport = PayloadUtils.uInt24(bytes, 11)

            return Odometer(hectoMeters, total, totalEco, totalTour, totalSport)
        }
    }
}
