package com.mobile.app.d1core

import com.mobile.app.d1core.util.hexToBytes
import com.mobile.app.d1core.util.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HexTest {

    @Test
    fun `decodes low nibble only values`() {
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x01), "010001".hexToBytes())
    }

    @Test
    fun `shifts the high nibble instead of adding it`() {
        assertArrayEquals(byteArrayOf(0xB1.toByte()), "B1".hexToBytes())
        assertArrayEquals(byteArrayOf(0xFF.toByte()), "FF".hexToBytes())
    }

    @Test
    fun `accepts lower case`() {
        assertArrayEquals("DEADBEEF".hexToBytes(), "deadbeef".hexToBytes())
    }

    @Test
    fun `round trips`() {
        val hex = "00B1D8E4F27A3C5E96"
        assertEquals(hex, hex.hexToBytes().toHex())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects odd length`() {
        "ABC".hexToBytes()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non hex characters`() {
        "ZZ".hexToBytes()
    }
}
