package com.simmsb.egretdash

import java.nio.ByteBuffer
import java.util.Date


object PayloadUtils {
    fun flag(byte: Byte, idx: Int): Boolean {
        return (byte and (1 shl idx)) > 0
    }

    fun uInt8(bArr: ByteArray, i: Int): Int {
        return bArr[i].toInt()
    }

    fun uInt16(bArr: ByteArray, i: Int): Int {
        return ByteBuffer.wrap(bArr).getShort(i).toInt()
    }

    fun uInt24(bArr: ByteArray, i: Int): Int {
        return uInt8(bArr, i + 2) or (uInt8(bArr, i) shl 16) or (uInt8(bArr, i + 1) shl 8)
    }

    fun uInt32(bArr: ByteArray, i: Int): Int {
        return ByteBuffer.wrap(bArr).getInt(i)
    }

    fun date(date: Int): Date {
        return Date(date.toLong())
    }
}
