package com.simmsb.egretdash

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer

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

        private fun flag(byte: Byte, idx: Int): Boolean {
            return (byte and (1 shl idx)) > 0
        }


        private fun uInt8(bArr: ByteArray, i: Int): Int {
            return bArr[i].toInt()
        }

        private fun uInt16(bArr: ByteArray, i: Int): Int {
            return ByteBuffer.wrap(bArr).getShort(i).toInt()
        }

        fun uInt24(bArr: ByteArray, i: Int): Int {
            return uInt8(bArr, i + 2) or (uInt8(bArr, i) shl 16) or (uInt8(bArr, i + 1) shl 8)
        }

        fun fromByteArray(bytes: ByteArray): Odometer {
            val hectoMeters = uInt16(bytes, 0)
            val total = uInt24(bytes, 2)
            val totalEco = uInt24(bytes, 5)
            val totalTour = uInt24(bytes, 8)
            val totalSport = uInt24(bytes, 11)

            return Odometer(hectoMeters, total, totalEco, totalTour, totalSport)
        }
    }
}
