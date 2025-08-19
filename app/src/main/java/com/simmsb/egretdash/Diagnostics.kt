package com.simmsb.egretdash

data class Diagnostics(val motorRpm: Int, val motorTemperature: Float) {
    companion object {
        val mock = Diagnostics(0, 0f)

        fun fromBytes(bytes: ByteArray): Diagnostics {
            val temp = PayloadUtils.uInt16(bytes, 0).toFloat() / 10f
            val rpm = PayloadUtils.uInt16(bytes, 2)
            return Diagnostics(rpm, temp)
        }
    }
}
