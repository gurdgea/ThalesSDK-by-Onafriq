package com.mobile.app.d1core.util

private const val HEX_DIGITS = "0123456789ABCDEF"

fun String.hexToBytes(): ByteArray {
    val cleaned = trim()
    require(cleaned.length % 2 == 0) {
        "Hex string must have an even length, was ${cleaned.length}"
    }
    return ByteArray(cleaned.length / 2) { index ->
        val high = cleaned[index * 2].hexValue()
        val low = cleaned[index * 2 + 1].hexValue()
        ((high shl 4) or low).toByte()
    }
}

fun ByteArray.toHex(): String = buildString(size * 2) {
    this@toHex.forEach { byte ->
        val value = byte.toInt() and 0xFF
        append(HEX_DIGITS[value ushr 4])
        append(HEX_DIGITS[value and 0x0F])
    }
}

private fun Char.hexValue(): Int =
    digitToIntOrNull(16) ?: throw IllegalArgumentException("Not a hex digit: '$this'")
